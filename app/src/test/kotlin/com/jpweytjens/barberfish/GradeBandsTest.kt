package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.datatype.shared.gradeBandColor
import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GradeBandsTest {

    private val NEUTRAL = Color(0xFFC4C4C4)

    @Test
    fun karoo_bands_are_ordered_low_to_high_with_open_ends() {
        val bands = gradeBands(GradePalette.KAROO, readable = false)
        assertNull("lowest band has an open low end", bands.first().lo)
        assertNull("highest band has an open high end", bands.last().hi)
        val los = bands.drop(1).map { it.lo }
        assertEquals(listOf(2.0, 5.0, 8.0, 11.0, 14.0, 20.0), los)
    }

    @Test
    fun bands_tile_the_axis_without_gaps() {
        GradePalette.entries.forEach { palette ->
            val bands = gradeBands(palette, readable = false)
            bands.zipWithNext().forEach { (a, b) ->
                assertEquals("$palette: band edges must meet", a.hi, b.lo)
            }
        }
    }

    // Only the colours differ between the readable variants. A threshold that drifted in one of
    // them would move a band for the fields while leaving the selectors' stops where they are:
    // gradeBandStops reads the base tables, gradeBandColor and gradeColor the readable ones.
    @Test
    fun every_readable_variant_keeps_the_base_thresholds() {
        GradePalette.entries.forEach { palette ->
            val base = gradeBands(palette, readable = false, isNightMode = false)
            listOf(false to true, true to false, true to true).forEach { (readable, night) ->
                assertEquals(
                    "$palette readable=$readable night=$night",
                    base.map { it.lo to it.hi },
                    gradeBands(palette, readable, night).map { it.lo to it.hi },
                )
            }
        }
    }

    @Test
    fun gradeColor_returns_null_below_zero_for_one_sided_palettes() {
        val oneSided = GradePalette.entries.filter { gradeBandStops(it).descent.isEmpty() }
        oneSided.forEach { palette ->
            assertNotNull(
                "$palette should color a flat/climbing grade",
                gradeColor(0.0, palette, readable = false),
            )
            assertNull(
                "$palette must not color a descent",
                gradeColor(-0.1, palette, readable = false),
            )
        }
    }

    @Test
    fun gradeColor_still_colors_descents_on_turbo() {
        assertNotNull(gradeColor(-50.0, GradePalette.TURBO, readable = false))
    }

    @Test
    fun grades_inside_the_edges_take_the_neutral() {
        val c =
            gradeBandColor(
                grade = 3.0,
                palette = GradePalette.KAROO,
                climbEdge = 8.0,
                descentEdge = null,
                neutral = NEUTRAL,
                readable = false,
            )
        assertEquals(NEUTRAL, c)
    }

    @Test
    fun grades_outside_the_edges_take_their_own_band_colour() {
        val c =
            gradeBandColor(
                grade = 9.0,
                palette = GradePalette.KAROO,
                climbEdge = 8.0,
                descentEdge = null,
                neutral = NEUTRAL,
                readable = false,
            )
        assertEquals(Color(0xFFF08868), c)
    }

    // The edge itself is coloured, on both sides. The map rounds a cell's mean grade to 0.01 per
    // cent precisely so a near-edge grade lands on the edge, which decides which way it goes.

    @Test
    fun a_grade_on_the_climb_edge_takes_its_band_colour() {
        val c =
            gradeBandColor(
                grade = 8.0,
                palette = GradePalette.KAROO,
                climbEdge = 8.0,
                descentEdge = null,
                neutral = NEUTRAL,
                readable = false,
            )
        assertEquals(Color(0xFFF08868), c)
    }

    @Test
    fun a_grade_on_the_descent_edge_takes_its_band_colour() {
        val c =
            gradeBandColor(
                grade = -6.0,
                palette = GradePalette.BARBERFISH,
                climbEdge = null,
                descentEdge = -6.0,
                neutral = NEUTRAL,
                readable = false,
            )
        assertEquals(Color(0xFF5D99DE), c)
    }

    @Test
    fun a_null_edge_greys_that_whole_side() {
        val c =
            gradeBandColor(
                grade = 25.0,
                palette = GradePalette.KAROO,
                climbEdge = null,
                descentEdge = null,
                neutral = NEUTRAL,
                readable = false,
            )
        assertEquals(NEUTRAL, c)
    }

    // gradeBands' KDoc makes the floor guard the caller's job: the lowest band's open low end is
    // not a real floor on a one-sided palette. A descent edge stored against one is the case that
    // needs it, and gradeBandColor must answer it the way gradeColor already does.

    @Test
    fun a_descent_below_a_one_sided_palette_floor_stays_neutral() {
        val oneSided = GradePalette.entries.filter { gradeBandStops(it).descent.isEmpty() }
        oneSided.forEach { palette ->
            val c =
                gradeBandColor(
                    grade = -5.0,
                    palette = palette,
                    climbEdge = 2.0,
                    descentEdge = -3.0,
                    neutral = NEUTRAL,
                    readable = false,
                )
            assertEquals("$palette must not colour a descent", NEUTRAL, c)
            assertNull("$palette gradeColor agrees", gradeColor(-5.0, palette, readable = false))
        }
    }

    @Test
    fun two_sided_palettes_still_colour_their_deepest_descents() {
        val twoSided = GradePalette.entries.filter { gradeBandStops(it).descent.isNotEmpty() }
        twoSided.forEach { palette ->
            val c =
                gradeBandColor(
                    grade = -50.0,
                    palette = palette,
                    climbEdge = 2.0,
                    descentEdge = -3.0,
                    neutral = NEUTRAL,
                    readable = false,
                )
            assertNotEquals("$palette must colour a deep descent", NEUTRAL, c)
        }
    }

    @Test
    fun barberfish_has_a_neutral_flat_band_and_three_descent_bands() {
        val bands = gradeBands(GradePalette.BARBERFISH, readable = false)
        assertEquals(10, bands.size)
        val flat = bands.single { it.lo == -2.0 }
        assertEquals(2.0, flat.hi)
        assertEquals(Color(0xFFC4C4C4), flat.color)
        assertEquals(3, bands.count { (it.hi ?: Double.POSITIVE_INFINITY) <= 0.0 })
    }

    @Test
    fun barberfish_keeps_karoo_climb_colours_above_two_percent() {
        val bf =
            gradeBands(GradePalette.BARBERFISH, readable = false).filter { (it.lo ?: 0.0) >= 2.0 }
        val karoo =
            gradeBands(GradePalette.KAROO, readable = false).filter { (it.lo ?: 0.0) >= 2.0 }
        assertEquals(karoo.map { it.color }, bf.map { it.color })
    }

    // Exactly 0.0 is neither a climb nor a descent, so the edge comparisons never see it. An edge
    // of 0.0 only ever comes from "Off" (no palette has a 0.0 stop), and Off means the whole side
    // is on, edge included — so 0.0 takes its containing band, same as the map's inclusive edges.
    // FlatGrey equals this file's NEUTRAL, so the Barberfish cases pass a sentinel neutral.

    @Test
    fun an_exact_zero_grade_takes_its_containing_band_when_climbs_are_off() {
        val sentinel = Color(0xFF123456)
        val c =
            gradeBandColor(
                grade = 0.0,
                palette = GradePalette.BARBERFISH,
                climbEdge = 0.0,
                descentEdge = 0.0,
                neutral = sentinel,
                readable = false,
            )
        assertEquals(Color(0xFFC4C4C4), c)
    }

    @Test
    fun an_exact_zero_grade_takes_the_flattest_band_on_a_zero_opening_palette() {
        val flattest = gradeBands(GradePalette.KAROO, readable = false).first().color
        val c =
            gradeBandColor(
                grade = 0.0,
                palette = GradePalette.KAROO,
                climbEdge = 0.0,
                descentEdge = null,
                neutral = NEUTRAL,
                readable = false,
            )
        assertEquals(flattest, c)
    }

    @Test
    fun an_exact_zero_grade_is_coloured_by_a_zero_descent_edge_alone() {
        val sentinel = Color(0xFF123456)
        val c =
            gradeBandColor(
                grade = 0.0,
                palette = GradePalette.BARBERFISH,
                climbEdge = 2.0,
                descentEdge = 0.0,
                neutral = sentinel,
                readable = false,
            )
        assertEquals(Color(0xFFC4C4C4), c)
    }

    @Test
    fun an_exact_zero_grade_stays_neutral_inside_nonzero_edges() {
        val sentinel = Color(0xFF123456)
        val c =
            gradeBandColor(
                grade = 0.0,
                palette = GradePalette.BARBERFISH,
                climbEdge = 2.0,
                descentEdge = -2.0,
                neutral = sentinel,
                readable = false,
            )
        assertEquals(sentinel, c)
    }
}
