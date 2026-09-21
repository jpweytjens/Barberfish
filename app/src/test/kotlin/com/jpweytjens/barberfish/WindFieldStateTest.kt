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
    fun front_right_headwind_gives_number_arrow_and_red() {
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
        assertEquals(225f, state.windArrowDeg ?: -1f, 0.001f)
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
    fun calm_draws_no_arrow_and_prints_zero() {
        val state =
            WindField.toFieldState(
                angle = streaming("a", 90.0),
                headwindSpeed = streaming("h", 0.2),
                windSpeed = streaming("w", 1.0),
                profile = metric,
                cfg = cfg,
            )
        assertEquals("0", state.primary)
        assertNull(state.windArrowDeg)
    }

    @Test
    fun no_forecast_is_the_no_wind_data_state_and_drops_the_hud_column() {
        // Before the first forecast the extension's windSpeed stream is silent while the other
        // two already stream zeros; the SDK reports the silent one as NotAvailable.
        val state =
            WindField.toFieldState(
                angle = streaming("a", 0.0),
                headwindSpeed = streaming("h", 0.0),
                windSpeed = StreamState.NotAvailable,
                profile = metric,
                cfg = cfg,
            )
        assertEquals("No wind data", state.primary)
        assertTrue(state.noSensor)
        assertEquals(FieldColor.StreamState, state.color)
    }

    @Test
    fun colour_off_keeps_the_arrow_but_not_the_colour() {
        val state =
            WindField.toFieldState(
                angle = streaming("a", 180.0),
                headwindSpeed = streaming("h", 20.0),
                windSpeed = streaming("w", 20.0),
                profile = metric,
                cfg = WindFieldConfig(colorMode = ZoneColorMode.NONE),
            )
        assertEquals(FieldColor.Default, state.color)
        assertEquals(180f, state.windArrowDeg ?: -1f, 0.001f)
    }

    @Test
    fun preview_cycles_through_the_spectrum() {
        val states = WindField.previewStates(cfg)
        assertTrue(states.size >= 4)
        assertTrue(states.any { it.windArrowDeg == null })
        assertTrue(states.any { it.windArrowDeg == 180f })
    }

    private val live =
        WindField.toFieldState(
            angle = streaming("a", 225.0),
            headwindSpeed = streaming("h", 12.4),
            windSpeed = streaming("w", 15.0),
            profile = metric,
            cfg = cfg,
        )

    @Test
    fun before_any_course_the_field_is_searching() {
        val state = WindField.heldWindState(previous = null, hasCourse = false, fresh = live)
        assertEquals("Searching…", state.primary)
        assertEquals("Wind", state.label)
    }

    @Test
    fun with_a_course_the_fresh_reading_shows() {
        val state = WindField.heldWindState(previous = null, hasCourse = true, fresh = live)
        assertEquals(live, state)
    }

    @Test
    fun without_a_course_the_last_live_reading_is_held_ungreyed() {
        // At rest the extension reports a dead tailwind at full strength; ignore it.
        val restingTailwind =
            WindField.toFieldState(
                angle = streaming("a", 0.0),
                headwindSpeed = streaming("h", -15.0),
                windSpeed = streaming("w", 15.0),
                profile = metric,
                cfg = cfg,
            )
        val state =
            WindField.heldWindState(previous = live, hasCourse = false, fresh = restingTailwind)
        assertEquals(live, state)
    }

    @Test
    fun a_text_state_always_shows_even_without_a_course() {
        val state =
            WindField.heldWindState(
                previous = live,
                hasCourse = false,
                fresh = WindField.noWindData(),
            )
        assertEquals("No wind data", state.primary)
    }

    @Test
    fun a_held_text_state_is_not_treated_as_a_reading() {
        val state =
            WindField.heldWindState(
                previous = WindField.noWindData(),
                hasCourse = false,
                fresh = live,
            )
        assertEquals("Searching…", state.primary)
    }
}
