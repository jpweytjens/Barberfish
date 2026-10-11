package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.followedRoute
import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import io.hammerhead.karooext.models.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedRouteTest {

    private val poi = Symbol.POI(id = "cafe", lat = 50.0, lng = 4.0)
    private val pin = Symbol.POI(id = "pin", lat = 50.1, lng = 4.1)
    private val climb =
        NavigationState.Climb(
            startDistance = 500.0,
            length = 800.0,
            grade = 6.0,
            totalElevation = 48.0,
        )

    private fun loadedRoute(rejoinPolyline: String? = null, rejoinDistance: Double? = null) =
        NavigationState.NavigatingRoute(
            routePolyline = "abc",
            routeDistance = 1234.0,
            routeElevationPolyline = "e",
            rejoinPolyline = rejoinPolyline,
            rejoinDistance = rejoinDistance,
            name = "r",
            reversed = true,
            breadcrumb = false,
            pois = listOf(poi),
            climbs = listOf(climb),
        )

    private val pinPolyline = RouteFixtures.encoded(RouteFixtures.straight(3000.0, 1500.0))

    @Test
    fun `idle is no followed route`() {
        assertNull(NavigationState.Idle.followedRoute())
    }

    @Test
    fun `loaded route passes its fields through`() {
        val followed = loadedRoute().followedRoute()!!
        assertEquals("abc", followed.polyline)
        assertEquals("e", followed.elevationPolyline)
        assertTrue(followed.reversed)
        assertEquals(1234.0, followed.distanceM, 0.0)
        assertEquals(listOf(climb), followed.climbs)
        assertEquals(listOf(poi), followed.pois)
        assertNull(followed.rejoinPolyline)
        assertNull(followed.rejoinDistanceM)
        assertEquals("r", followed.name)
        assertNull(followed.destination)
    }

    @Test
    fun `reroute keeps its rejoin path`() {
        val followed = loadedRoute(rejoinPolyline = "xyz", rejoinDistance = 250.0).followedRoute()!!
        assertEquals("xyz", followed.rejoinPolyline)
        assertEquals(250.0, followed.rejoinDistanceM!!, 0.0)
        assertNull(followed.destination)
    }

    @Test
    fun `nav to pin carries its own route`() {
        val elevation = RouteFixtures.encodeElevation(listOf(0f to 100f, 4200f to 180f))
        val followed =
            NavigationState.NavigatingToDestination(
                    destination = pin,
                    polyline = pinPolyline,
                    elevationPolyline = elevation,
                    climbs = listOf(climb),
                )
                .followedRoute()!!
        assertEquals(pin, followed.destination)
        assertEquals(pinPolyline, followed.polyline)
        assertEquals(elevation, followed.elevationPolyline)
        assertFalse(followed.reversed)
        assertEquals(4200.0, followed.distanceM, 1.0)
        assertEquals(listOf(climb), followed.climbs)
        assertTrue(followed.pois.isEmpty())
        assertNull(followed.rejoinPolyline)
        assertNull(followed.rejoinDistanceM)
    }

    @Test
    fun `nav to pin without elevation measures its polyline`() {
        val followed =
            NavigationState.NavigatingToDestination(
                    destination = pin,
                    polyline = pinPolyline,
                    elevationPolyline = null,
                    climbs = emptyList(),
                )
                .followedRoute()!!
        val expected = cumulativeDistancesM(decodeGpsPolyline(pinPolyline)).last()
        assertEquals(expected, followed.distanceM, 0.01)
        assertEquals(3000.0, followed.distanceM, 5.0)
    }
}
