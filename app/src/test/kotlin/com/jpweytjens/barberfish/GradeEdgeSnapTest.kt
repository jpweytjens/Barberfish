package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.datatype.shared.GRADE_EDGE_OFF
import com.jpweytjens.barberfish.datatype.shared.climbEdgeStops
import com.jpweytjens.barberfish.datatype.shared.descentEdgeStops
import com.jpweytjens.barberfish.datatype.shared.gradeBandColor
import com.jpweytjens.barberfish.datatype.shared.selectGradeEdges
import com.jpweytjens.barberfish.datatype.shared.snapGradeEdges
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeEdgeSnapTest {

    // Stored grades across the axis in half-percent steps, plus both parked sentinels.
    // Half steps matter: they land exactly between stops and exercise the tie rule.
    private val sweep = (-30..50).map { it / 2.0 } + listOf(GRADE_EDGE_OFF, -GRADE_EDGE_OFF)

    @Test
    fun snapped_edges_are_stops_of_the_palette() {
        for (palette in GradePalette.entries) {
            val climbStops = climbEdgeStops(palette).map { it.edge }
            val descentStops = descentEdgeStops(palette).map { it.edge }
            for (climb in sweep) for (descent in sweep) {
                val (c, d) = snapGradeEdges(palette, climb, descent)
                assertTrue("$palette climb $climb -> $c", c in climbStops)
                if (d != null) assertTrue("$palette descent $descent -> $d", d in descentStops)
            }
        }
    }

    @Test
    fun snapping_is_idempotent() {
        for (palette in GradePalette.entries) {
            for (climb in sweep) for (descent in sweep) {
                val once = snapGradeEdges(palette, climb, descent)
                assertEquals(
                    "$palette $climb/$descent",
                    once,
                    snapGradeEdges(palette, once.first, once.second),
                )
            }
        }
    }

    @Test
    fun the_selection_agrees_with_the_snapped_pair() {
        for (palette in GradePalette.entries) {
            for (climb in sweep) for (descent in sweep) {
                val sel = selectGradeEdges(palette, climb, descent)
                val (c, d) = snapGradeEdges(palette, climb, descent)
                assertEquals(sel.climb.edge, c)
                assertEquals(sel.descent?.edge, d)
            }
        }
    }

    @Test
    fun null_sides_stay_null() {
        assertEquals(null to null, snapGradeEdges(GradePalette.TURBO, null, null))
        val (climb, descent) = snapGradeEdges(GradePalette.TURBO, 3.0, null)
        assertEquals(3.0, climb)
        assertNull(descent)
    }

    @Test
    fun parked_sentinels_snap_to_themselves() {
        assertEquals(
            GRADE_EDGE_OFF to -GRADE_EDGE_OFF,
            snapGradeEdges(GradePalette.TURBO, GRADE_EDGE_OFF, -GRADE_EDGE_OFF),
        )
    }

    @Test
    fun a_descent_edge_on_a_one_sided_palette_resolves_to_null() {
        val (_, descent) = snapGradeEdges(GradePalette.KAROO, 2.0, -3.0)
        assertNull(descent)
    }

    @Test
    fun turbo_edges_read_as_the_flat_band_edges_on_surgeonfish() {
        assertEquals(2.0 to -2.0, snapGradeEdges(GradePalette.SURGEONFISH, 3.0, -3.0))
    }

    @Test
    fun a_crossed_stored_pair_normalizes_into_a_meet() {
        // Climb parked at the crossover stop (-2); a stored descent of +2 cannot pass it.
        assertEquals(-2.0 to -2.0, snapGradeEdges(GradePalette.SURGEONFISH, -2.0, 2.0))
    }

    @Test
    fun a_tie_resolves_toward_off_on_both_sides() {
        // 0.0 sits exactly between Barberfish's crossover stops (-2 and 2).
        assertEquals(2.0 to -2.0, snapGradeEdges(GradePalette.BARBERFISH, 0.0, 0.0))
    }

    @Test
    fun sparkline_edges_read_through_the_palette_stops_and_storage_stays_raw() {
        val config = SparklineConfig(climbEdge = 3.0, descentEdge = -3.0)
        assertEquals(3.0 to -3.0, config.gradeEdges(GradePalette.TURBO))
        assertEquals(2.0 to -2.0, config.gradeEdges(GradePalette.SURGEONFISH))
        // Switching back restores the Turbo reading: nothing rewrote the stored value.
        assertEquals(3.0 to -3.0, config.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun the_reported_stale_descent_edge_no_longer_leaves_a_gap() {
        // Turbo descent -3 stored, palette switched to Surgeonfish, climb handle moved to -2.
        val config = SparklineConfig(climbEdge = -2.0, descentEdge = -3.0)
        val (climb, descent) = config.gradeEdges(GradePalette.SURGEONFISH)
        val color =
            gradeBandColor(
                grade = -2.5,
                palette = GradePalette.SURGEONFISH,
                climbEdge = climb,
                descentEdge = descent,
                neutral = Color.Unspecified,
                readable = false,
            )
        assertNotEquals(Color.Unspecified, color)
    }

    @Test
    fun map_edges_read_through_the_palette_stops() {
        val config = GradeMapConfig(climbEdge = 3.0, descentEdge = -3.0)
        assertEquals(2.0 to -2.0, config.gradeEdges(GradePalette.SURGEONFISH))
    }

    @Test
    fun migrated_counts_snap_like_stored_edges() {
        // Default skip counts (1, 0) migrate to (2.0, -2.0) on Barberfish: count zero starts past
        // the flat band, so the default is everything but the rest state.
        assertEquals(2.0 to -2.0, SparklineConfig().gradeEdges(GradePalette.BARBERFISH))
        // One-sided palettes: the descent side has no stops and stays null.
        assertEquals(2.0 to null, SparklineConfig().gradeEdges(GradePalette.KAROO))
    }

    @Test
    fun sparkline_edges_are_stops_of_the_palette() {
        // Through the config, not the resolver: this is the pair every renderer reads.
        val pairs = sweep.flatMap { climb -> sweep.map { descent -> climb to descent } }
        for (palette in GradePalette.entries) {
            val climbStops = climbEdgeStops(palette).map { it.edge }
            val descentStops = descentEdgeStops(palette).map { it.edge } + null
            for ((climb, descent) in pairs) {
                val (c, d) =
                    SparklineConfig(climbEdge = climb, descentEdge = descent).gradeEdges(palette)
                assertTrue("$palette climb $climb -> $c", c in climbStops)
                assertTrue("$palette descent $descent -> $d", d in descentStops)
            }
        }
    }
}
