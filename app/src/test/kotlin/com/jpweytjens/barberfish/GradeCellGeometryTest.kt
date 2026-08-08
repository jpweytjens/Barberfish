package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.screens.GRADE_AXIS_MAX
import com.jpweytjens.barberfish.screens.GRADE_AXIS_MIN
import com.jpweytjens.barberfish.screens.GRADE_EDGE_OFF
import com.jpweytjens.barberfish.screens.axisFraction
import com.jpweytjens.barberfish.screens.climbEdgeStops
import com.jpweytjens.barberfish.screens.descentEdgeStops
import com.jpweytjens.barberfish.screens.gradeCells
import com.jpweytjens.barberfish.screens.gradeTickStops
import com.jpweytjens.barberfish.screens.nearestEdgeStop
import org.junit.Assert.assertEquals
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
        // Barberfish's flat band straddles zero: no 0 stop on either side.
        assertEquals(2.0, climbEdgeStops(GradePalette.BARBERFISH).first().axisGrade, 0.0)
        assertEquals(-2.0, descentEdgeStops(GradePalette.BARBERFISH).last().axisGrade, 0.0)
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
}
