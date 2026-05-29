package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamRideState
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.sample

internal data class SparklineFrame(
    val bitmap: Bitmap?,
    val displayedRange: Float,
    val lookaheadKm: Int,
    val hudEnabled: Boolean,
)

// An uphill climb on the route plus the approach distance (difficulty-scaled) at which the
// climb-only sparkline reveals before its foot.
private data class ClimbSpan(val startM: Float, val endM: Float, val approachM: Float)

/**
 * Shared sparkline data pipeline used by both HUD and standalone sparkline field.
 *
 * Streams navigation state, distance-to-destination, sparkline config, and zone config.
 * Decodes + caches the elevation polyline, tracks position and ratchet range, and calls
 * [renderElevationSparkline] to produce a [SparklineFrame] per emission.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    val rideStateFlow: Flow<RideState> = if (isPreview)
        flowOf(RideState.Idle)
    else
        karooSystem.streamRideState()

    return rideStateFlow.flatMapLatest { rideState ->
        val debugSweep = (BuildConfig.DEBUG && rideState !is RideState.Recording) || isPreview
        val distFlow: Flow<StreamState> = if (debugSweep)
            flow { while (true) { emit(StreamState.NotAvailable); delay(HUD_UPDATE_INTERVAL_MS) } }
        else
            karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION).sample(HUD_UPDATE_INTERVAL_MS)

        combine(
            karooSystem.streamNavigationState().sample(HUD_UPDATE_INTERVAL_MS),
            distFlow,
            context.streamZoneConfig(),
            configFlow,
        ) { navState, distState, zoneConfig, sparkCfg ->
            val isNightMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            val route = navState.state as? OnNavigationState.NavigationState.NavigatingRoute
            val dest = navState.state as? OnNavigationState.NavigationState.NavigatingToDestination
            val (elevEncoded, elevSource) = when {
                route != null -> (route.routeElevationPolyline ?: "") to 0
                dest != null -> (dest.elevationPolyline ?: "") to 1
                debugSweep -> "" to 2
                else -> "" to 3
            }
            val elevKey = Triple(elevEncoded, sparkCfg.simplification, elevSource)
            if (elevKey != cachedElevKey) {
                val raw = when {
                    elevSource == 2 -> previewElevationFixture()
                    elevEncoded.isBlank() -> emptyList()
                    else -> decodeElevationPolyline(elevEncoded)
                }
                cachedElevPoints = visvalingamWhyatt(raw, sparkCfg.simplification.minAreaM2)
                cachedElevKey = elevKey
            }
            val elevPoints = cachedElevPoints
            val routeLengthM = route?.routeDistance?.toFloat()
                ?: elevPoints.lastOrNull()?.first ?: 20_000f
            val streamingDist = distState as? StreamState.Streaming
            val distanceToDestinationM = streamingDist
                ?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION)
                ?.toFloat()
            val onRoute = streamingDist
                ?.dataPoint?.values?.get(DataType.Field.ON_ROUTE)
                ?.let { it >= 0.5 } ?: true
            val positionM = when {
                debugSweep ->
                    (System.currentTimeMillis() % 180_000L).toFloat() / 180_000f * routeLengthM
                distanceToDestinationM != null && (route != null || dest != null) ->
                    (routeLengthM - distanceToDestinationM).coerceIn(0f, routeLengthM)
                else -> 0f
            }
            val isOffRoute = route != null && !onRoute
            if (!isOffRoute) lastOnRoutePositionM = positionM
            val sparklinePositionM = if (isOffRoute) lastOnRoutePositionM else positionM
            val dotColor = when {
                isOffRoute -> KAROO_REJOIN_RED.toArgb()
                dest != null -> KAROO_DESTINATION_PURPLE.toArgb()
                else -> BarberfishYellow.toArgb()
            }
            val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
            lastPositionM = positionM
            val rawClimbRanges: List<Pair<Float, Float>> = when {
                route != null -> route.climbs.map { climb ->
                    climb.startDistance.toFloat() to (climb.startDistance + climb.length).toFloat()
                }
                debugSweep -> rvvClimbsFixture()
                else -> emptyList()
            }
            val climbSpans = rawClimbRanges.mapNotNull { (startM, endM) ->
                val startElev = elevationAt(elevPoints, startM) ?: return@mapNotNull null
                val endElev = elevationAt(elevPoints, endM) ?: return@mapNotNull null
                if (endElev <= startElev) return@mapNotNull null
                val lengthM = (endM - startM).toDouble()
                val gradePct = if (lengthM > 0) (endElev - startElev) / lengthM * 100.0 else 0.0
                val approachM = climbApproachM(pcsClimbScore(gradePct, lengthM))
                ClimbSpan(startM, endM, approachM)
            }
            val climbRanges = climbSpans.map { it.startM to it.endM }
            // Climb-only mode: reveal once within the (difficulty-scaled) approach of a climb foot
            // and keep it up through the descent of the climb. Pick the climb finished first when
            // approaches overlap. Pin the sparkline window to that climb (foot → top).
            val activeClimb = if (sparkCfg.hudMode == SparklineMode.CLIMBS) {
                climbSpans
                    .filter { sparklinePositionM in (it.startM - it.approachM)..it.endM }
                    .minByOrNull { it.endM }
            } else {
                null
            }
            val windowOverride = activeClimb?.let { it.startM to it.endM }
            val showArea = when (sparkCfg.hudMode) {
                SparklineMode.OFF -> false
                SparklineMode.ON -> true
                SparklineMode.CLIMBS -> activeClimb != null
            }
            val poiDistances: List<Float> = when {
                route != null -> route.pois.flatMap { it.distancesAlongRoute }.map { it.toFloat() }
                debugSweep -> rvvPoisFixture()
                else -> emptyList()
            }
            val (bitmap, updatedRange) = if (showArea) {
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
                    skipBands = sparkCfg.skipBands,
                    skipBandsDescent = sparkCfg.skipBandsDescent,
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
            )
        }
    }
}
