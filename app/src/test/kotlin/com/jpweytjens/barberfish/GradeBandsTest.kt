package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
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
}
