package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MAX_UNSUPPORTED_END_M
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.alignToRoad
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.elevationAtM
import com.jpweytjens.barberfish.datatype.shared.heightsOnLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RejoinHeightsTest {
    // A steady 5% climb from 100 m, one sample every 30 m and one at the end.
    private fun climb(lengthM: Double): List<Pair<Float, Float>> {
        val distances = generateSequence(0.0) { it + 30.0 }.takeWhile { it < lengthM } + lengthM
        return distances.map { it.toFloat() to (100.0 + 0.05 * it).toFloat() }.toList()
    }

    private fun road(vararg xy: Pair<Double, Double>, withHeights: Boolean = true): MatchedRoad {
        val points = xy.map { point(it.first, it.second) }
        val length = cumulativeDistancesM(points).last()
        return MatchedRoad(points, if (withHeights) climb(length) else null)
    }

    // The straight road from x = [fromM] to x = [toM] along y = 0.
    private fun straightRoad(fromM: Double, toM: Double) = road(fromM to 0.0, toM to 0.0)

    private fun lineAt(vararg xs: Double): List<LatLng> = xs.map { point(it, 0.0) }

    private fun heightsOn(line: List<LatLng>, road: MatchedRoad) =
        heightsOnLine(line, cumulativeDistancesM(line), road)

    private fun List<Pair<Float, Float>>.heightAt(distanceM: Double): Double =
        elevationAtM(this, distanceM)

    // The line a road with a hole in it leaves: the road runs out to [at], takes a detour more than
    // twice as long as the chord [at]..[at] + [chordM], and comes back.
    private fun roadWithDetours(
        lengthM: Double,
        vararg detours: Pair<Double, Double>,
    ): MatchedRoad {
        val xy = mutableListOf(0.0 to 0.0)
        for ((at, chordM) in detours) {
            xy += at to 0.0
            xy += (at + chordM / 2) to chordM
            xy += (at + chordM) to 0.0
        }
        xy += lengthM to 0.0
        return road(*xy.toTypedArray())
    }

    @Test
    fun a_line_whose_road_starts_80_m_in_passes_the_old_guards_and_gets_no_heights() {
        val xs = listOf(0.0, 80.0) + (180..980 step 100).map { it.toDouble() } + 1000.0
        val line = lineAt(*xs.toDoubleArray())
        val cumDist = cumulativeDistancesM(line)
        val road = straightRoad(80.0, 1000.0)
        val roadLengthM = 920.0
        assertTrue(cumDist.last() / roadLengthM in 0.9..1.1)
        val alignment = alignToRoad(line, cumDist, line.indices, road)
        assertEquals(1, alignment.chords.count { it == null })
        assertNull(heightsOnLine(line, cumDist, road))
    }

    @Test
    fun a_20_m_prefix_is_held_flat_and_the_rest_is_unshifted() {
        val line = lineAt(0.0, 20.0, *(120..1020 step 100).map { it.toDouble() }.toDoubleArray())
        val heights = heightsOn(line, straightRoad(20.0, 1020.0))!!.heights
        assertEquals(100.0, heights.heightAt(0.0), 0.01)
        assertEquals(100.0, heights.heightAt(20.0), 0.01)
        assertEquals(100.0 + 0.05 * 500.0, heights.heightAt(520.0), 0.01)
    }

    @Test
    fun a_20_m_suffix_is_held_flat_at_the_roads_last_height() {
        val line = lineAt(*(0..1000 step 100).map { it.toDouble() }.toDoubleArray(), 1020.0)
        val heights = heightsOn(line, straightRoad(0.0, 1000.0))!!.heights
        assertEquals(100.0 + 0.05 * 500.0, heights.heightAt(500.0), 0.01)
        assertEquals(150.0, heights.heightAt(1000.0), 0.01)
        assertEquals(150.0, heights.heightAt(1010.0), 0.01)
        // The landing check reads this one value.
        assertEquals(150f, heights.last().second, 0.01f)
        assertEquals(cumulativeDistancesM(line).last().toFloat(), heights.last().first, 0.01f)
    }

    @Test
    fun a_missing_80_m_suffix_is_refused() {
        val line =
            lineAt(0.0, *(100..900 step 100).map { it.toDouble() }.toDoubleArray(), 920.0, 1000.0)
        assertTrue(80.0 > MAX_UNSUPPORTED_END_M)
        assertNull(heightsOn(line, straightRoad(0.0, 920.0)))
    }

    @Test
    fun a_50_m_gap_in_the_middle_is_bridged_by_a_straight_line() {
        val line = lineAt(0.0, 100.0, 200.0, 300.0, 400.0, 450.0, 500.0, 600.0, 800.0, 1000.0)
        val result = heightsOn(line, roadWithDetours(1000.0, 400.0 to 50.0))
        assertNotNull(result)
        val heights = result!!.heights
        val before = heights.heightAt(400.0)
        val after = heights.heightAt(450.0)
        assertTrue(after > before)
        assertEquals((before + after) / 2, heights.heightAt(425.0), 0.01)
        assertTrue(heights.none { it.first > 400f && it.first < 450f })
    }

    @Test
    fun an_80_m_gap_in_the_middle_is_refused() {
        val line = lineAt(0.0, 100.0, 200.0, 300.0, 400.0, 480.0, 600.0, 800.0, 1000.0)
        assertNull(heightsOn(line, roadWithDetours(1000.0, 400.0 to 80.0)))
    }

    @Test
    fun small_gaps_adding_up_to_over_a_tenth_are_refused() {
        // Three gaps of 40 m on a line of about 1000 m: each is fine, together they are 12%.
        val line =
            lineAt(
                0.0,
                100.0,
                140.0,
                200.0,
                300.0,
                340.0,
                400.0,
                500.0,
                540.0,
                600.0,
                800.0,
                1000.0,
            )
        val gaps = arrayOf(100.0 to 40.0, 300.0 to 40.0, 500.0 to 40.0)
        assertNull(heightsOn(line, roadWithDetours(1000.0, *gaps)))
        // Two of them are 8%.
        assertNotNull(heightsOn(line, roadWithDetours(1000.0, gaps[0], gaps[1])))
    }

    @Test
    fun a_fully_covered_line_reproduces_the_roads_height_at_every_anchor() {
        val xs = (0..1000 step 100).map { it.toDouble() }
        val line = lineAt(*xs.toDoubleArray())
        // The road's heights are every 30 m, not at the line's points.
        val result = heightsOn(line, straightRoad(0.0, 1000.0))!!
        for (x in xs) assertEquals(100.0 + 0.05 * x, result.heights.heightAt(x), 0.01)
        assertEquals(10, result.line.followingChords)
        // Road samples strictly inside a chord are kept: 30 m apart, so more than the line's
        // points.
        assertTrue(result.heights.size > line.size)
    }

    @Test
    fun the_profile_spans_exactly_the_lines_length() {
        val line = lineAt(0.0, 20.0, *(120..1020 step 100).map { it.toDouble() }.toDoubleArray())
        val heights = heightsOn(line, straightRoad(20.0, 1020.0))!!.heights
        assertEquals(0f, heights.first().first, 0f)
        assertEquals(cumulativeDistancesM(line).last().toFloat(), heights.last().first, 0.01f)
        assertTrue(heights.zipWithNext().all { (a, b) -> b.first > a.first })
    }

    @Test
    fun a_road_without_heights_gives_null() {
        val line = lineAt(0.0, 100.0, 200.0)
        assertNull(heightsOn(line, road(0.0 to 0.0, 200.0 to 0.0, withHeights = false)))
    }

    @Test
    fun a_road_whose_heights_repeat_a_distance_still_works() {
        val line = lineAt(0.0, 100.0, 200.0)
        val heights = listOf(0f to 100f, 100f to 105f, 200f to 110f, 200f to 110f, 200f to 110f)
        val result = heightsOn(line, MatchedRoad(line, heights))!!.heights
        assertEquals(110.0, result.heightAt(200.0), 0.01)
        assertTrue(result.all { it.second.isFinite() })
    }

    @Test
    fun a_line_too_short_to_have_a_chord_gives_null() {
        val road = straightRoad(0.0, 100.0)
        assertNull(heightsOn(emptyList(), road))
        assertNull(heightsOn(lineAt(0.0), road))
        assertNull(heightsOn(lineAt(0.0, 0.0), road))
        assertNull(heightsOn(lineAt(50.0, 50.0, 50.0), road))
    }
}
