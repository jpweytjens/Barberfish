package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.WindField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.Wind
import com.jpweytjens.barberfish.extension.RiderFix
import com.jpweytjens.barberfish.extension.WindFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import de.timklge.headwind.client.HeadwindForecastPoint
import de.timklge.headwind.client.HeadwindSnapshot
import de.timklge.headwind.client.WeatherData
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindFieldStateTest {

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
    private val imperial =
        metric.copy(
            preferredUnit =
                metric.preferredUnit.copy(distance = UserProfile.PreferredUnit.UnitType.IMPERIAL)
        )
    private val cfg = WindFieldConfig(colorMode = ZoneColorMode.TEXT)

    private fun kmh(fromDeg: Double, kmh: Double) = Wind(fromDeg, kmh / 3.6)

    private fun state(
        wind: Wind?,
        courseDeg: Double? = 0.0,
        colorMode: ZoneColorMode = ZoneColorMode.TEXT,
        profile: UserProfile = metric,
    ) =
        WindField.toFieldState(
            wind = wind,
            courseDeg = courseDeg,
            profile = profile,
            cfg = WindFieldConfig(colorMode = colorMode),
        )

    @Test
    fun front_right_headwind_gives_number_arrow_and_red() {
        // Riding north, 15 km/h from the north-east: 225 degrees, 15 cos 45 = 10.6 into the wind.
        val state = state(kmh(45.0, 15.0))
        assertEquals("11", state.primary)
        assertEquals("Wind", state.label)
        assertEquals(225f, state.windArrowDeg ?: -1f, 0.001f)
        assertTrue((state.color as FieldColor.Threshold).factor < 0f)
    }

    @Test
    fun tailwind_prints_minus_and_green() {
        val state = state(kmh(180.0, 8.0))
        assertEquals("-8", state.primary)
        assertEquals(0f, state.windArrowDeg ?: -1f, 0.001f)
        assertTrue((state.color as FieldColor.Threshold).factor > 0f)
    }

    @Test
    fun an_imperial_profile_reads_mph() {
        // 10 m/s straight ahead is 22.4 mph.
        assertEquals("22", state(Wind(0.0, 10.0), profile = imperial).primary)
    }

    @Test
    fun the_reading_turns_with_the_course() {
        // The same north wind: dead ahead riding north, on the left riding east.
        val north = state(kmh(0.0, 10.0), courseDeg = 0.0)
        val east = state(kmh(0.0, 10.0), courseDeg = 90.0)
        assertEquals("10", north.primary)
        assertEquals(180f, north.windArrowDeg ?: -1f, 0.001f)
        assertEquals("0", east.primary)
        assertEquals(90f, east.windArrowDeg ?: -1f, 0.001f)
    }

    @Test
    fun calm_draws_no_arrow_and_prints_zero() {
        val state = state(kmh(270.0, 1.0))
        assertEquals("0", state.primary)
        assertNull(state.windArrowDeg)
    }

    @Test
    fun no_wind_is_the_no_wind_data_state_and_drops_the_hud_column() {
        val state = state(null)
        assertEquals("No wind data", state.primary)
        assertTrue(state.noSensor)
        assertEquals(FieldColor.StreamState, state.color)
    }

    @Test
    fun colour_off_keeps_the_arrow_but_not_the_colour() {
        val state = state(kmh(0.0, 20.0), colorMode = ZoneColorMode.NONE)
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
        val state = state(kmh(45.0, 15.0), courseDeg = null)
        assertEquals("Searching…", state.primary)
        assertEquals("Wind", state.label)
    }

    @Test
    fun no_wind_data_shows_even_before_any_course() {
        assertEquals("No wind data", state(null, courseDeg = null).primary)
    }

    private val now = 1_791_288_000L

    /** A one-point forecast at [at]: [kmh] from [fromDeg], with no hourly entries. */
    private fun snapshot(at: LatLng, fromDeg: Double, kmh: Double) =
        HeadwindSnapshot(
            lastSuccessfulFetchEpochSeconds = now,
            forecast =
                listOf(
                    HeadwindForecastPoint(
                        at.lat,
                        at.lng,
                        null,
                        WeatherData(
                            time = now,
                            temperature = 15.0,
                            relativeHumidity = 60,
                            precipitation = 0.0,
                            cloudCover = 50.0,
                            sealevelPressure = 1013.0,
                            surfacePressure = 1000.0,
                            windSpeed = kmh / 3.6,
                            windDirection = fromDeg,
                            windGusts = kmh / 3.6,
                            weatherCode = 0,
                            isForecast = false,
                            isNight = false,
                            uvi = 0.0,
                        ),
                    )
                ),
        )

    @Test
    fun a_stale_reading_greys_but_keeps_its_number_and_arrow() {
        val fresh = state(kmh(45.0, 15.0))
        val stale = WindField.toFieldState(kmh(45.0, 15.0), 0.0, metric, cfg, stale = true)
        assertEquals(fresh.primary, stale.primary)
        assertEquals(fresh.windArrowDeg, stale.windArrowDeg)
        assertEquals(FieldColor.Muted, stale.color)
    }

    @Test
    fun the_forecast_goes_stale_after_two_hours_without_a_download() {
        assertEquals(false, WindField.isStale(now, now))
        assertEquals(false, WindField.isStale(now, now + 2 * 3600))
        assertEquals(true, WindField.isStale(now, now + 2 * 3600 + 1))
        // Without a download time the age is unknown; the reading is not vouched for.
        assertEquals(true, WindField.isStale(null, now))
    }

    @Test
    fun the_live_flow_greys_once_the_last_download_is_old() = runBlocking {
        val here = LatLng(51.0, 4.0)
        val colors = mutableListOf<FieldColor>()
        val clock = MutableStateFlow(now)
        val collector =
            launch(Dispatchers.Unconfined) {
                WindField.fieldStates(
                        fixes = MutableStateFlow(RiderFix(here, 0.0)),
                        snapshots = MutableStateFlow(snapshot(here, 0.0, 15.0)),
                        clock = clock,
                        profile = metric,
                        cfg = cfg,
                    )
                    .collect { colors += it.color }
            }
        yield()
        assertTrue(colors.last() is FieldColor.Threshold)
        clock.value = now + 3 * 3600
        yield()
        assertEquals(FieldColor.Muted, colors.last())
        collector.cancel()
    }

    @Test
    fun the_live_flow_projects_the_wind_onto_the_held_course() = runBlocking {
        val here = LatLng(51.0, 4.0)
        val fixes = MutableStateFlow(RiderFix(null, null))
        val snapshots = MutableStateFlow<HeadwindSnapshot?>(null)
        val seen = mutableListOf<String>()
        val collector =
            launch(Dispatchers.Unconfined) {
                WindField.fieldStates(
                        fixes = fixes,
                        snapshots = snapshots,
                        clock = flowOf(now),
                        profile = metric,
                        cfg = cfg,
                    )
                    .collect { seen += it.primary }
            }
        yield()
        assertEquals(listOf("No wind data"), seen)

        snapshots.value = snapshot(here, 45.0, 15.0)
        yield()
        assertEquals("Searching…", seen.last())

        fixes.value = RiderFix(here, 0.0)
        yield()
        assertEquals("11", seen.last())

        // At rest streamRiderFix keeps the last course; a new forecast still reaches the field,
        // projected onto that held course.
        snapshots.value = snapshot(here, 0.0, 15.0)
        yield()
        assertEquals("15", seen.last())

        fixes.value = RiderFix(here, 180.0)
        yield()
        assertEquals("-15", seen.last())

        collector.cancel()
    }
}
