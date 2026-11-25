package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.minMaxThresholdColor
import com.jpweytjens.barberfish.datatype.shared.targetThresholdColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The min/max colour rule, shared by cadence and average speed. Bounds 85 to 95, 10 % ranges. */
class ThresholdColoringTest {

    private fun color(
        value: Double,
        min: Double? = 85.0,
        max: Double? = 95.0,
        rangePercent: Double = 10.0,
    ) = minMaxThresholdColor(value, min, max, rangePercent, rangePercent)

    @Test
    fun both_bounds_null_is_default() {
        assertEquals(FieldColor.Default, color(90.0, min = null, max = null))
    }

    @Test
    fun below_min_is_outside() {
        val danger = color(82.0) as FieldColor.DangerZone
        assertEquals(3.0f / 8.5f, danger.outsideFactor, 0.001f)
        assertEquals(1f, danger.borderProximity, 0.001f)
        assertTrue(danger.hasSafeZone)
    }

    @Test
    fun inside_reports_border_proximity() {
        val danger = color(86.0) as FieldColor.DangerZone
        assertEquals(0f, danger.outsideFactor, 0.001f)
        assertEquals(1f - 1.0f / 8.5f, danger.borderProximity, 0.001f)
        assertTrue(danger.hasSafeZone)
    }

    @Test
    fun above_max_is_outside() {
        val danger = color(100.0) as FieldColor.DangerZone
        assertEquals(5.0f / 9.5f, danger.outsideFactor, 0.001f)
    }

    @Test
    fun min_only_has_no_safe_zone() {
        val danger = color(90.0, max = null) as FieldColor.DangerZone
        assertFalse(danger.hasSafeZone)
    }

    @Test
    fun zero_range_below_min_is_fully_outside() {
        val danger = color(82.0, rangePercent = 0.0) as FieldColor.DangerZone
        assertEquals(1f, danger.outsideFactor, 0f)
    }

    @Test
    fun negative_range_below_min_is_fully_outside() {
        val danger = color(82.0, rangePercent = -10.0) as FieldColor.DangerZone
        assertEquals(1f, danger.outsideFactor, 0f)
    }

    // --- target rule: a range of 0 % or less leaves no fade, only the two sides ---

    private fun targetFactor(value: Double, rangePercent: Double) =
        (targetThresholdColor(value, 30.0, rangePercent, rangePercent) as FieldColor.Threshold)
            .factor

    @Test
    fun zero_range_at_the_target_is_neutral() {
        assertEquals(0f, targetFactor(30.0, 0.0), 0f)
    }

    @Test
    fun zero_range_off_the_target_is_fully_coloured() {
        assertEquals(1f, targetFactor(30.1, 0.0), 0f)
        assertEquals(-1f, targetFactor(29.9, 0.0), 0f)
    }

    @Test
    fun negative_range_does_not_swap_the_sides() {
        assertEquals(1f, targetFactor(31.0, -10.0), 0f)
        assertEquals(-1f, targetFactor(29.0, -10.0), 0f)
    }
}
