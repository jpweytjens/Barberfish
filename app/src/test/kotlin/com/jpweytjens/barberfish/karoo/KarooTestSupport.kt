package com.jpweytjens.barberfish.karoo

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.extension.dataStore
import fi.nikosavola.karooext.testing.MapRecorder
import fi.nikosavola.karooext.testing.decodePolyline
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.runBlocking

private const val CASING_SUFFIX = "-casing"

/** The extension keeps one process-wide DataStore, which Robolectric reuses across tests. */
internal fun clearBarberfishSettings(context: Context) {
    runBlocking { context.dataStore.edit { it.clear() } }
}

/** Routes run east along the equator, so longitude maps straight to metres from the start. */
internal fun metresEast(lng: Double): Double = lng * RouteFixtures.M_PER_DEG

internal fun lngAt(metres: Double): Double = metres / RouteFixtures.M_PER_DEG

/** A 4 km route: flat, a steady 8% climb from 1 to 2 km, then flat again. */
internal fun climbRoute(): TestRoute {
    val elevation =
        RouteFixtures.encodeElevation(
            listOf(0f to 100f, 1_000f to 100f, 2_000f to 180f, 3_000f to 180f, 4_000f to 180f)
        )
    return TestRoute(RouteFixtures.straight(lengthM = 4_000.0, stepM = 100.0), elevation, 4_000.0)
}

/** The same road, flat all the way: one colour run where the climb route has five. */
internal fun flatRoute(): TestRoute =
    TestRoute(
        RouteFixtures.straight(lengthM = 4_000.0, stepM = 100.0),
        RouteFixtures.flatElevation(4_000.0),
        4_000.0,
    )

internal class TestRoute(
    private val points: List<LatLng>,
    private val elevationPolyline: String,
    private val lengthM: Double,
) {
    /** The sticky state the ride app sends while following this route. */
    fun navigating(rejoinPolyline: String? = null, rejoinDistance: Double? = null) =
        OnNavigationState.NavigationState.NavigatingRoute(
            routePolyline = RouteFixtures.encoded(points),
            routeDistance = lengthM,
            routeElevationPolyline = elevationPolyline,
            rejoinPolyline = rejoinPolyline,
            rejoinDistance = rejoinDistance,
            name = "Test route",
            reversed = false,
            breadcrumb = false,
            pois = emptyList(),
            climbs = emptyList(),
        )
}

/** Metres east of the start where this polyline begins and ends. */
internal fun ShowPolyline.spanM(): ClosedFloatingPointRange<Double> {
    val points = decodePolyline(encodedPolyline)
    return metresEast(points.first().second)..metresEast(points.last().second)
}

internal fun MapRecorder.visibleFills(): Map<String, ShowPolyline> =
    visiblePolylines().filterKeys { !it.endsWith(CASING_SUFFIX) }

internal fun MapRecorder.visibleChevrons(): List<Symbol.Icon> =
    visibleSymbols().values.filterIsInstance<Symbol.Icon>()

/**
 * Waits for the first draw of a route. A restack sends the casings, settles for 500 ms, then sends
 * the fills, so the wait is on a fill and a quiet period longer than one settle.
 */
internal fun MapRecorder.awaitDrawn(karoo: FakeKarooRule) {
    karoo.awaitValue { visibleFills().takeIf { it.isNotEmpty() } }
    awaitQuiet(quietMs = 800, timeoutMs = 15_000)
}

/**
 * Layer ids still on the map after the sessions ran in order. The ride app keeps drawn layers
 * across map sessions, but each recorder only knows its own, so replay them together.
 */
internal fun paintedAfter(vararg sessions: MapRecorder): Pair<Set<String>, Set<String>> {
    val polylines = linkedSetOf<String>()
    val symbols = linkedSetOf<String>()
    sessions
        .flatMap { it.items }
        .forEach { effect ->
            when (effect) {
                is ShowPolyline -> polylines += effect.id
                is HidePolyline -> polylines -= effect.id
                is ShowSymbols -> symbols += effect.symbols.map { it.id }
                is HideSymbols -> symbols -= effect.symbolIds.toSet()
                else -> Unit
            }
        }
    return polylines to symbols
}
