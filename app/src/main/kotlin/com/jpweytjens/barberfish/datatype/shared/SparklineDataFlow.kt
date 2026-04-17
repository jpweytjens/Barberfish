package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamRideState
import com.jpweytjens.barberfish.extension.streamSparklineConfig
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
    val enabled: Boolean,
)

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
            flow { while (true) { emit(StreamState.NotAvailable); delay(1000L) } }
        else
            karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION).sample(1000L)

        combine(
            karooSystem.streamNavigationState().sample(1000L),
            distFlow,
            context.streamZoneConfig(),
            context.streamSparklineConfig(),
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
                else -> ICON_TINT_TEAL.toArgb()
            }
            val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
            lastPositionM = positionM
            val (bitmap, updatedRange) = if (sparkCfg.enabled)
                renderElevationSparkline(
                    elevationPoints = elevPoints,
                    positionM = sparklinePositionM,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    density = context.resources.displayMetrics.density,
                    palette = zoneConfig.gradePalette,
                    readable = zoneConfig.readableColors,
                    lookaheadM = sparkCfg.lookaheadKm * 1000f,
                    skipBands = sparkCfg.skipBands,
                    displayedRange = ratchetRange,
                    distanceDeltaM = distanceDeltaM,
                    dotColor = dotColor,
                    isNightMode = isNightMode,
                    minElevRangeM = sparkCfg.yZoom.minRangeM,
                    logWarpK = sparkCfg.warp.k,
                    positionFraction = sparkCfg.warp.positionFraction,
                )
            else ElevationSparklineResult(null, ratchetRange)
            ratchetRange = updatedRange

            SparklineFrame(
                bitmap = bitmap,
                displayedRange = ratchetRange,
                lookaheadKm = sparkCfg.lookaheadKm,
                enabled = sparkCfg.enabled,
            )
        }
    }
}
