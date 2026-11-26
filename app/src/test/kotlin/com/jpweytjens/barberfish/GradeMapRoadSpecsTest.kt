package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.M_PER_DEG
import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.ChevronRoad
import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.GradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.alignChords
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.latLngDistanceM
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [buildGradeMapSpecs] with a matched road: the band moves onto it, nothing else changes. */
class GradeMapRoadSpecsTest {
    // 2 km east along y = 0, as the Karoo hands it over (rounded to the polyline grid).
    private val routePolyline = encodeGpsPolyline(RouteFixtures.straight(2000.0, 50.0))
    private val saved = decodeGpsPolyline(routePolyline)

    // Two climbs and two flats, so the band has several pieces.
    private val elevationPolyline =
        RouteFixtures.encodeElevation(
            listOf(0f to 0f, 500f to 40f, 1000f to 40f, 1500f to 80f, 2000f to 80f)
        )

    private val tuning =
        EffectiveGradeMapTuning(
            skipBands = 0,
            simplification = ElevationSimplification.NONE,
            climbEdge = -1000.0,
            descentEdge = 1000.0,
        )

    private fun north(points: List<LatLng>, m: Double) = points.map {
        it.copy(lat = it.lat + m / M_PER_DEG)
    }

    // Every chord of [points] following a road 3 m to the north.
    private fun onRoad(points: List<LatLng>): MatchedRoadLine {
        val cum = cumulativeDistancesM(points)
        return MatchedRoadLine(points, cum)
            .with(0, alignChords(points, cum, points.indices, MatchedRoad(north(points, 3.0))))
    }

    private fun specsFor(line: MatchedRoadLine?, reversed: Boolean = false): GradeMapSpecs =
        buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            tuning = tuning,
            chevronSpacingMaxM = 60.0,
            chevronSpacingMinM = 60.0,
            chevronWindowHalfM = 12.0,
            reversed = reversed,
            matchedRoadLine = line,
        )

    // Metres north of y = 0, to the 0.6 m the polyline grid allows.
    private fun assertNorth(expectedM: Double, encoded: String) {
        val points = decodeGpsPolyline(encoded)
        assertTrue(points.size >= 2)
        points.forEach { assertEquals(expectedM, it.lat * M_PER_DEG, 0.6) }
    }

    @Test
    fun the_fixture_has_several_pieces_and_chevrons() {
        val specs = specsFor(null)
        assertTrue(specs.polylines.size >= 3)
        assertTrue(specs.chevrons.size >= 10)
        assertEquals(saved.size - 1, onRoad(saved).followingChords)
    }

    @Test
    fun an_empty_matched_line_changes_nothing() {
        val plain = specsFor(null)
        val empty = specsFor(MatchedRoadLine(saved, cumulativeDistancesM(saved)))
        assertEquals(plain.polylines, empty.polylines)
        assertEquals(plain.chevrons, empty.chevrons)
    }

    @Test
    fun a_line_built_for_another_route_is_ignored() {
        val other =
            onRoad(decodeGpsPolyline(encodeGpsPolyline(RouteFixtures.straight(1900.0, 50.0))))
        val specs = specsFor(other)
        assertEquals(specsFor(null), specs)
        assertNull(specs.chevronRoad)
    }

    @Test
    fun the_band_and_its_casing_follow_the_road() {
        val plain = specsFor(null).polylines
        val road = specsFor(onRoad(saved)).polylines
        assertEquals(plain.size, road.size)
        road.forEachIndexed { i, spec ->
            assertNorth(3.0, spec.encoded)
            assertNorth(3.0, spec.casingEncoded)
            assertNorth(0.0, plain[i].encoded)
            assertEquals(
                plain[i].copy(encoded = spec.encoded, casingEncoded = spec.casingEncoded),
                spec,
            )
        }
    }

    @Test
    fun the_candidate_chevrons_do_not_move() {
        assertEquals(specsFor(null).chevrons, specsFor(onRoad(saved)).chevrons)
    }

    @Test
    fun a_moved_chevron_sits_on_the_road_and_is_otherwise_the_same() {
        val specs = specsFor(onRoad(saved))
        val road = specs.chevronRoad
        assertNotNull(road)
        specs.chevrons.forEach { candidate ->
            val moved = road!!.moved(candidate)
            assertEquals(
                3.0,
                latLngDistanceM(
                    LatLng(candidate.lat, candidate.lng),
                    LatLng(moved.lat, moved.lng),
                ),
                0.05,
            )
            assertEquals(3.0, (moved.lat - candidate.lat) * M_PER_DEG, 0.05)
            assertEquals(candidate.id, moved.id)
            assertEquals(candidate.colorArgb, moved.colorArgb)
            assertEquals(candidate.distanceM, moved.distanceM, 0.0)
            assertTrue(abs(candidate.bearingDeg - moved.bearingDeg) < 1f)
        }
    }

    @Test
    fun a_moved_chevron_takes_the_roads_bearing() {
        // The handed-over line cuts a bend that the road takes through (150, 30).
        val line = (0..3).map { point(it * 100.0, 0.0) }
        val bend =
            listOf(
                point(0.0, 0.0),
                point(100.0, 0.0),
                point(150.0, 30.0),
                point(200.0, 0.0),
                point(300.0, 0.0),
            )
        val cum = cumulativeDistancesM(line)
        val road =
            MatchedRoadLine(line, cum)
                .with(0, alignChords(line, cum, line.indices, MatchedRoad(bend)))
        val at = point(125.0, 0.0)
        val moved =
            ChevronRoad(road, windowHalfM = 10.0, reversed = false)
                .moved(ClimbChevronSpec("c", at.lat, at.lng, 90f, 7, 125.0))
        assertTrue(latLngDistanceM(point(125.0, 15.0), LatLng(moved.lat, moved.lng)) < 0.01)
        // Up the first leg of the bend: 50 m east for 30 m north.
        assertEquals(59.0, moved.bearingDeg.toDouble(), 1.0)
        // Ridden the other way the same spot is 175 m in, and the mark points back down the leg.
        val back =
            ChevronRoad(road, windowHalfM = 10.0, reversed = true)
                .moved(ClimbChevronSpec("c", at.lat, at.lng, 270f, 7, 175.0))
        assertTrue(latLngDistanceM(point(125.0, 15.0), LatLng(back.lat, back.lng)) < 0.01)
        assertEquals(239.0, back.bearingDeg.toDouble(), 1.0)
    }

    @Test
    fun a_reversed_route_follows_the_same_road() {
        val plain = specsFor(null, reversed = true)
        val road = specsFor(onRoad(saved), reversed = true)
        assertEquals(plain.polylines.size, road.polylines.size)
        road.polylines.forEachIndexed { i, spec ->
            assertNorth(3.0, spec.encoded)
            assertNorth(3.0, spec.casingEncoded)
            // Still in ride order: each piece starts where the chord piece starts.
            val start = decodeGpsPolyline(spec.encoded).first()
            val plainStart = decodeGpsPolyline(plain.polylines[i].encoded).first()
            assertEquals(plainStart.lng * M_PER_DEG, start.lng * M_PER_DEG, 1.2)
            assertEquals(plain.polylines[i].id, spec.id)
            assertEquals(plain.polylines[i].startM, spec.startM, 0.0)
        }
        // The ride starts at the saved line's end.
        assertEquals(
            2000.0,
            decodeGpsPolyline(road.polylines.first().encoded).first().lng * M_PER_DEG,
            1.2,
        )
        val candidate = road.chevrons.first()
        val moved = road.chevronRoad!!.moved(candidate)
        assertEquals(3.0, (moved.lat - candidate.lat) * M_PER_DEG, 0.05)
        assertEquals(candidate.lng, moved.lng, 1e-9)
    }
}
