package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.KAROO_HEIGHT_STEP_M
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MAX_ROAD_HEIGHT_SLOPE
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.ROAD_HEIGHT_FLOOR_M
import com.jpweytjens.barberfish.datatype.shared.ROAD_HEIGHT_INTERVAL_M
import com.jpweytjens.barberfish.datatype.shared.STOCK_HEIGHT_STEP_M
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.heightsOnLine
import com.jpweytjens.barberfish.datatype.shared.landingHeightAgrees
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RejoinScaleCheckTest {
    // The route runs 4 km east along y = 0 and the profile reads 300 m at 2 km.
    private val route = RouteFixtures.straight(lengthM = 4000.0, stepM = 100.0)
    private val profile = listOf(0f to 100f, 2000f to 300f, 4000f to 500f)
    private val landing = point(2000.0, 0.0)

    // The rejoin line: 1 km north to south ending on the route at 2 km, in 100 m vertices.
    private val line = (0..10).map { point(2000.0, 1000.0 - it * 100.0) }
    private val lineLengthM = cumulativeDistancesM(line).last()

    // A steady 2% climb ending at 300 m, one sample every 30 m and one at the end.
    private val trueHeights: List<Pair<Float, Float>> =
        (generateSequence(0.0) { it + ROAD_HEIGHT_INTERVAL_M }.takeWhile { it < lineLengthM } +
                lineLengthM)
            .map { it.toFloat() to (300.0 - 0.02 * (lineLengthM - it)).toFloat() }
            .toList()

    private fun List<Pair<Float, Float>>.mapHeights(transform: (Double) -> Double) =
        map { (distanceM, height) ->
            distanceM to transform(height.toDouble()).toFloat()
        }

    private fun agrees(
        heights: List<Pair<Float, Float>>,
        landing: LatLng = this.landing,
        routePoints: List<LatLng> = route,
        routeElevation: List<Pair<Float, Float>> = profile,
    ) = landingHeightAgrees(heights, landing, routePoints, routeElevation)

    // A line that ends at [heightM], 20 m lower at its start.
    private fun endingAt(heightM: Float) = listOf(0f to heightM - 20f, 1000f to heightM)

    private fun steepestStep(heights: List<Pair<Float, Float>>) =
        heights.zipWithNext { a, b -> abs(b.second - a.second) / (b.first - a.first) }.max()

    private fun lineHeights(heights: List<Pair<Float, Float>>) =
        heightsOnLine(line, cumulativeDistancesM(line), MatchedRoad(line, heights))

    @Test
    fun a_smooth_wrong_scale_passes_the_other_rules_and_fails_the_landing_check() {
        val variants =
            mapOf(
                // (b) eight times too steep about the floor
                "too steep" to
                    trueHeights.mapHeights {
                        ROAD_HEIGHT_FLOOR_M + 8 * (it - ROAD_HEIGHT_FLOOR_M)
                    },
                // (c) left unconverted: the inverse of karooHeightM
                "unconverted" to
                    trueHeights.mapHeights {
                        ROAD_HEIGHT_FLOOR_M +
                            (it - ROAD_HEIGHT_FLOOR_M) * (STOCK_HEIGHT_STEP_M / KAROO_HEIGHT_STEP_M)
                    },
                // (d) correct shape, 300 m too high
                "too high" to trueHeights.mapHeights { it + 300.0 },
            )
        for ((name, heights) in variants) {
            assertTrue("$name: slope rule", steepestStep(heights) <= MAX_ROAD_HEIGHT_SLOPE)
            assertNotNull("$name: heightsOnLine", lineHeights(heights))
            assertFalse("$name: landing check", agrees(heights))
        }
    }

    @Test
    fun correct_heights_are_published() {
        assertNotNull(lineHeights(trueHeights))
        assertTrue(agrees(trueHeights))
    }

    @Test
    fun twenty_five_metres_off_passes_and_thirty_five_fails() {
        assertTrue(agrees(endingAt(325f)))
        assertTrue(agrees(endingAt(275f)))
        assertFalse(agrees(endingAt(335f)))
        assertFalse(agrees(endingAt(265f)))
    }

    @Test
    fun a_landing_40_m_from_the_route_fails() {
        assertFalse(agrees(trueHeights, landing = point(2000.0, 40.0)))
    }

    @Test
    fun a_route_without_a_profile_fails() {
        assertFalse(agrees(trueHeights, routeElevation = emptyList()))
    }

    @Test
    fun on_a_reversed_route_the_landing_is_read_in_ride_order() {
        // Ridden east to west: the profile climbs 100 -> 500 m in ride order, so x = 1000 m is
        // 3 km in and reads 400 m. Read in saved order it would be 1 km in and read 200 m.
        val climbing = listOf(0f to 100f, 4000f to 500f)
        val atX1000 = point(1000.0, 0.0)
        assertTrue(agrees(endingAt(400f), atX1000, route.reversed(), climbing))
        assertFalse(agrees(endingAt(200f), atX1000, route.reversed(), climbing))
        assertTrue(agrees(endingAt(200f), atX1000, route, climbing))
    }

    // Kept on purpose, see the design's known risk: the check compares one height, the landing's,
    // so gradients doubled about that height still agree with the route there. It guards the
    // scale of the heights, not their shape.
    @Test
    fun the_documented_gap_gradients_doubled_about_the_landing_height_pass() {
        val doubled = trueHeights.mapHeights { 300.0 + 2 * (it - 300.0) }
        assertTrue(steepestStep(doubled) <= MAX_ROAD_HEIGHT_SLOPE)
        assertTrue(agrees(doubled))
    }
}
