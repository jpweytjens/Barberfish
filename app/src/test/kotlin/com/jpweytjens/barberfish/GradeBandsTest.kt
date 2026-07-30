package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GradeBandsTest {

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

    @Test
    fun gradeColor_returns_null_below_zero_for_one_sided_palettes() {
        val oneSided = GradePalette.entries.filter { gradeBandStops(it).descent.isEmpty() }
        oneSided.forEach { palette ->
            assertNotNull("$palette should color a flat/climbing grade", gradeColor(0.0, palette, readable = false))
            assertNull("$palette must not color a descent", gradeColor(-0.1, palette, readable = false))
        }
    }

    @Test
    fun gradeColor_still_colors_descents_on_turbo() {
        assertNotNull(gradeColor(-50.0, GradePalette.TURBO, readable = false))
    }
}
