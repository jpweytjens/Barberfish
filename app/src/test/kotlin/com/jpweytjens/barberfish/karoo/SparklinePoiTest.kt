package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.globalPoiProjections
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.RejoinRoad
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.rejoinRoad
import com.jpweytjens.barberfish.extension.saveFieldSparklineConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.ViewRecorder
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.Symbol
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Saved POIs reach the elevation strip as markers, drawn pixels that are compared across frames.
 * The SDK drops a frame within 900 ms of the last, read from the real clock, so that clock is
 * virtualized and advanced before each change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SparklinePoiTest {
    @get:Rule val karoo = FakeKarooRule()
    private lateinit var host: FakeKarooHost

    @Before
    fun startOnTheRoute() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        rejoinRoad.value = null
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setDistanceRemaining(4_000.0)
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    @After
    fun withdrawTheRejoinLine() {
        rejoinRoad.value = null
    }

    private fun poiAt(id: String, metresEast: Double, metresNorth: Double) =
        RouteFixtures.point(metresEast, metresNorth).let { Symbol.POI(id, it.lat, it.lng) }

    private val nearPoi
        get() = listOf(poiAt("near", 2_000.0, metresNorth = 100.0))

    /** The strip in the field's latest frame. */
    private fun ViewRecorder.strip(): Bitmap =
        frames.last().inflate(karoo.app, TALL_FIELD_CONFIG).sparklineValue()

    /** Opens the field with no saved POIs and returns it with the unmarked strip it draws. */
    private fun openBareStrip(): Pair<ViewRecorder, Bitmap> {
        karoo.system.setGlobalPois(emptyList())
        val view = host.startView("elevation-sparkline", TALL_FIELD_CONFIG)
        view.awaitFrame(15_000)
        return view to view.strip()
    }

    /**
     * Applies [change], then waits for a strip that satisfies [expected]. A field's first frame can
     * be drawn before the POIs reach it, so a fresh view proves nothing about them; a change in the
     * open view's strip does.
     */
    private fun ViewRecorder.stripAfter(change: () -> Unit, expected: (Bitmap) -> Boolean): Bitmap {
        RobolectricPump.advanceBy(1.seconds)
        change()
        return karoo.awaitValue { strip().takeIf(expected) }
    }

    @Test
    fun a_saved_poi_beside_the_route_gets_a_marker_and_one_far_away_does_not() {
        val (view, bare) = openBareStrip()

        // A POI 100 m off the route is marked.
        view.stripAfter({ karoo.system.setGlobalPois(nearPoi) }) { !it.sameAs(bare) }

        // A POI 5 km off the route is not. The marker going also shows the new list arrived.
        val farPoi = listOf(poiAt("far", 2_000.0, metresNorth = 5_000.0))
        view.stripAfter({ karoo.system.setGlobalPois(farPoi) }) { it.sameAs(bare) }
    }

    @Test
    fun poi_markers_can_be_turned_off_in_settings() {
        val (view, bare) = openBareStrip()
        view.stripAfter({ karoo.system.setGlobalPois(nearPoi) }) { !it.sameAs(bare) }

        view.stripAfter({
            runBlocking { karoo.app.saveFieldSparklineConfig(SparklineConfig(showPois = false)) }
        }) {
            it.sameAs(bare)
        }
    }

    // --- off the route ---

    private val routePoi =
        RouteFixtures.point(400.0, 0.0).let {
            Symbol.POI("route", it.lat, it.lng, distancesAlongRoute = listOf(400.0))
        }

    // 900 m due south onto the route, at the rider's distance along it, flat then 8% up.
    private val rejoinLine =
        RouteFixtures.encoded((9 downTo 0).map { RouteFixtures.point(0.0, it * 100.0) })

    private val rejoin: RejoinRoad =
        decodeGpsPolyline(rejoinLine).let { points ->
            val cumDist = cumulativeDistancesM(points)
            val lengthM = cumDist.last()
            RejoinRoad(
                rejoinLine,
                MatchedRoadLine(points, cumDist),
                listOf(0f to 64f, (lengthM / 2).toFloat() to 64f, lengthM.toFloat() to 100f),
            )
        }

    private fun navigating(pois: List<Symbol.POI>, rejoinPolyline: String? = null) =
        climbRoute()
            .navigating(rejoinPolyline, rejoinDistance = rejoinPolyline?.let { 900.0 })
            .copy(pois = pois)

    private fun goOffRoute(pois: List<Symbol.POI>, road: RejoinRoad?) {
        rejoinRoad.value = road
        karoo.system.setNavigation(navigating(pois, rejoinLine))
        karoo.system.setDistanceRemaining(4_000.0, onRoute = false)
    }

    private fun comeBack(pois: List<Symbol.POI>) {
        rejoinRoad.value = null
        karoo.system.setNavigation(navigating(pois))
        karoo.system.setDistanceRemaining(4_000.0)
    }

    @Test
    fun the_rejoin_view_draws_no_markers_and_the_route_keeps_them() {
        val (view, bare) = openBareStrip()
        // The rejoin view with no POIs anywhere.
        val unmarkedRejoin =
            view.stripAfter({ goOffRoute(emptyList(), rejoin) }) { !it.sameAs(bare) }
        view.stripAfter({ comeBack(emptyList()) }) { it.sameAs(bare) }

        // Both markers on the route: each one changes the strip.
        val routeMarked =
            view.stripAfter({ karoo.system.setNavigation(navigating(listOf(routePoi))) }) {
                !it.sameAs(bare)
            }
        val marked =
            view.stripAfter({ karoo.system.setGlobalPois(nearPoi) }) { !it.sameAs(routeMarked) }
        val projections = globalPoiProjections.get()

        // Off the route with heights, neither marker is drawn.
        view.stripAfter({ goOffRoute(listOf(routePoi), rejoin) }) { it.sameAs(unmarkedRejoin) }

        // Back on the route, the same frame, from the projection already made.
        view.stripAfter({ comeBack(listOf(routePoi)) }) { it.sameAs(marked) }
        assertEquals(projections, globalPoiProjections.get())
    }

    @Test
    fun off_the_route_without_heights_the_frozen_view_keeps_its_markers() {
        val (view, bare) = openBareStrip()
        val frozenBare = view.stripAfter({ goOffRoute(emptyList(), null) }) { !it.sameAs(bare) }

        view.stripAfter({ karoo.system.setGlobalPois(nearPoi) }) { !it.sameAs(frozenBare) }
    }
}
