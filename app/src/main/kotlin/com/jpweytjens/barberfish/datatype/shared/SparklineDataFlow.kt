package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamGlobalPOIs
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnGlobalPOIs
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample

// Max cross-track distance (metres) for a saved/global POI to count as "on this route". Matches
// karoo-routegraph's poiDistanceToRouteMaxMeters default (issue #22).
private const val POI_ROUTE_CORRIDOR_M = 500.0

internal data class SparklineFrame(
    val bitmap: Bitmap?,
    val displayedRange: Float,
    val lookaheadKm: Int,
    val hudEnabled: Boolean,
    // "Climb n/total" heads-up text shown in the strip before a climb's profile reveals.
    val counterText: String? = null,
)

/**
 * Shared sparkline data pipeline used by both HUD and standalone sparkline field.
 *
 * Streams navigation state, distance-to-destination, sparkline config, and zone config. Decodes +
 * caches the elevation polyline, tracks position and ratchet range, and calls
 * [renderElevationSparkline] to produce a [SparklineFrame] per emission.
 */
@OptIn(FlowPreview::class)
internal fun sparklineBitmapFlow(
    karooSystem: KarooSystemService,
    context: Context,
    configFlow: Flow<SparklineConfig>,
    widthPx: Int,
    heightPx: Int,
    isPreview: Boolean,
): Flow<SparklineFrame> {
    var ratchetRange = 0f
    var lastPositionM = 0f
    var lastOnRoutePositionM = 0f
    var cachedElevKey: Triple<String, ElevationSimplification, Int>? = null
    var cachedElevPoints: List<Pair<Float, Float>> = emptyList()
    // Projection of global POIs onto the route is keyed on (routePolyline, reversed, global POI
    // ids) so it only recomputes when the route, its direction, or the saved-POI set changes —
    // snapping over a dense polyline every emission would be wasteful.
    var cachedGlobalPoiKey: Triple<String, Boolean, List<String>>? = null
    var cachedGlobalPoiDistances: List<Float> = emptyList()

    val distFlow: Flow<StreamState> =
        if (isPreview)
            flow {
                while (true) {
                    emit(StreamState.NotAvailable)
                    delay(HUD_UPDATE_INTERVAL_MS)
                }
            }
        else
            karooSystem
                .streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)
                .sample(HUD_UPDATE_INTERVAL_MS)

    // Global (saved) POIs arrive on their own event, separate from route.pois. Guard both
    // failure modes so this extra source can't break the combined sparkline: onStart seeds an
    // empty value so combine never stalls waiting for a first emission (there may be no global
    // POIs), and catch degrades an error to empty rather than cancelling the whole flow.
    val globalPoisFlow: Flow<OnGlobalPOIs> =
        karooSystem
            .streamGlobalPOIs()
            .onStart { emit(OnGlobalPOIs(emptyList())) }
            .catch { e ->
                android.util.Log.e("Barberfish", "OnGlobalPOIs stream threw", e)
                emit(OnGlobalPOIs(emptyList()))
            }

    return combine(
        karooSystem.streamNavigationState().sample(HUD_UPDATE_INTERVAL_MS),
        distFlow,
        context.streamZoneConfig(),
        configFlow,
        globalPoisFlow,
    ) { navState, distState, zoneConfig, sparkCfg, globalPois ->
        val isNightMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val route = navState.state as? OnNavigationState.NavigationState.NavigatingRoute
        val dest = navState.state as? OnNavigationState.NavigationState.NavigatingToDestination
        // Climbs-mode preview uses a real climb (Col de Rates); other previews keep the
        // mixed RvV terrain so the On-mode preview stays realistic.
        val previewClimbs = isPreview && sparkCfg.hudMode == SparklineMode.CLIMBS
        val (elevEncoded, elevSource) =
            when {
                route != null -> (route.routeElevationPolyline ?: "") to 0
                dest != null -> (dest.elevationPolyline ?: "") to 1
                previewClimbs -> "" to 4
                isPreview -> "" to 2
                else -> "" to 3
            }
        val elevKey = Triple(elevEncoded, sparkCfg.simplification, elevSource)
        if (elevKey != cachedElevKey) {
            val raw =
                when {
                    elevSource == 4 -> colDeRatesElevationFixture()
                    elevSource == 2 -> previewElevationFixture()
                    elevEncoded.isBlank() -> emptyList()
                    else -> decodeElevationPolyline(elevEncoded)
                }
            cachedElevPoints = visvalingamWhyatt(raw, sparkCfg.simplification.minAreaM2)
            cachedElevKey = elevKey
        }
        val elevPoints = cachedElevPoints
        val routeLengthM =
            route?.routeDistance?.toFloat() ?: elevPoints.lastOrNull()?.first ?: 20_000f
        val streamingDist = distState as? StreamState.Streaming
        val distanceToDestinationM =
            streamingDist?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION)?.toFloat()
        val onRoute =
            streamingDist?.dataPoint?.values?.get(DataType.Field.ON_ROUTE)?.let { it >= 0.5 }
                ?: true
        val positionM =
            when {
                isPreview ->
                    (System.currentTimeMillis() % 180_000L).toFloat() / 180_000f * routeLengthM
                distanceToDestinationM != null && (route != null || dest != null) ->
                    (routeLengthM - distanceToDestinationM).coerceIn(0f, routeLengthM)
                else -> 0f
            }
        val isOffRoute = route != null && !onRoute
        if (!isOffRoute) lastOnRoutePositionM = positionM
        val sparklinePositionM = if (isOffRoute) lastOnRoutePositionM else positionM
        val dotColor =
            when {
                isOffRoute -> KAROO_REJOIN_RED.toArgb()
                dest != null -> KAROO_DESTINATION_PURPLE.toArgb()
                else -> BarberfishYellow.toArgb()
            }
        val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
        lastPositionM = positionM
        val rawClimbRanges: List<Pair<Float, Float>> =
            when {
                route != null ->
                    route.climbs.map { climb ->
                        climb.startDistance.toFloat() to
                            (climb.startDistance + climb.length).toFloat()
                    }
                previewClimbs -> colDeRatesClimbsFixture()
                isPreview -> rvvClimbsFixture()
                else -> emptyList()
            }
        val climbRanges = rawClimbRanges.mapNotNull { (startM, endM) ->
            val startElev = elevationAt(elevPoints, startM) ?: return@mapNotNull null
            val endElev = elevationAt(elevPoints, endM) ?: return@mapNotNull null
            if (endElev > startElev) startM to endM else null
        }
        val reveal =
            resolveClimbReveal(sparkCfg.hudMode, climbRanges, elevPoints, sparklinePositionM)
        val windowOverride = reveal.windowOverride
        val showArea = reveal.visible
        // Project saved (global) POIs onto the route so they show alongside route-embedded ones.
        // Globals arrive with an empty distancesAlongRoute (lat/lng only), so snap each to the
        // nearest point on the route geometry and keep those within POI_ROUTE_CORRIDOR_M. Cached
        // on (routePolyline, reversed, global POI ids). A global that already carries a projected
        // distance (rare) is used as-is.
        if (route != null) {
            val globalKey =
                Triple(route.routePolyline, route.reversed, globalPois.pois.map { it.id })
            if (globalKey != cachedGlobalPoiKey) {
                val decodedPts = decodeGpsPolyline(route.routePolyline)
                val routePts = if (route.reversed) decodedPts.asReversed() else decodedPts
                val routeCum = cumulativeDistancesM(routePts)
                cachedGlobalPoiDistances =
                    globalPois.pois.flatMap { poi ->
                        if (poi.distancesAlongRoute.isNotEmpty())
                            poi.distancesAlongRoute.map { it.toFloat() }
                        else
                            projectPoiAlongRoute(
                                    LatLng(poi.lat, poi.lng),
                                    routePts,
                                    routeCum,
                                    POI_ROUTE_CORRIDOR_M,
                                )
                                ?.let { listOf(it.toFloat()) } ?: emptyList()
                    }
                cachedGlobalPoiKey = globalKey
            }
        } else {
            cachedGlobalPoiDistances = emptyList()
            cachedGlobalPoiKey = null
        }
        val poiDistances: List<Float> =
            when {
                route != null ->
                    route.pois.flatMap { it.distancesAlongRoute }.map { it.toFloat() } +
                        cachedGlobalPoiDistances
                previewClimbs -> colDeRatesPoisFixture()
                isPreview -> rvvPoisFixture()
                else -> emptyList()
            }
        val (climbEdge, descentEdge) = sparkCfg.gradeEdges(zoneConfig.gradePalette)
        val (bitmap, updatedRange) =
            if (showArea && reveal.counterText == null) {
                renderElevationSparkline(
                    elevationPoints = elevPoints,
                    positionM = sparklinePositionM,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    density = context.resources.displayMetrics.density,
                    palette = zoneConfig.gradePalette,
                    // Sparkline always renders as a fill; use brand colors.
                    readable = false,
                    lookaheadM = sparkCfg.lookaheadKm * 1000f,
                    climbEdge = climbEdge,
                    descentEdge = descentEdge,
                    displayedRange = ratchetRange,
                    distanceDeltaM = distanceDeltaM,
                    dotColor = dotColor,
                    isNightMode = isNightMode,
                    minElevRangeM = sparkCfg.yZoom.minRangeM,
                    logWarpK = sparkCfg.warp.k,
                    positionFraction = sparkCfg.warp.positionFraction,
                    climbRanges = climbRanges,
                    showClimbs = sparkCfg.showClimbs,
                    poiDistances = poiDistances,
                    showPois = sparkCfg.showPois,
                    windowOverride = windowOverride,
                )
            } else {
                ElevationSparklineResult(null, ratchetRange)
            }
        ratchetRange = updatedRange

        SparklineFrame(
            bitmap = bitmap,
            displayedRange = ratchetRange,
            lookaheadKm = sparkCfg.lookaheadKm,
            hudEnabled = showArea,
            counterText = reveal.counterText,
        )
    }
}
