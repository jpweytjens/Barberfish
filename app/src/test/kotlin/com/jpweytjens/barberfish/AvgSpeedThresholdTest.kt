package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.avgSpeedFieldState
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.extension.AvgSpeedFieldConfig
import com.jpweytjens.barberfish.extension.AvgSpeedThresholdConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.ThresholdMode
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.UserProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AvgSpeedThresholdTest {

    // Decodes the way the settings store does: unknown keys are skipped.
    private val lenientJson = Json { ignoreUnknownKeys = true }

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

    private fun color(kph: Double, threshold: AvgSpeedThresholdConfig) =
        avgSpeedFieldState(
                kph / 3.6,
                AvgSpeedFieldConfig(threshold),
                metricProfile,
                includePaused = false,
            )
            .color

    @Test
    fun target_above_threshold_is_positive() {
        val color = color(27.5, AvgSpeedThresholdConfig(thresholdKph = 25.0))
        assertEquals(1f, (color as FieldColor.Threshold).factor, 0.001f)
    }

    @Test
    fun min_max_below_min_is_outside() {
        val threshold =
            AvgSpeedThresholdConfig(mode = ThresholdMode.MIN_MAX, minKph = 20.0, maxKph = 30.0)
        val danger = color(19.0, threshold) as FieldColor.DangerZone
        assertEquals(0.5f, danger.outsideFactor, 0.001f)
        assertEquals(1f, danger.borderProximity, 0.001f)
        assertEquals(true, danger.hasSafeZone)
    }

    // --- saved settings: the flat shape from before the threshold was split out ---

    @Test
    fun old_flat_config_keeps_colour_mode() {
        val old = """{"mode":"MIN_MAX","minKph":20.0,"colorMode":"BACKGROUND"}"""
        assertEquals(
            AvgSpeedFieldConfig(colorMode = ZoneColorMode.BACKGROUND),
            lenientJson.decodeFromString<AvgSpeedFieldConfig>(old),
        )
    }

    @Test
    fun old_slot_config_decodes() {
        val old =
            """{"field":{"type":"com.jpweytjens.barberfish.extension.HUDSlotField.AvgSpeed","includePaused":true},"avgSpeedConfig":{"mode":"MIN_MAX","minKph":20.0},"colorMode":"BACKGROUND"}"""
        val decoded = lenientJson.decodeFromString<HUDSlotConfig>(old)
        assertEquals(HUDSlotField.AvgSpeed(includePaused = true), decoded.field)
        assertEquals(AvgSpeedThresholdConfig(), decoded.avgSpeedThreshold)
        assertEquals(ZoneColorMode.BACKGROUND, decoded.colorMode)
    }
}
