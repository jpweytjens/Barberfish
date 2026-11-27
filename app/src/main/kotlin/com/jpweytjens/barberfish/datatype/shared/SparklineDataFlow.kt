package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.RejoinRoad
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.rejoinRoad
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamGlobalPOIs
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnGlobalPOIs
import io.hammerhead.karooext.models.StreamState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

// Max cross-track distance (metres) for a saved/global POI to count as "on this route". Matches
// karoo-routegraph's poiDistanceToRouteMaxMeters default (issue #22).
private const val POI_ROUTE_CORRIDOR_M = 500.0

// How many times the saved-POI projection has run, process-wide. Tests read it to see the cache
// hold across a detour.
internal val globalPoiProjections = AtomicInteger()

internal data class SparklineFrame(
    val bitmap: Bitmap?,
    val displayedRange: Float,
    val lookaheadKm: Int,
    val hudEnabled: Boolean,
    // "Climb n/total" heads-up text shown in the strip before a climb's profile reveals.
    val counterText: String? = null,
    // No route is being followed. Distinct from a null bitmap, which climbs mode also yields
    // between climbs.
    val noRoute: Boolean = false,
) {
    companion object {
        /** The HUD strip switched off: nothing drawn and no area reserved. */
        val OFF =
            SparklineFrame(bitmap = null, displayedRange = 0f, lookaheadKm = 0, hudEnabled = false)
    }
}

/**
 * Shared sparkline data pipeline used by both HUD and standalone sparkline field.
 *
 * Streams navigation state, distance-to-destination, sparkline config, and zone config. Decodes +
 * caches the elevation polyline, tracks position and ratchet range, and calls
 * [renderElevationSparkline] to produce a [SparklineFrame] per emission.
 */
internal fun sparklineBitmapFlow(
    karooSystem: KarooSystemService,
    context: Context,
    configFlow: Flow<SparklineConfig>,
    widthPx: Int,
    heightPx: Int,
    isPreview: Boolean,
    rejoin: Flow<RejoinRoad?> = rejoinRoad,
): Flow<SparklineFrame> {
    var ratchetRange = 0f
    var lastPositionM = 0f
    var lastOnRoutePositionM = 0f
    var lastOnRouteClimbRanges: List<Pair<Float, Float>> = emptyList()
    var cachedElevKey: Triple<String, ElevationSimplification, Int>? = null
    var cachedElevPoints: List<Pair<Float, Float>> = emptyList()
    // Projection of global POIs onto the route is keyed on (routePolyline, reversed, global POI
    // ids) so it only recomputes when the route, its direction, or the saved-POI set changes —
    // snapping over a dense polyline every emission would be wasteful.
    var cachedGlobalPoiKey: Triple<String, Boolean, List<String>>? = null
    var cachedGlobalPoiDistances: List<Float> = emptyList()
    // The rejoin view's own simplified profile and height range, beside the route's.
    var cachedRejoinKey: Pair<String, ElevationSimplification>? = null
    var cachedRejoinPoints: List<Pair<Float, Float>> = emptyList()
    var rejoinRange = 0f

    val distFlow: Flow<StreamState> =
        if (isPreview)
            flow {
                while (true) {
                    emit(StreamState.NotAvailable)
                    delay(HUD_UPDATE_INTERVAL_MS)
                }
            }
        else karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)

    // Global (saved) POIs arrive on their own event, separate from route.pois. Guard both
    // failure modes so this extra source can't break the combined sparkline: onStart seeds an
    // empty value so combine never stalls waiting for a first emission (there may be no global
    // POIs), and catch degrades an error to empty rather than cancelling the whole flow.
    val globalPoisFlow: Flow<OnGlobalPOIs> =
        karooSystem
            .streamGlobalPOIs()
            .onStart { emit(OnGlobalPOIs(emptyList())) }
            .catch { e ->
                Timber.e(e, "OnGlobalPOIs stream threw")
                emit(OnGlobalPOIs(emptyList()))
            }

    return sparklineInputs(
            // Converted before the combine, so distance ticks reuse the route instead of decoding
            // it again.
            karooSystem
                .streamNavigationState()
                .map { it.state.followedRoute() }
                .distinctUntilChanged(),
            distFlow,
            context.streamZoneConfig(),
            configFlow,
            globalPoisFlow,
            rejoin,
        )
        .map { inputs ->
            val followed = inputs.followed
            val distState = inputs.distState
            val zoneConfig = inputs.zoneConfig
            val sparkCfg = inputs.sparkCfg
            val globalPois = inputs.globalPois
            val isNightMode =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            // Climbs, POIs and the off-route freeze stay with a loaded route; nav-to-pin draws its
            // profile and position only.
            val route = followed?.takeIf { it.destination == null }
            // Climbs-mode preview uses a real climb (Col de Rates); other previews keep the
            // mixed RvV terrain so the On-mode preview stays realistic.
            val previewClimbs = isPreview && sparkCfg.hudMode == SparklineMode.CLIMBS
            val (elevEncoded, elevSource) =
                when {
                    route != null -> route.elevationPolyline.orEmpty() to 0
                    followed != null -> followed.elevationPolyline.orEmpty() to 1
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
                route?.distanceM?.toFloat() ?: elevPoints.lastOrNull()?.first ?: 20_000f
            val streamingDist = distState as? StreamState.Streaming
            val distanceToDestinationM =
                streamingDist
                    ?.dataPoint
                    ?.values
                    ?.get(DataType.Field.DISTANCE_TO_DESTINATION)
                    ?.toFloat()
            val onRoute =
                streamingDist?.dataPoint?.values?.get(DataType.Field.ON_ROUTE)?.let { it >= 0.5 }
                    ?: true
            // Stricter than onRoute above: an absent or not-yet-streaming ON_ROUTE counts as
            // off-route, so a doubtful emission can neither commit climbs whose start distances may
            // track the rider instead of the route start, nor overwrite the held on-route position.
            val onRouteConfirmed =
                streamingDist?.dataPoint?.values?.get(DataType.Field.ON_ROUTE)?.let { it >= 0.5 }
                    ?: false
            val positionM =
                when {
                    isPreview ->
                        (System.currentTimeMillis() % 180_000L).toFloat() / 180_000f * routeLengthM
                    distanceToDestinationM != null && followed != null ->
                        (routeLengthM - distanceToDestinationM).coerceIn(0f, routeLengthM)
                    else -> 0f
                }
            val isOffRoute = route != null && !onRoute
            if (route != null && distanceToDestinationM != null && onRouteConfirmed) {
                lastOnRoutePositionM = positionM
            }
            val sparklinePositionM = if (isOffRoute) lastOnRoutePositionM else positionM
            val dotColor =
                when {
                    isOffRoute -> KAROO_REJOIN_RED.toArgb()
                    followed?.destination != null -> KAROO_DESTINATION_PURPLE.toArgb()
                    else -> BarberfishYellow.toArgb()
                }
            val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
            lastPositionM = positionM
            // Navigation cleared (or switched to destination mode): drop the inventory. Live
            // flows only — a preview rendering with no route must not clear the cache the live
            // fields are riding on.
            if (route == null && !isPreview) {
                sharedClimbCache.resolve(null, null, false)
            }
            val rawClimbRanges: List<Pair<Float, Float>> =
                when {
                    route != null ->
                        sharedClimbCache
                            .resolve(
                                gradeMapRouteKey(route.polyline, route.reversed),
                                route.climbs,
                                onRouteConfirmed,
                            )
                            .map { climb ->
                                climb.startDistance.toFloat() to
                                    (climb.startDistance + climb.length).toFloat()
                            }
                    previewClimbs -> colDeRatesClimbsFixture()
                    isPreview -> rvvClimbsFixture()
                    else -> emptyList()
                }
            val currentClimbRanges = rawClimbRanges.mapNotNull { (startM, endM) ->
                val startElev = elevationAt(elevPoints, startM) ?: return@mapNotNull null
                val endElev = elevationAt(elevPoints, endM) ?: return@mapNotNull null
                if (endElev > startElev) startM to endM else null
            }
            // Climb start distances reported while rerouting are not verified to sit on the
            // route-start axis the profile uses, so off-route the last on-route ranges are
            // kept — the same posture as the frozen rider position above.
            if (!isOffRoute) lastOnRouteClimbRanges = currentClimbRanges
            val climbRanges = if (isOffRoute) lastOnRouteClimbRanges else currentClimbRanges
            val reveal =
                resolveClimbReveal(sparkCfg.hudMode, climbRanges, elevPoints, sparklinePositionM)
            val windowOverride = reveal.windowOverride
            val showArea = reveal.visible
            // Project saved (global) POIs onto the route so they show alongside route-embedded
            // ones.
            // Globals arrive with an empty distancesAlongRoute (lat/lng only), so snap each to the
            // nearest point on the route geometry and keep those within POI_ROUTE_CORRIDOR_M.
            // Cached
            // on (routePolyline, reversed, global POI ids). A global that already carries a
            // projected
            // distance (rare) is used as-is.
            if (route != null) {
                val globalKey =
                    Triple(route.polyline, route.reversed, globalPois.pois.map { it.id })
                if (globalKey != cachedGlobalPoiKey) {
                    globalPoiProjections.incrementAndGet()
                    val decodedPts = decodeGpsPolyline(route.polyline)
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
                                    ?.let { listOf(it.toFloat()) }
                                    .orEmpty()
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
            // Off the route, the rejoin line's terrain once its heights are in. No climbs and no
            // POIs: theirs are distances along the route. It keeps its own profile and height
            // range, so it reads and writes none of the route's cached state above, and the route
            // returns as it was. Climbs mode keeps today's view off the route. Any failure falls
            // through to that view.
            val rejoin =
                inputs.rejoin?.takeIf {
                    isOffRoute &&
                        sparkCfg.hudMode == SparklineMode.ON &&
                        it.rejoinPolyline == route.rejoinPolyline
                }
            @Suppress("TooGenericExceptionCaught")
            val rejoinResult =
                try {
                    rejoin?.let { road ->
                        val rejoinKey = road.rejoinPolyline to sparkCfg.simplification
                        if (rejoinKey != cachedRejoinKey) {
                            // Each rejoin line starts its height range fresh.
                            if (road.rejoinPolyline != cachedRejoinKey?.first) rejoinRange = 0f
                            cachedRejoinPoints =
                                visvalingamWhyatt(road.heights, sparkCfg.simplification.minAreaM2)
                            cachedRejoinKey = rejoinKey
                        }
                        val lookaheadM = sparkCfg.lookaheadKm * 1000f
                        val view =
                            rejoinView(
                                road.lengthM.toFloat(),
                                route?.rejoinDistanceM,
                                lookaheadM,
                                widthPx,
                            )
                        renderElevationSparkline(
                            elevationPoints = cachedRejoinPoints,
                            positionM = view.positionM,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            density = context.resources.displayMetrics.density,
                            palette = zoneConfig.gradePalette,
                            readable = false,
                            lookaheadM = lookaheadM,
                            climbEdge = climbEdge,
                            descentEdge = descentEdge,
                            displayedRange = rejoinRange,
                            distanceDeltaM = distanceDeltaM,
                            dotColor = dotColor,
                            isNightMode = isNightMode,
                            minElevRangeM = sparkCfg.yZoom.minRangeM,
                            logWarpK = sparkCfg.warp.k,
                            positionFraction = sparkCfg.warp.positionFraction,
                            climbRanges = emptyList(),
                            showClimbs = sparkCfg.showClimbs,
                            poiDistances = emptyList(),
                            showPois = sparkCfg.showPois,
                            windowOverride = view.windowOverride,
                            clampWindowOverride = false,
                        )
                    }
                } catch (e: RuntimeException) {
                    Timber.w(e, "could not draw the rejoin line")
                    null
                }
            if (rejoinResult?.bitmap != null) {
                rejoinRange = rejoinResult.displayedRange
                return@map SparklineFrame(
                    bitmap = rejoinResult.bitmap,
                    displayedRange = rejoinRange,
                    lookaheadKm = sparkCfg.lookaheadKm,
                    hudEnabled = showArea,
                )
            }
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
                noRoute = followed == null && !isPreview,
            )
        }
}

internal data class SparklineInputs(
    val followed: FollowedRoute?,
    val distState: StreamState,
    val zoneConfig: ZoneConfig,
    val sparkCfg: SparklineConfig,
    val globalPois: OnGlobalPOIs,
    val rejoin: RejoinRoad?,
)

/**
 * The sparkline's inputs on one render tick. Sampling them together, not each on its own clock,
 * makes a navigation event and a distance update draw the profile once instead of twice.
 */
internal fun sparklineInputs(
    followed: Flow<FollowedRoute?>,
    distState: Flow<StreamState>,
    zoneConfig: Flow<ZoneConfig>,
    sparkCfg: Flow<SparklineConfig>,
    globalPois: Flow<OnGlobalPOIs>,
    rejoin: Flow<RejoinRoad?>,
): Flow<SparklineInputs> =
    combine(
            combine(followed, distState, zoneConfig, sparkCfg, globalPois) {
                nav,
                dist,
                zone,
                cfg,
                pois ->
                SparklineInputs(nav, dist, zone, cfg, pois, rejoin = null)
            },
            rejoin,
        ) { inputs, road ->
            inputs.copy(rejoin = road)
        }
        .onRenderTick()

/**
 * Where the Profile puts a rejoin line [lengthM] long: the rider at [positionM] on the line's own
 * axis, and the fitted [windowOverride] for a line shorter than the lookahead, null for one that
 * scrolls like the route.
 */
internal data class RejoinView(val positionM: Float, val windowOverride: Pair<Float, Float>?)

/**
 * The rejoin view of a line [lengthM] long with [rejoinDistanceM] of it still to ride: the rider
 * that far before its end, kept on the line, and at its start while the distance is unknown. A line
 * shorter than [lookaheadM] is fitted to a strip [widthPx] wide (see [rejoinWindow]).
 */
internal fun rejoinView(
    lengthM: Float,
    rejoinDistanceM: Double?,
    lookaheadM: Float,
    widthPx: Int,
): RejoinView =
    RejoinView(
        positionM = rejoinDistanceM?.let { (lengthM - it.toFloat()).coerceIn(0f, lengthM) } ?: 0f,
        windowOverride = if (lengthM < lookaheadM) rejoinWindow(lengthM, widthPx) else null,
    )
