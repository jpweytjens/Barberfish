package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.RouteFixtures.M_PER_DEG
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.roadFollowStatus
import com.jpweytjens.barberfish.extension.saveGradeMapConfig
import com.jpweytjens.barberfish.extension.saveKarooFilesConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.MapRecorder
import fi.nikosavola.karooext.testing.decodePolyline
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.OnNavigationState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/**
 * The band on the map with KAROO FILES on, against a fake matcher that puts the road 3 m north of
 * whatever line it is given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradeMapFollowRoadTest {
    @get:Rule val karoo = FakeKarooRule()

    private val realFactory = BarberfishExtension.roadMatcherFactory
    private val calls = AtomicInteger()

    // Held shut by a test that wants the match to still be running.
    private var gate = CountDownLatch(0)
    private var answers = true

    private val north3 = RoadMatcher { points ->
        calls.incrementAndGet()
        gate.await(20, TimeUnit.SECONDS)
        if (answers) MatchedRoad(points.map { it.copy(lat = it.lat + 3.0 / M_PER_DEG) }) else null
    }

    private lateinit var host: FakeKarooHost

    @Before
    fun setUp() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        roadFollowStatus.value = RoadFollowStatus.IDLE
        BarberfishExtension.roadMatcherFactory = { north3 }
        shadowOf(karoo.app).grantPermissions(*ROAD_DATA_PERMISSIONS)
        host = karoo.startBarberfish()
    }

    @After
    fun tearDown() {
        gate.countDown()
        BarberfishExtension.roadMatcherFactory = realFactory
        roadFollowStatus.value = RoadFollowStatus.IDLE
    }

    private fun save(config: GradeMapConfig) = runBlocking { karoo.app.saveGradeMapConfig(config) }

    private fun save(config: KarooFilesConfig) = runBlocking {
        karoo.app.saveKarooFilesConfig(config)
    }

    private fun <T : Any> await(value: () -> T?): T = karoo.awaitValue { value() }

    private fun awaitTrue(condition: () -> Boolean) = await { true.takeIf { condition() } }

    // How far north of the line y = [lineM] every point of the visible band lies, in metres.
    private fun MapRecorder.bandNorthM(lineM: Double = 0.0): List<Double> =
        visibleFills().values.flatMap { fill ->
            decodePolyline(fill.encodedPolyline).map { it.first * M_PER_DEG - lineM }
        }

    // The band is drawn on the polyline grid, which is 1.1 m at the equator.
    private fun MapRecorder.onRoad(lineM: Double = 0.0) =
        bandNorthM(lineM).let { north -> north.isNotEmpty() && north.all { it in 2.4..3.6 } }

    private fun MapRecorder.onChords() =
        bandNorthM().let { north -> north.isNotEmpty() && north.all { it in -0.6..0.6 } }

    /** Setting on, the climb route followed, the band on the road, then the map page closed. */
    private fun followedThenClosed(): MapRecorder {
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        awaitTrue { map.onRoad() && roadFollowStatus.value == RoadFollowStatus.FOLLOWING }
        host.stopMap(map)
        return map
    }

    @Test
    fun with_the_setting_on_the_band_moves_onto_the_road() {
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        map.awaitDrawn(karoo)

        awaitTrue { map.onRoad() }
        awaitTrue {
            val chevrons = map.visibleChevrons()
            chevrons.isNotEmpty() && chevrons.all { it.lat * M_PER_DEG in 2.9..3.1 }
        }
        assertEquals(RoadFollowStatus.FOLLOWING, roadFollowStatus.value)
    }

    @Test
    fun with_the_setting_off_the_matcher_is_never_asked() {
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        map.awaitDrawn(karoo)

        assertEquals(0, calls.get())
        assertTrue(map.onChords())
        assertEquals(RoadFollowStatus.IDLE, roadFollowStatus.value)
    }

    @Test
    fun on_an_unsupported_device_the_matcher_is_never_asked() {
        ShadowBuild.setSupportedAbis(arrayOf("x86_64"))
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        map.awaitDrawn(karoo)

        assertEquals(0, calls.get())
        assertTrue(map.onChords())
        assertEquals(RoadFollowStatus.IDLE, roadFollowStatus.value)
    }

    @Test
    fun without_storage_access_nothing_is_matched_until_it_is_granted() {
        shadowOf(karoo.app).denyPermissions(*ROAD_DATA_PERMISSIONS)
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        map.awaitDrawn(karoo)

        // Not started, so not ended as "no offline map" either.
        assertEquals(0, calls.get())
        assertTrue(map.onChords())
        assertEquals(RoadFollowStatus.IDLE, roadFollowStatus.value)

        // Granted without a restart of the process: the next redraw starts following.
        shadowOf(karoo.app).grantPermissions(*ROAD_DATA_PERMISSIONS)
        karoo.system.setMapZoom(18.0)

        awaitTrue { map.onRoad() }
    }

    @Test
    fun without_an_answer_the_band_is_todays() {
        answers = false
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        map.awaitDrawn(karoo)
        val today = map.visibleFills().mapValues { it.value.encodedPolyline }

        save(KarooFilesConfig(true))

        awaitTrue { roadFollowStatus.value == RoadFollowStatus.NO_MAP }
        map.awaitQuiet(quietMs = 800, timeoutMs = 15_000)
        assertEquals(today, map.visibleFills().mapValues { it.value.encodedPolyline })
    }

    @Test
    fun turning_the_setting_off_puts_the_band_back_on_the_chords() {
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        awaitTrue { map.onRoad() }

        save(KarooFilesConfig(false))

        awaitTrue { map.onChords() }
        awaitTrue { roadFollowStatus.value == RoadFollowStatus.IDLE }
    }

    @Test
    fun the_road_shape_arrives_without_blanking_the_band() {
        gate = CountDownLatch(1)
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        // The match is still running, so the band is on the map on its chords first.
        map.awaitDrawn(karoo)
        assertTrue(map.onChords())
        val drawn = map.items.size

        gate.countDown()

        awaitTrue { map.onRoad() }
        // Every rebuild withdraws the rejoin line when there is none to draw; that is not the
        // band. No piece of the band or its casing may go.
        val hidden =
            map.items
                .drop(drawn)
                .filterIsInstance<HidePolyline>()
                .map { it.id }
                .filter { it.startsWith("barberfish-seg") }
        assertTrue("band layers hidden while the road arrived: $hidden", hidden.isEmpty())
    }

    // With the map page closed: nothing in a map session is left to tell the follower.

    @Test
    fun closing_the_map_keeps_the_road() {
        followedThenClosed()
        assertEquals(RoadFollowStatus.FOLLOWING, roadFollowStatus.value)
        val asked = calls.get()

        val reopened = host.startMap()

        awaitTrue { reopened.onRoad() }
        assertEquals(asked, calls.get())
    }

    @Test
    fun the_setting_turned_off_with_the_map_closed_drops_the_road() {
        followedThenClosed()
        val asked = calls.get()

        save(KarooFilesConfig(false))

        awaitTrue { roadFollowStatus.value == RoadFollowStatus.IDLE }
        val reopened = host.startMap()
        reopened.awaitDrawn(karoo)
        assertTrue(reopened.onChords())
        assertEquals(asked, calls.get())
    }

    @Test
    fun the_grade_map_disabled_with_the_map_closed_drops_the_road() {
        followedThenClosed()

        save(GradeMapConfig(enabled = false))

        awaitTrue { roadFollowStatus.value == RoadFollowStatus.IDLE }
    }

    @Test
    fun navigation_cleared_with_the_map_closed_drops_the_road() {
        followedThenClosed()

        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)

        awaitTrue { roadFollowStatus.value == RoadFollowStatus.IDLE }
    }

    @Test
    fun a_new_route_with_the_map_closed_drops_the_old_road_and_its_pending_pieces() {
        val seen = CopyOnWriteArrayList<RoadFollowStatus>()
        val watching = CoroutineScope(Dispatchers.Unconfined)
        watching.launch { roadFollowStatus.collect { seen += it } }
        val before = karoo.system.consumerCount
        gate = CountDownLatch(1)
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        // The old route's match is still running when the map page closes.
        map.awaitDrawn(karoo)
        awaitTrue { calls.get() == 1 }
        host.stopMap(map)

        val north = 500.0
        val other =
            TestRoute(
                RouteFixtures.straight(lengthM = 4_000.0, stepM = 100.0, yM = north),
                RouteFixtures.flatElevation(4_000.0),
                4_000.0,
            )
        karoo.system.setNavigation(other.navigating())
        // The follower has let go of the navigation state: it has stopped following.
        awaitTrue { karoo.system.consumerCount == before }
        gate.countDown()

        // The old match now returns into a follower that no longer wants it.
        Thread.sleep(500)
        assertEquals(1, calls.get())
        assertFalse(RoadFollowStatus.FOLLOWING in seen)
        assertEquals(RoadFollowStatus.IDLE, roadFollowStatus.value)

        val reopened = host.startMap()
        awaitTrue { reopened.onRoad(lineM = north) }
        watching.cancel()
    }

    @Test
    fun a_dropped_road_releases_the_services_consumers() {
        val before = karoo.system.consumerCount
        followedThenClosed()
        assertTrue(karoo.system.consumerCount > before)

        save(KarooFilesConfig(false))

        awaitTrue { karoo.system.consumerCount == before }
    }
}
