package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import com.jpweytjens.barberfish.datatype.shared.gradeFillRange
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeThresholdTest {

    // The edges a config may snap to, per palette. Zero is not a stop: it opens the flattest
    // band rather than closing it, so it is a floor, not an edge.
    private val expectedStops =
        mapOf(
            GradePalette.BARBERFISH to
                (listOf(2.0, 5.0, 8.0, 11.0, 14.0, 20.0) to listOf(-2.0, -6.0, -12.0)),
            GradePalette.WAHOO to (listOf(4.0, 8.0, 12.0, 20.0) to emptyList<Double>()),
            GradePalette.GARMIN to (listOf(3.0, 6.0, 9.0, 12.0) to emptyList<Double>()),
            GradePalette.HSLUV to (listOf(3.0, 6.0, 9.0, 12.0, 15.0, 18.0) to emptyList<Double>()),
            GradePalette.KAROO to (listOf(2.0, 5.0, 8.0, 11.0, 14.0, 20.0) to emptyList<Double>()),
            GradePalette.ZWIFT to (listOf(3.0, 6.0, 9.0) to emptyList<Double>()),
            GradePalette.TURBO to (listOf(3.0, 6.0, 9.0, 12.0, 15.0) to listOf(-3.0, -6.0, -9.0)),
        )

    @Test
    fun every_palette_reports_its_own_stops() {
        assertEquals(
            "all palettes must be covered",
            GradePalette.entries.toSet(),
            expectedStops.keys
        )
        expectedStops.forEach { (palette, expected) ->
            val (climb, descent) = expected
            val stops = gradeBandStops(palette)
            assertEquals("$palette climb stops", climb, stops.climb)
            assertEquals("$palette descent stops", descent, stops.descent)
        }
    }

    @Test
    fun climb_stops_ascend_and_descent_stops_descend() {
        GradePalette.entries.forEach { palette ->
            val stops = gradeBandStops(palette)
            assertEquals("$palette climb stops ascend", stops.climb.sorted(), stops.climb)
            assertEquals(
                "$palette descent stops descend",
                stops.descent.sortedDescending(),
                stops.descent,
            )
        }
    }

    // The band-count fill range still feeds the sparkline and the map until they read edges.

    @Test
    fun wahoo_climb_threshold_is_4() {
        val range = gradeFillRange(GradePalette.WAHOO)
        assertEquals(4.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }

    @Test
    fun garmin_climb_threshold_is_3() {
        val range = gradeFillRange(GradePalette.GARMIN)
        assertEquals(3.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }

    @Test
    fun hsluv_climb_threshold_is_3() {
        val range = gradeFillRange(GradePalette.HSLUV)
        assertEquals(3.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }

    @Test
    fun karoo_climb_threshold_is_2() {
        val range = gradeFillRange(GradePalette.KAROO)
        assertEquals(2.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }
}
