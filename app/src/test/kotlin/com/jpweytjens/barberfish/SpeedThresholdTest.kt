package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.targetThresholdColor
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import io.hammerhead.karooext.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedThresholdTest {

    private val metricProfile =
        UserProfile(
            weight = 70f,
            preferredUnit =
                UserProfile.PreferredUnit(
                    distance = UserProfile.PreferredUnit.UnitType.METRIC,
                    elevation = UserProfile.PreferredUnit.UnitType.METRIC,
                    temperature = UserProfile.PreferredUnit.UnitType.METRIC,
                    weight = UserProfile.PreferredUnit.UnitType.METRIC,
                ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList(),
        )

    // --- targetThresholdColor pure helper ---

    @Test fun helper_zero_threshold_returns_default() {
        assertEquals(FieldColor.Default, targetThresholdColor(25.0, 0.0, 10.0, 10.0))
    }

    @Test fun helper_negative_threshold_returns_default() {
        // Mirrors the legacy thresholdKph <= 0.0 guard in avgSpeedFieldState.
        assertEquals(FieldColor.Default, targetThresholdColor(25.0, -5.0, 10.0, 10.0))
    }

    @Test fun helper_at_threshold_returns_zero_factor() {
        val color = targetThresholdColor(30.0, 30.0, 10.0, 10.0) as FieldColor.Threshold
        assertEquals(0f, color.factor, 0.001f)
    }

    @Test fun helper_above_threshold_within_range() {
        // 30 + 5% of 30 = 31.5 → factor = (1.5/30 * 100) / 10 = 0.5
        val color = targetThresholdColor(31.5, 30.0, 10.0, 10.0) as FieldColor.Threshold
        assertEquals(0.5f, color.factor, 0.001f)
    }

    @Test fun helper_above_threshold_clamps_to_one() {
        // 30 + 20% — beyond the 10% above range, clamps to +1.
        val color = targetThresholdColor(36.0, 30.0, 10.0, 10.0) as FieldColor.Threshold
        assertEquals(1f, color.factor, 0.001f)
    }

    @Test fun helper_below_threshold_clamps_to_minus_one() {
        val color = targetThresholdColor(20.0, 30.0, 10.0, 10.0) as FieldColor.Threshold
        assertEquals(-1f, color.factor, 0.001f)
    }

    @Test fun helper_asymmetric_ranges_pick_above_when_above() {
        // Above branch uses rangePercentAbove=20; 30+10% = 33 → (3/30 * 100) / 20 = 0.5.
        val color = targetThresholdColor(33.0, 30.0, 5.0, 20.0) as FieldColor.Threshold
        assertEquals(0.5f, color.factor, 0.001f)
    }

    @Test fun helper_asymmetric_ranges_pick_below_when_below() {
        // Below branch uses rangePercentBelow=5; 30-2.5% = 29.25 → (-0.75/30 * 100) / 5 = -0.5.
        val color = targetThresholdColor(29.25, 30.0, 5.0, 20.0) as FieldColor.Threshold
        assertEquals(-0.5f, color.factor, 0.001f)
    }

    // --- previewStates: configured coloring is reflected in preview frames ---

    @Test fun preview_fixed_disabled_shows_default() {
        val cfg = SpeedFieldConfig(
            smoothing = SpeedSmoothingStream.S3,
            source = SpeedThresholdSource.FIXED,
            thresholdKph = 0.0,
        )
        val states = SpeedField.previewStates(cfg, metricProfile)
        assertTrue(states.isNotEmpty())
        assertTrue(states.all { it.color is FieldColor.Default })
    }

    @Test fun preview_fixed_enabled_produces_colored_transitions() {
        val cfg = SpeedFieldConfig(
            smoothing = SpeedSmoothingStream.S3,
            source = SpeedThresholdSource.FIXED,
            thresholdKph = 30.0,
        )
        val states = SpeedField.previewStates(cfg, metricProfile)
        val thresholds = states.mapNotNull { it.color as? FieldColor.Threshold }
        assertEquals(states.size, thresholds.size)
        // The synthetic offset set [-0.15..0.15] straddles the threshold, so the preview
        // should show at least one negative factor (below) and one positive (above).
        assertTrue("expected at least one below-threshold frame", thresholds.any { it.factor < 0f })
        assertTrue("expected at least one above-threshold frame", thresholds.any { it.factor > 0f })
    }

    @Test fun preview_avg_total_uses_synthetic_threshold_without_warmup() {
        // AVG sources have no live avg in preview; the synthetic 25 km/h center should
        // still produce colored transitions (preview must NOT apply the warmup gate).
        val cfg = SpeedFieldConfig(
            smoothing = SpeedSmoothingStream.S3,
            source = SpeedThresholdSource.AVG_TOTAL,
        )
        val states = SpeedField.previewStates(cfg, metricProfile)
        assertTrue(states.all { it.color is FieldColor.Threshold })
    }

    @Test fun preview_avg_moving_uses_synthetic_threshold_without_warmup() {
        val cfg = SpeedFieldConfig(
            smoothing = SpeedSmoothingStream.S3,
            source = SpeedThresholdSource.AVG_MOVING,
        )
        val states = SpeedField.previewStates(cfg, metricProfile)
        assertTrue(states.all { it.color is FieldColor.Threshold })
    }
}
