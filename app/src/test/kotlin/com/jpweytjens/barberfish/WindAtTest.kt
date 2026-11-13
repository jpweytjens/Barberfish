package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.Wind
import com.jpweytjens.barberfish.datatype.shared.windAt
import de.timklge.headwind.client.HeadwindForecastPoint
import de.timklge.headwind.client.WeatherData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindAtTest {

    private val t1400 = 1_791_288_000L
    private val hour = 3600L

    private fun weather(time: Long, fromDeg: Double, speedMs: Double) =
        WeatherData(
            time = time,
            temperature = 15.0,
            relativeHumidity = 60,
            precipitation = 0.0,
            cloudCover = 50.0,
            sealevelPressure = 1013.0,
            surfacePressure = 1000.0,
            windSpeed = speedMs,
            windDirection = fromDeg,
            windGusts = speedMs,
            weatherCode = 0,
            isForecast = true,
            isNight = false,
            uvi = 0.0,
        )

    /** A forecast point on the equator; one degree of longitude there is about 111 km. */
    private fun point(lonDeg: Double, current: WeatherData, vararg hourly: WeatherData) =
        HeadwindForecastPoint(0.0, lonDeg, null, current, hourly.toList())

    private val rider = LatLng(0.0, 0.0)

    private fun assertWind(fromDeg: Double, speedMs: Double, actual: Wind?) {
        requireNotNull(actual)
        assertEquals(speedMs, actual.speedMs, 1e-6)
        val d = abs(actual.fromDeg - fromDeg) % 360.0
        assertEquals(0.0, minOf(d, 360.0 - d), 1e-6)
    }

    @Test fun no_forecast_is_no_wind() = assertNull(windAt(emptyList(), rider, t1400))

    @Test
    fun blends_linearly_between_the_hours_around_now() {
        val p =
            point(
                0.0,
                weather(t1400 - 600, 270.0, 99.0),
                weather(t1400, 270.0, 10.0),
                weather(t1400 + hour, 270.0, 4.0),
            )
        // 14:20 is a third of the way from 14:00 to 15:00.
        assertWind(270.0, 8.0, windAt(listOf(p), rider, t1400 + 1200))
    }

    @Test
    fun before_the_first_hour_blends_from_the_current_reading() {
        val p = point(0.0, weather(t1400 - 1800, 270.0, 2.0), weather(t1400, 270.0, 6.0))
        // 13:45 is halfway from the 13:30 reading to the 14:00 forecast.
        assertWind(270.0, 4.0, windAt(listOf(p), rider, t1400 - 900))
    }

    @Test
    fun after_the_last_hour_holds_it() {
        val p = point(0.0, weather(t1400 - 600, 270.0, 2.0), weather(t1400, 270.0, 6.0))
        assertWind(270.0, 6.0, windAt(listOf(p), rider, t1400 + 5 * hour))
    }

    @Test
    fun without_hours_the_current_reading_holds_even_at_its_own_time() {
        val p = point(0.0, weather(t1400, 90.0, 3.0))
        assertWind(90.0, 3.0, windAt(listOf(p), rider, t1400))
        assertWind(90.0, 3.0, windAt(listOf(p), rider, t1400 + hour))
    }

    @Test
    fun weights_the_two_nearest_points_by_inverse_distance() {
        val a = point(-0.1, weather(t1400, 270.0, 8.0))
        val b = point(0.3, weather(t1400, 270.0, 4.0))
        // 11 km from A and 33 km from B: 0.75 of A plus 0.25 of B.
        assertWind(270.0, 7.0, windAt(listOf(a, b), rider, t1400))
    }

    @Test
    fun ignores_all_but_the_two_nearest_points() {
        val a = point(-0.1, weather(t1400, 270.0, 8.0))
        val b = point(0.3, weather(t1400, 270.0, 4.0))
        val far = point(2.0, weather(t1400, 90.0, 30.0))
        assertWind(270.0, 7.0, windAt(listOf(far, a, b), rider, t1400))
    }

    @Test
    fun at_a_point_reads_that_point() {
        val a = point(0.0, weather(t1400, 200.0, 5.0))
        val b = point(0.5, weather(t1400, 20.0, 9.0))
        assertWind(200.0, 5.0, windAt(listOf(a, b), rider, t1400))
    }

    @Test
    fun two_points_at_the_rider_read_the_first() {
        val a = point(0.0, weather(t1400, 200.0, 5.0))
        val b = point(0.0, weather(t1400, 20.0, 9.0))
        assertWind(200.0, 5.0, windAt(listOf(a, b), rider, t1400))
    }

    @Test
    fun without_a_position_reads_the_first_point() {
        val a = point(-0.1, weather(t1400, 200.0, 5.0))
        val b = point(0.0, weather(t1400, 20.0, 9.0))
        assertWind(200.0, 5.0, windAt(listOf(a, b), null, t1400))
    }

    @Test
    fun opposing_winds_cancel_halfway() {
        val a = point(-0.2, weather(t1400, 0.0, 10.0))
        val b = point(0.2, weather(t1400, 180.0, 10.0))
        val wind = requireNotNull(windAt(listOf(a, b), rider, t1400))
        assertEquals(0.0, wind.speedMs, 1e-9)
    }

    @Test
    fun a_veer_across_north_blends_as_a_vector() {
        val a = point(-0.2, weather(t1400, 350.0, 10.0))
        val b = point(0.2, weather(t1400, 10.0, 10.0))
        // Halfway between 10 m/s from 350 and from 10: from north, 10 cos(10 deg) m/s.
        assertWind(0.0, 10.0 * cos(10.0 * PI / 180.0), windAt(listOf(a, b), rider, t1400))
    }

    @Test
    fun blends_in_space_and_time_together() {
        val a =
            point(
                -0.1,
                weather(t1400, 270.0, 8.0),
                weather(t1400, 270.0, 8.0),
                weather(t1400 + hour, 270.0, 12.0),
            )
        val b =
            point(
                0.3,
                weather(t1400, 270.0, 4.0),
                weather(t1400, 270.0, 4.0),
                weather(t1400 + hour, 270.0, 0.0),
            )
        // 14:30: A is at 10, B at 2; then 0.75 of A plus 0.25 of B.
        assertWind(270.0, 8.0, windAt(listOf(a, b), rider, t1400 + 1800))
    }
}
