package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.WindField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.extension.WindFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindFieldStateTest {

    private fun streaming(typeId: String, value: Double) =
        StreamState.Streaming(DataPoint(typeId, mapOf(DataType.Field.SINGLE to value)))

    private val metric =
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
    private val cfg = WindFieldConfig(colorMode = ZoneColorMode.TEXT)

    @Test
    fun front_right_headwind_gives_number_sock_and_red() {
        val state =
            WindField.toFieldState(
                angle = streaming("a", 225.0),
                headwindSpeed = streaming("h", 12.4),
                windSpeed = streaming("w", 15.0),
                profile = metric,
                cfg = cfg,
            )
        assertEquals("12", state.primary)
        assertEquals("Wind", state.label)
        assertEquals(3, state.windSock?.bands)
        assertEquals(225f, state.windSock?.angleDeg ?: -1f, 0.001f)
        assertTrue((state.color as FieldColor.Threshold).factor < 0f)
    }

    @Test
    fun tailwind_prints_minus_and_green() {
        val state =
            WindField.toFieldState(
                angle = streaming("a", 0.0),
                headwindSpeed = streaming("h", -7.6),
                windSpeed = streaming("w", 8.0),
                profile = metric,
                cfg = cfg,
            )
        assertEquals("-8", state.primary)
        assertTrue((state.color as FieldColor.Threshold).factor > 0f)
    }

    @Test
    fun calm_draws_no_sock_and_prints_zero() {
        val state =
            WindField.toFieldState(
                angle = streaming("a", 90.0),
                headwindSpeed = streaming("h", 0.2),
                windSpeed = streaming("w", 1.0),
                profile = metric,
                cfg = cfg,
            )
        assertEquals("0", state.primary)
        assertNull(state.windSock)
    }

    @Test
    fun missing_extension_is_the_no_headwind_app_state_and_drops_the_hud_column() {
        val state =
            WindField.toFieldState(
                angle = StreamState.NotAvailable,
                headwindSpeed = StreamState.NotAvailable,
                windSpeed = StreamState.NotAvailable,
                profile = metric,
                cfg = cfg,
            )
        assertEquals("No Headwind app", state.primary)
        assertTrue(state.noSensor)
        assertEquals(FieldColor.StreamState, state.color)
    }

    @Test
    fun colour_off_keeps_the_sock_but_not_the_colour() {
        val state =
            WindField.toFieldState(
                angle = streaming("a", 180.0),
                headwindSpeed = streaming("h", 20.0),
                windSpeed = streaming("w", 20.0),
                profile = metric,
                cfg = WindFieldConfig(colorMode = ZoneColorMode.NONE),
            )
        assertEquals(FieldColor.Default, state.color)
        assertEquals(4, state.windSock?.bands)
    }

    @Test
    fun preview_cycles_through_the_spectrum() {
        val states = WindField.previewStates(cfg)
        assertTrue(states.size >= 4)
        assertTrue(states.any { it.windSock == null })
        assertTrue(states.any { it.windSock?.bands == 5 })
    }
}
