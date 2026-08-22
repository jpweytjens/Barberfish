package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.FlatGrey
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.mapNeutral
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.screens.EdgeStop
import com.jpweytjens.barberfish.screens.GRADE_AXIS_MAX
import com.jpweytjens.barberfish.screens.GRADE_AXIS_MIN
import com.jpweytjens.barberfish.screens.GRADE_EDGE_OFF
import com.jpweytjens.barberfish.screens.axisFraction
import com.jpweytjens.barberfish.screens.barRuns
import com.jpweytjens.barberfish.screens.climbEdgeStops
import com.jpweytjens.barberfish.screens.descentEdgeStops
import com.jpweytjens.barberfish.screens.gradeCells
import com.jpweytjens.barberfish.screens.gradeTickStops
import com.jpweytjens.barberfish.screens.nearestEdgeStop
import com.jpweytjens.barberfish.screens.pressSide
import com.jpweytjens.barberfish.screens.reachableClimbStops
import com.jpweytjens.barberfish.screens.reachableDescentStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeCellGeometryTest {

    private val barberfish = gradeBands(GradePalette.BARBERFISH, readable = false)
    private val karoo = gradeBands(GradePalette.KAROO, readable = false)

    @Test
    fun barberfish_cells_tile_the_whole_axis() {
        val cells = gradeCells(barberfish)
        assertEquals(10, cells.size)
        assertEquals(GRADE_AXIS_MIN, cells.first().lo, 0.0)
        assertEquals(GRADE_AXIS_MAX, cells.last().hi, 0.0)
        assertEquals(40.0f, cells.map { it.weight }.sum(), 0.001f)
    }

    @Test
    fun exemplars_are_rounded_midpoints_ties_away_from_zero() {
        val cells = gradeCells(barberfish)
        assertEquals(
            listOf("-13", "-8", "-4", "0", "4", "7", "10", "13", "17", "23"),
            cells.map { it.exemplar },
        )
    }

    @Test
    fun one_sided_palettes_start_at_zero_not_the_clamp() {
        val cells = gradeCells(karoo)
        assertEquals(0.0, cells.first().lo, 0.0)
        assertEquals("1", cells.first().exemplar)
    }

    @Test
    fun tick_stops_are_real_thresholds_never_the_clamp() {
        assertEquals(
            listOf(-10.0, -6.0, -2.0, 2.0, 5.0, 8.0, 11.0, 14.0, 20.0),
            gradeTickStops(barberfish),
        )
        assertEquals(
            listOf(0.0, 2.0, 5.0, 8.0, 11.0, 14.0, 20.0),
            gradeTickStops(karoo),
        )
    }

    @Test
    fun axis_fraction_maps_the_clamp_to_zero_and_one() {
        assertEquals(0.0f, axisFraction(GRADE_AXIS_MIN), 0.0001f)
        assertEquals(1.0f, axisFraction(GRADE_AXIS_MAX), 0.0001f)
        assertEquals(0.375f, axisFraction(0.0), 0.0001f)
    }

    @Test
    fun zero_stop_exists_exactly_where_zero_is_a_band_edge() {
        // One-sided palettes floor at 0: climb side gains the stop.
        assertEquals(0.0, climbEdgeStops(GradePalette.KAROO).first().axisGrade, 0.0)
        // Turbo's bands meet at a 0 edge: both sides gain it.
        assertEquals(0.0, climbEdgeStops(GradePalette.TURBO).first().axisGrade, 0.0)
        assertEquals(0.0, descentEdgeStops(GradePalette.TURBO).last().axisGrade, 0.0)
        // Barberfish's flat band straddles zero: no 0 stop on either side, the crossover
        // stops at its far edges instead.
        assertTrue(climbEdgeStops(GradePalette.BARBERFISH).none { it.axisGrade == 0.0 })
        assertTrue(descentEdgeStops(GradePalette.BARBERFISH).none { it.axisGrade == 0.0 })
    }

    @Test
    fun off_stays_parked_at_the_axis_ends() {
        assertEquals(GRADE_EDGE_OFF, climbEdgeStops(GradePalette.TURBO).last().edge, 0.0)
        assertEquals(-GRADE_EDGE_OFF, descentEdgeStops(GradePalette.TURBO).first().edge, 0.0)
    }

    @Test
    fun stored_zero_lands_on_the_zero_stop_where_one_exists() {
        assertEquals(0.0, nearestEdgeStop(climbEdgeStops(GradePalette.KAROO), 0.0).edge, 0.0)
        // Barberfish has no zero stop: a stored 0.0 still snaps to the innermost stop.
        assertEquals(2.0, nearestEdgeStop(climbEdgeStops(GradePalette.BARBERFISH), 0.0).edge, 0.0)
    }

    @Test
    fun bar_runs_merge_the_filtered_middle_into_one_neutral_run() {
        val sage = mapNeutral(GradePalette.BARBERFISH, readable = false)
        val runs =
            barRuns(GradePalette.BARBERFISH, climbEdge = 14.0, descentEdge = -6.0, neutral = sage)
        // slate, deep teal, one merged neutral run (-6..14), red, purple
        assertEquals(listOf(5.0f, 4.0f, 20.0f, 6.0f, 5.0f), runs.map { it.weight })
        assertEquals(sage, runs[2].color)
    }

    @Test
    fun null_neutral_yields_a_groove_run() {
        val runs =
            barRuns(GradePalette.BARBERFISH, climbEdge = 14.0, descentEdge = -6.0, neutral = null)
        assertNull(runs[2].color)
        assertEquals(5, runs.size)
    }

    @Test
    fun fully_on_karoo_has_no_neutral_run() {
        val runs =
            barRuns(GradePalette.KAROO, climbEdge = 0.0, descentEdge = null, neutral = FlatGrey)
        assertTrue(runs.none { it.color == FlatGrey || it.color == null })
    }

    @Test
    fun reachable_stops_are_bounded_by_the_other_handle() {
        val climbAll = climbEdgeStops(GradePalette.BARBERFISH)
        val descentAll = descentEdgeStops(GradePalette.BARBERFISH)
        // Today's stops all sit on their own side, so nothing is filtered out: the meet
        // constraint only bites once crossover stops exist.
        val climbInner = nearestEdgeStop(climbAll, 2.0)
        assertEquals(descentAll, reachableDescentStops(descentAll, climbInner))
        val descentInner = nearestEdgeStop(descentAll, -2.0)
        assertEquals(climbAll, reachableClimbStops(climbAll, descentInner))
        // A null descent selection (one-sided palette) filters nothing either.
        assertEquals(climbAll, reachableClimbStops(climbAll, null))
        // The bound itself: a synthetic stop past the other handle is dropped, a stop at the
        // handle survives (handles may meet, never cross).
        val crossed = descentAll + EdgeStop(3.0, 3.0)
        assertEquals(descentAll, reachableDescentStops(crossed, climbInner))
    }

    // pressSide with hand-built stops, so these hold before and after crossover stops exist.
    // Climb at -2 (crossover), descent at -6: the press must grab by proximity, not sign.

    @Test
    fun press_grabs_the_side_whose_snap_target_is_nearest() {
        val climbStops =
            listOf(
                EdgeStop(-2.0, -2.0),
                EdgeStop(2.0, 2.0),
                EdgeStop(GRADE_AXIS_MAX, GRADE_EDGE_OFF),
            )
        val descentStops =
            listOf(
                EdgeStop(GRADE_AXIS_MIN, -GRADE_EDGE_OFF),
                EdgeStop(-10.0, -10.0),
                EdgeStop(-6.0, -6.0),
                EdgeStop(-2.0, -2.0),
            )
        val climbSel = climbStops[0]
        val descentSel = descentStops[2]
        // -5 is nearest the -6 descent stop: descent side, despite the climb handle at -2.
        assertEquals(true, pressSide(-5.0, climbSel, descentSel, climbStops, descentStops))
        // -3 ties between the -2 stops on both sides; the nearer handle (climb, at -2) wins.
        assertEquals(false, pressSide(-3.0, climbSel, descentSel, climbStops, descentStops))
        // A press in climb territory stays climb.
        assertEquals(false, pressSide(1.0, climbSel, descentSel, climbStops, descentStops))
    }

    @Test
    fun press_on_coincident_handles_defers_to_movement() {
        val climbStops =
            listOf(EdgeStop(0.0, 0.0), EdgeStop(3.0, 3.0), EdgeStop(GRADE_AXIS_MAX, GRADE_EDGE_OFF))
        val descentStops =
            listOf(
                EdgeStop(GRADE_AXIS_MIN, -GRADE_EDGE_OFF),
                EdgeStop(-3.0, -3.0),
                EdgeStop(0.0, 0.0),
            )
        val shared = EdgeStop(0.0, 0.0)
        // On the shared stop both sides tie completely: no side is named.
        assertNull(pressSide(0.0, shared, shared, climbStops, descentStops))
        // Away from it, tap-to-set still names the side that can reach the press.
        assertEquals(false, pressSide(2.0, shared, shared, climbStops, descentStops))
        assertEquals(true, pressSide(-2.0, shared, shared, climbStops, descentStops))
    }

    @Test
    fun press_on_a_one_sided_palette_is_always_climb() {
        val climbStops = climbEdgeStops(GradePalette.KAROO)
        assertEquals(false, pressSide(-8.0, climbStops.first(), null, climbStops, null))
    }

    @Test
    fun crossover_stops_sit_at_the_flat_bands_far_edges() {
        val climb = climbEdgeStops(GradePalette.BARBERFISH)
        assertEquals(EdgeStop(-2.0, -2.0), climb.first())
        assertEquals(
            listOf(-2.0, 2.0, 5.0, 8.0, 11.0, 14.0, 20.0, 25.0),
            climb.map { it.axisGrade },
        )
        val descent = descentEdgeStops(GradePalette.BARBERFISH)
        assertEquals(EdgeStop(2.0, 2.0), descent.last())
        assertEquals(listOf(-15.0, -10.0, -6.0, -2.0, 2.0), descent.map { it.axisGrade })
    }

    @Test
    fun zero_edge_palettes_gain_no_crossover_stops() {
        assertEquals(
            listOf(0.0, 2.0, 5.0, 8.0, 11.0, 14.0, 20.0, 25.0),
            climbEdgeStops(GradePalette.KAROO).map { it.axisGrade },
        )
        assertEquals(
            listOf(-15.0, -9.0, -6.0, -3.0, 0.0),
            descentEdgeStops(GradePalette.TURBO).map { it.axisGrade },
        )
    }

    @Test
    fun a_stored_zero_tie_snaps_toward_off() {
        // 0.0 sits exactly between the crossover and the innermost stop on both sides; the
        // conservative pick (nearer Off) colours less and keeps migrated configs stable.
        assertEquals(2.0, nearestEdgeStop(climbEdgeStops(GradePalette.BARBERFISH), 0.0).edge, 0.0)
        assertEquals(
            -2.0,
            nearestEdgeStop(descentEdgeStops(GradePalette.BARBERFISH), 0.0).edge,
            0.0,
        )
    }

    @Test
    fun a_crossed_stored_pair_resolves_into_a_meet() {
        // climb stored at -2, descent stored at +2: climb resolves first, the descent side is
        // bounded by it, and the pair displays as a meet at -2 instead of crossed handles.
        val climbSel = nearestEdgeStop(climbEdgeStops(GradePalette.BARBERFISH), -2.0)
        val descentStops =
            reachableDescentStops(descentEdgeStops(GradePalette.BARBERFISH), climbSel)
        assertTrue(descentStops.none { it.axisGrade > climbSel.axisGrade })
        assertEquals(-2.0, nearestEdgeStop(descentStops, 2.0).axisGrade, 0.0)
    }
}
