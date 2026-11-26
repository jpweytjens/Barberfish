package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.ChordRoad
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.alignChords
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.extractSubPolyline
import com.jpweytjens.barberfish.datatype.shared.latLngDistanceM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchedRoadLineTest {
    // The handed-over line: P0..P3 along y = 0 at x = 0, 100, 200, 300.
    private val line4 = (0..3).map { point(it * 100.0, 0.0) }
    private val line5 = (0..4).map { point(it * 100.0, 0.0) }

    // Chord 1 cuts this bend.
    private val bend =
        listOf(
            point(0.0, 0.0),
            point(100.0, 0.0),
            point(150.0, 30.0),
            point(200.0, 0.0),
            point(300.0, 0.0),
        )

    private fun unmatched(points: List<LatLng>) =
        MatchedRoadLine(points, cumulativeDistancesM(points))

    private fun align(points: List<LatLng>, road: List<LatLng>) =
        alignChords(points, cumulativeDistancesM(points), points.indices, road)

    private fun bendLine() = unmatched(line4).with(0, align(line4, bend))

    private fun road(vararg xy: Pair<Double, Double>): ChordRoad {
        val points = xy.map { point(it.first, it.second) }
        return ChordRoad(points, cumulativeDistancesM(points))
    }

    // Chord 1 alone follows a road 3 m to the north; its neighbours are straight.
    private fun offsetLine() = unmatched(line4).with(1, listOf(road(100.0 to 3.0, 200.0 to 3.0)))

    // Chords 0 and 1 come from two separate matches that disagree by 2 m at P1.
    private fun twoPieceLine() =
        unmatched(line4)
            .with(0, listOf(road(0.0 to 2.0, 100.0 to 2.0)))
            .with(1, listOf(road(100.0 to 4.0, 200.0 to 4.0)))

    private fun assertNear(expected: LatLng, actual: LatLng) {
        val off = latLngDistanceM(expected, actual)
        assertTrue("expected $expected, got $actual ($off m apart)", off < 0.01)
    }

    private fun assertPath(expected: List<Pair<Double, Double>>, actual: List<LatLng>) {
        assertEquals("point count of $actual", expected.size, actual.size)
        expected.forEachIndexed { i, xy -> assertNear(point(xy.first, xy.second), actual[i]) }
    }

    @Test
    fun no_road_gives_the_chords_exactly() {
        val cum = cumulativeDistancesM(line4)
        val line = unmatched(line4)
        assertEquals(0, line.followingChords)
        for ((a, b) in
            listOf(0.0 to 300.0, 37.0 to 250.0, 100.0 to 200.0, -5.0 to 400.0, 200.0 to 100.0)) {
            assertEquals(extractSubPolyline(line4, cum, a, b), line.pointsBetween(a, b))
        }
    }

    @Test
    fun a_chord_takes_its_road_points() {
        val line = bendLine()
        assertEquals(3, line.followingChords)
        assertPath(
            listOf(0.0 to 0.0, 100.0 to 0.0, 150.0 to 30.0, 200.0 to 0.0, 300.0 to 0.0),
            line.pointsBetween(0.0, 300.0),
        )
    }

    @Test
    fun a_distance_maps_to_the_same_fraction_of_the_road() {
        val line = bendLine()
        assertNear(point(125.0, 15.0), line.positionAt(125.0))
        assertNear(point(150.0, 30.0), line.positionAt(150.0))
        assertPath(
            listOf(150.0 to 30.0, 200.0 to 0.0, 250.0 to 0.0),
            line.pointsBetween(150.0, 250.0),
        )
    }

    @Test
    fun a_position_past_either_end_is_the_end() {
        val line = bendLine()
        assertNear(point(0.0, 0.0), line.positionAt(-20.0))
        assertNear(point(300.0, 0.0), line.positionAt(350.0))
    }

    @Test
    fun neighbouring_ranges_meet() {
        for (line in listOf(bendLine(), offsetLine(), twoPieceLine())) {
            for (b in listOf(100.0, 137.0, 150.0, 200.0)) {
                assertEquals(
                    line.pointsBetween(0.0, b).last(),
                    line.pointsBetween(b, 300.0).first(),
                )
            }
        }
    }

    @Test
    fun a_straight_chord_before_a_road_chord_is_unchanged() {
        val cum = cumulativeDistancesM(line4)
        val line = offsetLine()
        assertNear(point(100.0, 0.0), line.positionAt(100.0))
        // Exactly at the saved point, whose distance is not exactly 100.0 in floating point.
        assertEquals(extractSubPolyline(line4, cum, 0.0, cum[1]), line.pointsBetween(0.0, cum[1]))
        assertPath(
            listOf(100.0 to 0.0, 100.0 to 3.0, 200.0 to 3.0, 200.0 to 0.0),
            line.pointsBetween(cum[1], cum[2]),
        )
        // The chord's path is 3 + 100 + 3 m; half of it is 53 m in.
        assertNear(point(150.0, 3.0), line.positionAt(150.0))
    }

    @Test
    fun a_straight_chord_after_a_road_chord_is_unchanged() {
        val cum = cumulativeDistancesM(line4)
        val line = offsetLine()
        assertNear(point(200.0, 0.0), line.positionAt(200.0))
        assertEquals(
            extractSubPolyline(line4, cum, cum[2], cum[3]),
            line.pointsBetween(cum[2], cum[3]),
        )
    }

    @Test
    fun separately_matched_pieces_join_at_the_earlier_road_end() {
        val line = twoPieceLine()
        assertNear(point(100.0, 2.0), line.positionAt(100.0))
        // No step from P0 onto the road at the route start.
        assertNear(point(0.0, 2.0), line.positionAt(0.0))
        val expected =
            listOf(
                0.0 to 2.0,
                100.0 to 2.0,
                100.0 to 4.0,
                200.0 to 4.0,
                200.0 to 0.0,
                300.0 to 0.0,
            )
        assertPath(expected, line.pointsBetween(0.0, 300.0))
        val swapped =
            unmatched(line4)
                .with(1, listOf(road(100.0 to 4.0, 200.0 to 4.0)))
                .with(0, listOf(road(0.0 to 2.0, 100.0 to 2.0)))
        assertEquals(line.pointsBetween(0.0, 300.0), swapped.pointsBetween(0.0, 300.0))
    }

    @Test
    fun an_end_off_the_road_straightens_only_its_two_chords() {
        val road =
            listOf(
                point(0.0, 0.0),
                point(100.0, 0.0),
                point(190.0, 0.0),
                point(200.0, 6.0),
                point(210.0, 0.0),
                point(300.0, 0.0),
                point(400.0, 0.0),
            )
        val chords = align(line5, road)
        assertEquals(listOf(true, false, false, true), chords.map { it != null })
    }

    @Test
    fun a_stretch_over_twice_the_chord_is_straight_and_its_neighbours_follow() {
        val road =
            listOf(
                point(0.0, 0.0),
                point(100.0, 0.0),
                point(100.0, 80.0),
                point(200.0, 80.0),
                point(200.0, 0.0),
                point(300.0, 0.0),
            )
        val chords = align(line4, road)
        assertEquals(listOf(true, false, true), chords.map { it != null })
    }

    @Test
    fun a_road_that_stops_short_leaves_the_rest_straight() {
        val road = listOf(point(0.0, 0.0), point(100.0, 0.0), point(200.0, 0.0))
        val chords = align(line5, road)
        assertEquals(listOf(true, true, false, false), chords.map { it != null })
    }

    @Test
    fun out_and_back_keeps_each_pass_on_its_own_stretch() {
        val p = RouteFixtures.outAndBack(legM = 200.0, stepM = 50.0)
        val cum = cumulativeDistancesM(p)
        val chords = align(p, p)
        assertEquals(p.size - 1, chords.size)
        chords.forEachIndexed { j, chord ->
            assertNotNull("chord $j should follow the road", chord)
            assertEquals(cum[j + 1] - cum[j], chord!!.cumDist.last(), 0.01)
            assertNear(p[j], chord.points.first())
        }
    }

    @Test
    fun a_closer_return_pass_does_not_take_the_outward_points() {
        // The line goes out and back along y = 0. The road goes out 2 m to the north and comes
        // back 1 m to the south, so every outward point is nearer to the return pass.
        val p = RouteFixtures.outAndBack(legM = 200.0, stepM = 50.0)
        val out = (0..4).map { point(it * 50.0, 2.0) }
        val back = (4 downTo 0).map { point(it * 50.0, -1.0) }
        val chords = align(p, out + back)
        assertEquals(List(8) { true }, chords.map { it != null })
        for (j in 0..2) {
            chords[j]!!.points.forEach { assertEquals(2.0, it.lat * RouteFixtures.M_PER_DEG, 0.01) }
            assertEquals(50.0, chords[j]!!.cumDist.last(), 0.01)
        }
        for (j in 5..7) {
            chords[j]!!.points.forEach {
                assertEquals(-1.0, it.lat * RouteFixtures.M_PER_DEG, 0.01)
            }
        }
    }

    @Test
    fun a_repeated_point_and_an_empty_road_are_straight() {
        val repeated = listOf(line4[0], line4[1], line4[1], line4[2], line4[3])
        val chords = align(repeated, bend)
        assertNull(chords[1])
        assertEquals(listOf(true, false, true, true), chords.map { it != null })
        assertTrue(align(line4, emptyList()).all { it == null })
        assertTrue(align(line4, listOf(point(0.0, 0.0))).all { it == null })
        assertEquals(3, align(line4, emptyList()).size)
    }

    @Test
    fun a_two_point_route_follows_its_road() {
        val two = listOf(point(0.0, 0.0), point(100.0, 0.0))
        val line =
            unmatched(two)
                .with(0, align(two, listOf(point(0.0, 2.0), point(50.0, 4.0), point(100.0, 2.0))))
        assertEquals(1, line.followingChords)
        assertPath(listOf(0.0 to 2.0, 50.0 to 4.0, 100.0 to 2.0), line.pointsBetween(0.0, 100.0))
    }

    @Test
    fun reversed_mirrors_the_forward_line() {
        val line = bendLine()
        val mirrorPoints = line4.reversed()
        val mirror = unmatched(mirrorPoints).with(0, align(mirrorPoints, bend.reversed()))
        for ((a, b) in listOf(0.0 to 300.0, 60.0 to 180.0)) {
            val expected = mirror.pointsBetween(a, b)
            val actual = line.pointsBetween(a, b, reversed = true)
            assertEquals(expected.size, actual.size)
            expected.forEachIndexed { i, p -> assertNear(p, actual[i]) }
        }
        assertNear(mirror.positionAt(125.0), line.positionAt(125.0, reversed = true))
    }

    @Test
    fun with_leaves_the_receiver_unchanged() {
        val before = unmatched(line4)
        val after = before.with(0, align(line4, bend))
        assertEquals(0, before.followingChords)
        assertEquals(3, after.followingChords)
        assertEquals(line4, before.pointsBetween(0.0, 300.0))
        // A null entry leaves a chord that already follows the road as it is.
        assertEquals(3, after.with(1, listOf(null)).followingChords)
    }
}
