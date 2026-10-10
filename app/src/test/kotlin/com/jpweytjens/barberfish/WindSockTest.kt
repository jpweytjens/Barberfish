package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.WIND_SOCK_ID
import com.jpweytjens.barberfish.datatype.shared.destinationLatLng
import com.jpweytjens.barberfish.datatype.shared.formatHeadwind
import com.jpweytjens.barberfish.datatype.shared.latLngDistanceM
import com.jpweytjens.barberfish.datatype.shared.metresPerPixel
import com.jpweytjens.barberfish.datatype.shared.windFieldColor
import com.jpweytjens.barberfish.datatype.shared.windSockBands
import com.jpweytjens.barberfish.datatype.shared.windSockSymbol
import com.jpweytjens.barberfish.extension.ZoneColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WindSockTest {

    // --- bands ---

    private fun knots(kn: Double) = kn * 1852.0 / 3600.0

    @Test
    fun bands_follow_the_airfield_rule_of_one_per_3_knots() {
        assertEquals(0, windSockBands(0.0))
        assertEquals(0, windSockBands(knots(1.4)))
        assertEquals(1, windSockBands(knots(1.6)))
        assertEquals(1, windSockBands(knots(3.0)))
        assertEquals(3, windSockBands(knots(9.0)))
        assertEquals(4, windSockBands(knots(12.0)))
        assertEquals(5, windSockBands(knots(15.0)))
        assertEquals(5, windSockBands(knots(40.0)))
    }

    @Test
    fun negative_speed_is_treated_as_calm() {
        assertEquals(0, windSockBands(-1.0))
    }

    // --- number ---

    @Test
    fun headwind_prints_bare_and_tailwind_prints_minus() {
        assertEquals("12", formatHeadwind(12.4))
        assertEquals("-8", formatHeadwind(-7.6))
        assertEquals("0", formatHeadwind(0.3))
        assertEquals("0", formatHeadwind(-0.3))
        assertEquals("13", formatHeadwind(12.5))
    }

    // --- colour ---

    private fun kmh(kmh: Double) = kmh / 3.6

    @Test
    fun colour_is_red_into_the_wind_and_green_with_it() {
        val head = windFieldColor(kmh(20.0), ZoneColorMode.TEXT) as FieldColor.Threshold
        assertEquals(-1f, head.factor, 0.001f)
        val tail = windFieldColor(kmh(-10.0), ZoneColorMode.TEXT) as FieldColor.Threshold
        assertEquals(0.5f, tail.factor, 0.001f)
        val calm = windFieldColor(0.0, ZoneColorMode.TEXT) as FieldColor.Threshold
        assertEquals(0f, calm.factor, 0.001f)
    }

    @Test
    fun colour_saturates_at_20_kmh() {
        val strong = windFieldColor(kmh(45.0), ZoneColorMode.TEXT) as FieldColor.Threshold
        assertEquals(-1f, strong.factor, 0.001f)
    }

    @Test
    fun colour_off_gives_default() {
        assertEquals(FieldColor.Default, windFieldColor(kmh(20.0), ZoneColorMode.NONE))
    }

    // --- geometry on the map ---

    @Test
    fun destination_100m_north_moves_latitude_only() {
        val from = LatLng(50.0, 4.0)
        val to = destinationLatLng(from, 0.0, 100.0)
        assertEquals(50.0 + 100.0 / 111_320.0, to.lat, 1e-9)
        assertEquals(4.0, to.lng, 1e-9)
        assertEquals(100.0, latLngDistanceM(from, to), 0.5)
    }

    @Test
    fun destination_east_accounts_for_latitude() {
        val from = LatLng(60.0, 10.0)
        val to = destinationLatLng(from, 90.0, 100.0)
        assertEquals(60.0, to.lat, 1e-9)
        assertEquals(100.0, latLngDistanceM(from, to), 0.5)
    }

    @Test
    fun symbol_sits_on_the_mast_ahead_of_the_rider() {
        val fix = LatLng(50.0, 4.0)
        val zoom = 14.98
        val density = 1.875f
        val symbol =
            windSockSymbol(
                fix,
                courseDeg = 0.0,
                zoom = zoom,
                density = density,
                windFromDeg = 315.0,
                bands = 3,
            )
        assertNotNull(symbol)
        val s = symbol ?: return
        assertEquals(WIND_SOCK_ID, s.id)
        val expectedM = 53.0 * density * metresPerPixel(zoom)
        assertEquals(expectedM, latLngDistanceM(fix, LatLng(s.lat, s.lng)), 0.5)
        assertEquals(50.0 + expectedM / 111_320.0, s.lat, 1e-7)
    }

    @Test
    fun symbol_orientation_is_the_blows_toward_bearing() {
        val s =
            windSockSymbol(LatLng(50.0, 4.0), 90.0, 14.0, 1.875f, windFromDeg = 315.0, bands = 2)
        assertEquals(135f, s?.orientation ?: -1f, 0.001f)
        val wrap =
            windSockSymbol(LatLng(50.0, 4.0), 90.0, 14.0, 1.875f, windFromDeg = 200.0, bands = 2)
        assertEquals(20f, wrap?.orientation ?: -1f, 0.001f)
    }

    @Test
    fun a_stale_forecast_draws_the_muted_sock() {
        fun icon(muted: Boolean) =
            windSockSymbol(LatLng(50.0, 4.0), 0.0, 14.0, 1.875f, 315.0, bands = 2, muted = muted)
                ?.iconRes
        assertEquals(R.drawable.ic_wind_sock_2, icon(muted = false))
        assertEquals(R.drawable.ic_wind_sock_muted_2, icon(muted = true))
    }

    @Test
    fun calm_has_no_symbol() {
        assertNull(
            windSockSymbol(LatLng(50.0, 4.0), 0.0, 14.0, 1.875f, windFromDeg = 0.0, bands = 0)
        )
    }
}
