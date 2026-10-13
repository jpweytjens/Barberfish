package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.extension.BarberfishExtension
import fi.nikosavola.karooext.testing.encodePolyline
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.OnNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradeMapOverlayTest {
    @get:Rule val karoo = FakeKarooRule()

    @Before
    fun clean() = clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun a_route_is_painted_as_grade_bands_with_a_casing_under_each() {
        karoo.system.setNavigation(climbRoute().navigating())
        val map = karoo.host<BarberfishExtension>().startMap()
        map.awaitDrawn(karoo)

        val fills = map.visibleFills().values.toList()
        fun fillAt(metres: Double) = fills.single { metres in it.spanM() }
        // The 8% climb is coloured apart from the flat ground either side of it.
        assertNotEquals(fillAt(500.0).color, fillAt(1_500.0).color)
        assertEquals(fillAt(500.0).color, fillAt(3_500.0).color)
        // Together the bands cover the route end to end.
        assertEquals(0.0, fills.minOf { it.spanM().start }, 30.0)
        assertEquals(4_000.0, fills.maxOf { it.spanM().endInclusive }, 30.0)
        assertTrue(fills.all { it.encodedPolyline.isNotEmpty() })

        // The casing has to sit beneath its fill. The map keeps a layer's slot when it is shown
        // again, so the recorder's insertion order is the stacking order.
        val order = map.visiblePolylines().keys.toList()
        map.visibleFills().keys.forEach { fillId ->
            assertTrue(
                "$fillId is under its casing: $order",
                order.indexOf("$fillId-casing") in 0 until order.indexOf(fillId),
            )
        }
        assertEquals(order.size, fills.size * 2)

        val chevrons = map.visibleChevrons()
        assertTrue(chevrons.isNotEmpty())
        chevrons.forEach {
            assertEquals(0.0, it.lat, 1e-6)
            assertTrue(metresEast(it.lng) in 0.0..4_000.0)
            assertEquals(90f, it.orientation, 1f)
        }
    }

    @Test
    fun clearing_navigation_takes_the_overlay_off_the_map() {
        karoo.system.setNavigation(climbRoute().navigating())
        val map = karoo.host<BarberfishExtension>().startMap()
        map.awaitDrawn(karoo)
        assertTrue(map.visibleChevrons().isNotEmpty())

        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)

        karoo.awaitValue {
            true.takeIf { map.visiblePolylines().isEmpty() && map.visibleSymbols().isEmpty() }
        }
    }

    @Test
    fun the_way_back_to_the_route_is_drawn_red_and_goes_when_the_rider_rejoins() {
        val route = climbRoute()
        val rejoin =
            encodePolyline(
                listOf(
                        RouteFixtures.point(1_500.0, 300.0),
                        RouteFixtures.point(1_500.0, 150.0),
                        RouteFixtures.point(1_500.0, 0.0),
                    )
                    .map { it.lat to it.lng }
            )
        karoo.system.setNavigation(route.navigating(rejoin, rejoinDistance = 300.0))
        val map = karoo.host<BarberfishExtension>().startMap()
        karoo.awaitValue { map.visiblePolylines()["barberfish-rejoin"] }
        map.awaitQuiet(quietMs = 1_500, timeoutMs = 15_000)

        assertEquals(0xFFF80000.toInt(), map.visiblePolylines().getValue("barberfish-rejoin").color)
        assertTrue(map.visiblePolylines().containsKey("barberfish-rejoin-casing"))
        val rejoinChevrons =
            map.visibleSymbols().keys.filter { it.startsWith("barberfish-rejoin-chev") }
        assertTrue(rejoinChevrons.isNotEmpty())

        karoo.system.setNavigation(route.navigating())

        karoo.awaitValue {
            true.takeIf {
                map.visiblePolylines().keys.none { it.startsWith("barberfish-rejoin") } &&
                    map.visibleSymbols().keys.none { it.startsWith("barberfish-rejoin") }
            }
        }
        assertTrue(map.visibleFills().isNotEmpty())
    }

    @Test
    fun a_new_map_session_clears_what_the_last_one_left_painted() {
        val host = karoo.host<BarberfishExtension>()
        karoo.system.setNavigation(climbRoute().navigating())
        val first = host.startMap()
        first.awaitDrawn(karoo)
        val paintedByFirst = paintedAfter(first)
        assertTrue(paintedByFirst.first.size > 2)
        host.stopMap(first)

        // The route changes while no map page is open, then the page opens again.
        karoo.system.setNavigation(flatRoute().navigating())
        val second = host.startMap()
        second.awaitDrawn(karoo)

        val (polylines, symbols) = paintedAfter(first, second)
        assertEquals(setOf("barberfish-seg-0", "barberfish-seg-0-casing"), polylines)
        assertEquals(second.visibleSymbols().keys, symbols)
    }
}
