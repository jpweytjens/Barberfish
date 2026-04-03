package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.cadenceFieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.extension.CadenceThresholdConfig
import com.jpweytjens.barberfish.extension.ThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceThresholdTest {

    // --- TARGET mode ---

    @Test fun target_disabled_returns_default() {
        val cfg = CadenceThresholdConfig(mode = ThresholdMode.TARGET, thresholdRpm = 0.0)
        assertEquals(FieldColor.Default, cadenceFieldColor(90.0, cfg))
    }

    @Test fun target_at_threshold_returns_zero_factor() {
        val cfg = CadenceThresholdConfig(mode = ThresholdMode.TARGET, thresholdRpm = 90.0)
        val color = cadenceFieldColor(90.0, cfg) as FieldColor.Threshold
        assertEquals(0f, color.factor, 0.001f)
    }

    @Test fun target_above_threshold_returns_positive_factor() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.TARGET,
            thresholdRpm = 90.0,
            rangePercentAbove = 10.0,
        )
        val color = cadenceFieldColor(99.0, cfg) as FieldColor.Threshold
        assertEquals(1.0f, color.factor, 0.001f)
    }

    @Test fun target_below_threshold_returns_negative_factor() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.TARGET,
            thresholdRpm = 90.0,
            rangePercentBelow = 10.0,
        )
        val color = cadenceFieldColor(81.0, cfg) as FieldColor.Threshold
        assertEquals(-1.0f, color.factor, 0.001f)
    }

    @Test fun target_factor_clamped_to_minus_one() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.TARGET,
            thresholdRpm = 90.0,
            rangePercentBelow = 10.0,
        )
        val color = cadenceFieldColor(50.0, cfg) as FieldColor.Threshold
        assertEquals(-1.0f, color.factor, 0.001f)
    }

    // --- MIN_MAX mode ---

    @Test fun min_max_both_null_returns_default() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.MIN_MAX,
            minRpm = null,
            maxRpm = null,
        )
        assertEquals(FieldColor.Default, cadenceFieldColor(90.0, cfg))
    }

    @Test fun min_max_inside_safe_zone_returns_zero_outside_factor() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.MIN_MAX,
            minRpm = 80.0,
            maxRpm = 100.0,
        )
        val color = cadenceFieldColor(90.0, cfg) as FieldColor.DangerZone
        assertEquals(0f, color.outsideFactor, 0.001f)
        assertTrue(color.hasSafeZone)
    }

    @Test fun min_max_below_min_returns_positive_outside_factor() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.MIN_MAX,
            minRpm = 80.0,
            maxRpm = 100.0,
            rangePercentBelow = 10.0,
        )
        val color = cadenceFieldColor(72.0, cfg) as FieldColor.DangerZone
        assertEquals(1.0f, color.outsideFactor, 0.001f)
        assertTrue(color.hasSafeZone)
    }

    @Test fun min_max_above_max_returns_positive_outside_factor() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.MIN_MAX,
            minRpm = 80.0,
            maxRpm = 100.0,
            rangePercentAbove = 10.0,
        )
        val color = cadenceFieldColor(110.0, cfg) as FieldColor.DangerZone
        assertEquals(1.0f, color.outsideFactor, 0.001f)
    }

    @Test fun min_only_no_safe_zone() {
        val cfg = CadenceThresholdConfig(
            mode = ThresholdMode.MIN_MAX,
            minRpm = 80.0,
            maxRpm = null,
        )
        val color = cadenceFieldColor(90.0, cfg) as FieldColor.DangerZone
        assertEquals(0f, color.outsideFactor, 0.001f)
        assertTrue(!color.hasSafeZone)
    }
}
