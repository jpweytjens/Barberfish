package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.edgesFromSkipBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeEdgeMigrationTest {

    @Test
    fun karoo_skip_one_becomes_the_two_percent_edge() {
        val (climb, descent) =
            edgesFromSkipBands(skipBands = 1, skipBandsDescent = 0, palette = GradePalette.KAROO)
        assertEquals(2.0, climb)
        assertNull(descent)
    }

    @Test
    fun skip_zero_maps_to_a_zero_edge_on_both_sides() {
        val (karooClimb, _) =
            edgesFromSkipBands(skipBands = 0, skipBandsDescent = 0, palette = GradePalette.KAROO)
        assertEquals(0.0, karooClimb)

        val (turboClimb, turboDescent) =
            edgesFromSkipBands(skipBands = 0, skipBandsDescent = 0, palette = GradePalette.TURBO)
        assertEquals(0.0, turboClimb)
        assertEquals(0.0, turboDescent)
    }

    @Test
    fun turbo_descent_counts_map_through_the_negative_stops() {
        val (_, descent) =
            edgesFromSkipBands(skipBands = 1, skipBandsDescent = 1, palette = GradePalette.TURBO)
        assertEquals(-3.0, descent)
    }

    @Test
    fun counts_past_the_last_stop_clamp_to_it() {
        val (climb, descent) =
            edgesFromSkipBands(skipBands = 99, skipBandsDescent = 99, palette = GradePalette.TURBO)
        assertEquals(15.0, climb)
        assertEquals(-9.0, descent)
    }

    @Test
    fun a_descent_count_on_a_one_sided_palette_stays_uncoloured() {
        val (_, descent) =
            edgesFromSkipBands(skipBands = 1, skipBandsDescent = 3, palette = GradePalette.GARMIN)
        assertNull(descent)
    }

    @Test
    fun barberfish_counts_map_through_its_own_stops() {
        val (climb, descent) =
            edgesFromSkipBands(
                skipBands = 3,
                skipBandsDescent = 2,
                palette = GradePalette.BARBERFISH,
            )
        assertEquals(8.0, climb)
        assertEquals(-6.0, descent)
    }
}
