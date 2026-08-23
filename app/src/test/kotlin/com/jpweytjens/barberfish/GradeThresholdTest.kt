package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.datatype.shared.gradeBandColor
import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeThresholdTest {

    // The edges a config may snap to, per palette. Zero is not a stop: it opens the flattest
    // band rather than closing it, so it is a floor, not an edge.
    private val expectedStops =
        mapOf(
            GradePalette.BARBERFISH to
                (listOf(2.0, 5.0, 8.0, 11.0, 14.0, 20.0) to listOf(-2.0, -6.0, -10.0)),
            GradePalette.SURGEONFISH to
                (listOf(2.0, 5.0, 8.0, 11.0, 14.0, 20.0) to listOf(-2.0, -6.0, -10.0)),
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
            expectedStops.keys,
        )
        expectedStops.forEach { (palette, expected) ->
            val (climb, descent) = expected
            val stops = gradeBandStops(palette)
            assertEquals("$palette climb stops", climb, stops.climb)
            assertEquals("$palette descent stops", descent, stops.descent)
        }
    }

    // The threshold the shipped default count means, per palette. This is also the number the
    // EMPHASIS readouts print, since both cards resolve the count through gradeEdges.
    @Test
    fun the_default_count_resolves_to_each_palette_s_first_stop() {
        val expected =
            mapOf(
                GradePalette.BARBERFISH to 2.0,
                GradePalette.SURGEONFISH to 2.0,
                GradePalette.WAHOO to 4.0,
                GradePalette.GARMIN to 3.0,
                GradePalette.HSLUV to 3.0,
                GradePalette.KAROO to 2.0,
                GradePalette.ZWIFT to 3.0,
                GradePalette.TURBO to 3.0,
            )
        assertEquals(
            "all palettes must be covered",
            GradePalette.entries.toSet(),
            expected.keys,
        )
        expected.forEach { (palette, climbEdge) ->
            val (climb, descent) = SparklineConfig().gradeEdges(palette)
            assertEquals("$palette climb edge", climbEdge, climb)
            if (gradeBandStops(palette).descent.isEmpty()) {
                assertNull("$palette must not colour descents", descent)
            }
        }
    }

    // The counts the EMPHASIS selectors offer, on both sides.
    private val offeredCounts = listOf(0, 1, 2, 3)

    // Not in any band table, so "took a band colour" cannot pass by accident.
    private val neutral = Color(0xFF808080)

    /**
     * Every count both cards offer must resolve to the grade the fill actually starts at: coloured
     * at the edge, neutral one step inside it. One step is 0.01 per cent, the resolution the map
     * rounds a cell's mean grade to.
     */
    @Test
    fun every_offered_count_names_the_grade_the_renderer_paints_from() {
        GradePalette.entries.forEach { palette ->
            offeredCounts.forEach { climbCount ->
                offeredCounts.forEach { descentCount ->
                    val (climbEdge, descentEdge) =
                        SparklineConfig(skipBands = climbCount, skipBandsDescent = descentCount)
                            .gradeEdges(palette)
                    fun colorAt(grade: Double) =
                        gradeBandColor(
                            grade = grade,
                            palette = palette,
                            climbEdge = climbEdge,
                            descentEdge = descentEdge,
                            neutral = neutral,
                            readable = false,
                        )
                    val at = "$palette climb=$climbCount descent=$descentCount"
                    if (climbEdge != null) {
                        assertNotEquals(
                            "$at: grade $climbEdge% must take a band colour",
                            neutral,
                            colorAt(maxOf(climbEdge, 0.01)),
                        )
                        if (climbEdge > 0.0) {
                            assertEquals(
                                "$at: grade ${climbEdge - 0.01}% must stay neutral",
                                neutral,
                                colorAt(climbEdge - 0.01),
                            )
                        }
                    }
                    if (descentEdge != null) {
                        assertNotEquals(
                            "$at: grade $descentEdge% must take a band colour",
                            neutral,
                            colorAt(minOf(descentEdge, -0.01)),
                        )
                        if (descentEdge < 0.0) {
                            assertEquals(
                                "$at: grade ${descentEdge + 0.01}% must stay neutral",
                                neutral,
                                colorAt(descentEdge + 0.01),
                            )
                        }
                    }
                }
            }
        }
    }
}
