package com.jpweytjens.barberfish.karoo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.datastore.preferences.core.edit
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.dataStore
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.MapRecorder
import fi.nikosavola.karooext.testing.decodePolyline
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.runBlocking

private const val CASING_SUFFIX = "-casing"

/**
 * A cell wide enough for a numeric field, matching the one the existing field tests render into.
 */
internal val FIELD_CONFIG = ViewConfig(gridSize = 60 to 15, viewSize = 480 to 200, textSize = 30)

/** The stream-state text a field shows when it has no value, or null once it has one. */
internal fun View.fieldStateText(): String? =
    findViewById<TextView>(R.id.stream_state_tv)
        ?.takeIf { it.visibility == View.VISIBLE }
        ?.text
        ?.toString()

/** The value bitmap a field has drawn, for pixel comparison between readings. */
internal fun View.fieldValue(): Bitmap =
    (findViewById<ImageView>(R.id.field_value).drawable as BitmapDrawable).bitmap

internal fun View.fieldValueVisible(): Boolean =
    findViewById<ImageView>(R.id.field_value).visibility == View.VISIBLE

internal fun View.sparklineValue(): Bitmap =
    (findViewById<ImageView>(R.id.sparkline_image).drawable as BitmapDrawable).bitmap

internal fun View.headerVisible(): Boolean =
    findViewById<View>(R.id.field_header).visibility == View.VISIBLE

/** Starts a view and inflates its first frame, the way the ride app draws one placed field. */
internal fun FakeKarooHost.renderField(
    context: Context,
    typeId: String,
    config: ViewConfig = FIELD_CONFIG,
): View = startView(typeId, config).awaitFrame(15_000).inflate(context, config)

/**
 * Starts the extension and waits for its profile discovery, the one consumer it holds for its whole
 * life. Discovery registers after connecting, so a consumer count taken before it lands would never
 * come back down.
 */
internal fun FakeKarooRule.startBarberfish(): FakeKarooHost {
    val host = host<BarberfishExtension>()
    awaitValue { true.takeIf { system.consumerParams.any { it is ActiveRideProfile.Params } } }
    return host
}

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
            }
        }
    return polylines to symbols
}
