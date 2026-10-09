package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.WindField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.extension.RiderFix
import com.jpweytjens.barberfish.extension.WindFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    private fun state(
        windFrom: StreamState,
        windSpeed: StreamState,
        courseDeg: Double? = 0.0,
        colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    ) =
        WindField.toFieldState(
            windDirection = windFrom,
            windSpeed = windSpeed,
            courseDeg = courseDeg,
            profile = metric,
            cfg = WindFieldConfig(colorMode = colorMode),
        )

    @Test
    fun front_right_headwind_gives_number_arrow_and_red() {
        // Riding north, 15 km/h from the north-east: 225 degrees, 15 cos 45 = 10.6 into the wind.
        val state = state(streaming("d", 45.0), streaming("w", 15.0))
        assertEquals("11", state.primary)
        assertEquals("Wind", state.label)
        assertEquals(225f, state.windArrowDeg ?: -1f, 0.001f)
        assertTrue((state.color as FieldColor.Threshold).factor < 0f)
    }

    @Test
    fun tailwind_prints_minus_and_green() {
        val state = state(streaming("d", 180.0), streaming("w", 8.0))
        assertEquals("-8", state.primary)
        assertEquals(0f, state.windArrowDeg ?: -1f, 0.001f)
        assertTrue((state.color as FieldColor.Threshold).factor > 0f)
    }

    @Test
    fun the_reading_turns_with_the_course() {
        // The same north wind: dead ahead riding north, on the left riding east.
        val north = state(streaming("d", 0.0), streaming("w", 10.0), courseDeg = 0.0)
        val east = state(streaming("d", 0.0), streaming("w", 10.0), courseDeg = 90.0)
        assertEquals("10", north.primary)
        assertEquals(180f, north.windArrowDeg ?: -1f, 0.001f)
        assertEquals("0", east.primary)
        assertEquals(90f, east.windArrowDeg ?: -1f, 0.001f)
    }

    @Test
    fun calm_draws_no_arrow_and_prints_zero() {
        val state = state(streaming("d", 270.0), streaming("w", 1.0))
        assertEquals("0", state.primary)
        assertNull(state.windArrowDeg)
    }

    @Test
    fun no_forecast_is_the_no_wind_data_state_and_drops_the_hud_column() {
        // Before the first forecast the extension's streams are silent; the SDK reports a silent
        // stream as NotAvailable.
        val state = state(StreamState.NotAvailable, StreamState.NotAvailable)
        assertEquals("No wind data", state.primary)
        assertTrue(state.noSensor)
        assertEquals(FieldColor.StreamState, state.color)
    }

    @Test
    fun one_silent_stream_is_enough_for_no_wind_data() {
        val state = state(streaming("d", 0.0), StreamState.Searching)
        assertEquals("No wind data", state.primary)
        assertTrue(state.noSensor)
    }

    @Test
    fun colour_off_keeps_the_arrow_but_not_the_colour() {
        val state = state(streaming("d", 0.0), streaming("w", 20.0), colorMode = ZoneColorMode.NONE)
        assertEquals(FieldColor.Default, state.color)
        assertEquals(180f, state.windArrowDeg ?: -1f, 0.001f)
    }

    @Test
    fun preview_cycles_through_the_spectrum() {
        val states = WindField.previewStates(cfg)
        assertTrue(states.size >= 4)
        assertTrue(states.any { it.windArrowDeg == null })
        assertTrue(states.any { it.windArrowDeg == 180f })
        // A two-digit tailwind, as wide as the arrow's reference reading.
        assertTrue(states.any { it.primary == "-24" })
    }

    @Test
    fun before_any_course_the_field_is_searching() {
        val state = state(streaming("d", 45.0), streaming("w", 15.0), courseDeg = null)
        assertEquals("Searching…", state.primary)
        assertEquals("Wind", state.label)
    }

    @Test
    fun no_wind_data_shows_even_before_any_course() {
        val state = state(StreamState.NotAvailable, StreamState.NotAvailable, courseDeg = null)
        assertEquals("No wind data", state.primary)
    }

    @Test
    fun the_live_flow_projects_the_wind_onto_the_held_course() = runBlocking {
        val here = LatLng(51.0, 4.0)
        val fixes = MutableStateFlow(RiderFix(null, null))
        val windFrom = MutableStateFlow(streaming("d", 45.0))
        val seen = mutableListOf<String>()
        val collector =
            launch(Dispatchers.Unconfined) {
                WindField.fieldStates(
                        fixes = fixes,
                        windDirection = windFrom,
                        windSpeed = MutableStateFlow(streaming("w", 15.0)),
                        profile = metric,
                        cfg = cfg,
                    )
                    .collect { seen += it.primary }
            }
        yield()
        assertEquals(listOf("Searching…"), seen)

        fixes.value = RiderFix(here, 0.0)
        yield()
        assertEquals("11", seen.last())

        // At rest streamRiderFix keeps the last course; a wind shift still reaches the field,
        // projected onto that held course.
        windFrom.value = streaming("d", 0.0)
        yield()
        assertEquals("15", seen.last())

        fixes.value = RiderFix(here, 180.0)
        yield()
        assertEquals("-15", seen.last())

        collector.cancel()
    }
}
