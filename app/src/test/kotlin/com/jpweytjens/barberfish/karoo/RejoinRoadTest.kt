package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.rejoinRoad
import com.jpweytjens.barberfish.extension.roadFollowStatus
import com.jpweytjens.barberfish.extension.saveGradeMapConfig
import com.jpweytjens.barberfish.extension.saveKarooFilesConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.Symbol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The rejoin line matched by the service, against a fake matcher that gives back the line it is
 * asked for, its heights landing on the climb route's profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RejoinRoadTest {
    @get:Rule val karoo = FakeKarooRule()

    private val realFactory = BarberfishExtension.roadMatcherFactory
    private val calls = AtomicInteger()
    private val inFlight = AtomicInteger()
    private val mostInFlight = AtomicInteger()

    // Held shut by a test that wants a match to still be running.
    private var gate = CountDownLatch(0)

    // The road under the line is the line itself, descending 10 m to the climb route's profile,
    // which is 100 m high over its first kilometre, where the rejoin line lands.
    private val echo = RoadMatcher { points ->
        calls.incrementAndGet()
        mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
        try {
            gate.await(20, TimeUnit.SECONDS)
            val lengthM = cumulativeDistancesM(points).last().toFloat()
            MatchedRoad(points, listOf(0f to 110f, lengthM to 100f))
        } finally {
            inFlight.decrementAndGet()
        }
    }

    // 300 m due south onto the route, 500 m from its start.
    private val rejoin =
        RouteFixtures.encoded(
            listOf(
                RouteFixtures.point(500.0, 300.0),
                RouteFixtures.point(500.0, 150.0),
                RouteFixtures.point(500.0, 0.0),
            )
        )

    private lateinit var host: FakeKarooHost

    @Before
    fun setUp() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        rejoinRoad.value = null
        roadFollowStatus.value = RoadFollowStatus.IDLE
        BarberfishExtension.roadMatcherFactory = { echo }
        shadowOf(karoo.app).grantPermissions(*ROAD_DATA_PERMISSIONS)
    }

    @After
    fun tearDown() {
        gate.countDown()
        BarberfishExtension.roadMatcherFactory = realFactory
        rejoinRoad.value = null
        roadFollowStatus.value = RoadFollowStatus.IDLE
    }

    private fun save(config: GradeMapConfig) = runBlocking { karoo.app.saveGradeMapConfig(config) }

    private fun save(config: KarooFilesConfig) = runBlocking {
        karoo.app.saveKarooFilesConfig(config)
    }

    private fun awaitTrue(condition: () -> Boolean) = karoo.awaitValue {
        true.takeIf { condition() }
    }

    private fun offRoute() = climbRoute().navigating(rejoin, rejoinDistance = 300.0)

    // Long enough for a config read and a match the fake answers at once.
    private fun settle() = Thread.sleep(500)

    @Test
    fun with_the_grade_map_off_a_rejoin_line_is_still_matched() {
        save(GradeMapConfig(enabled = false))
        save(KarooFilesConfig(true))
        host = karoo.startBarberfish()

        karoo.system.setNavigation(offRoute())

        awaitTrue { rejoinRoad.value != null }
        assertEquals(rejoin, rejoinRoad.value!!.rejoinPolyline)
        assertEquals(1, calls.get())
    }

    @Test
    fun with_the_switch_off_the_matcher_is_never_asked() {
        host = karoo.startBarberfish()

        karoo.system.setNavigation(offRoute())
        settle()

        assertEquals(0, calls.get())
        assertNull(rejoinRoad.value)
    }

    @Test
    fun without_storage_access_the_matcher_is_never_asked() {
        shadowOf(karoo.app).denyPermissions(*ROAD_DATA_PERMISSIONS)
        save(KarooFilesConfig(true))
        host = karoo.startBarberfish()

        karoo.system.setNavigation(offRoute())
        settle()

        assertEquals(0, calls.get())
        assertNull(rejoinRoad.value)
    }

    @Test
    fun the_line_going_away_withdraws_the_result() {
        save(KarooFilesConfig(true))
        host = karoo.startBarberfish()
        karoo.system.setNavigation(offRoute())
        awaitTrue { rejoinRoad.value != null }

        karoo.system.setNavigation(climbRoute().navigating())

        awaitTrue { rejoinRoad.value == null }
    }

    @Test
    fun turning_the_switch_off_withdraws_the_result() {
        save(KarooFilesConfig(true))
        host = karoo.startBarberfish()
        karoo.system.setNavigation(offRoute())
        awaitTrue { rejoinRoad.value != null }

        save(KarooFilesConfig(false))

        awaitTrue { rejoinRoad.value == null }
    }

    @Test
    fun navigation_to_a_pin_is_not_followed() {
        save(KarooFilesConfig(true))
        host = karoo.startBarberfish()

        karoo.system.setNavigation(
            OnNavigationState.NavigationState.NavigatingToDestination(
                destination = Symbol.POI(id = "pin", lat = 0.0, lng = 0.0),
                polyline = rejoin,
                elevationPolyline = null,
                climbs = emptyList(),
            )
        )
        settle()

        assertEquals(0, calls.get())
        assertNull(rejoinRoad.value)
    }

    @Test
    fun with_the_switch_off_the_service_holds_no_navigation_consumer() {
        host = karoo.startBarberfish()
        val started = karoo.system.consumerCount
        settle()

        assertEquals(started, karoo.system.consumerCount)
        assertTrue(karoo.system.consumerParams.none { it is OnNavigationState.Params })

        // The consumer comes with the switch and goes with it.
        save(KarooFilesConfig(true))
        awaitTrue { karoo.system.consumerParams.any { it is OnNavigationState.Params } }
        save(KarooFilesConfig(false))
        awaitTrue { karoo.system.consumerCount == started }
    }

    @Test
    fun the_rejoin_line_waits_for_a_running_route_match() {
        gate = CountDownLatch(1)
        save(KarooFilesConfig(true))
        host = karoo.startBarberfish()
        karoo.system.setNavigation(climbRoute().navigating())
        host.startMap()
        // The route's first piece holds the engine.
        awaitTrue { calls.get() == 1 }

        karoo.system.setNavigation(offRoute())
        settle()

        assertEquals(1, calls.get())
        gate.countDown()
        awaitTrue { rejoinRoad.value != null }
        assertEquals(1, mostInFlight.get())
    }
}
