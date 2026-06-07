package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamRouteRemainingConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.sample

private const val OVERVIEW_DOT_RADIUS_PX = 7f
private const val OVERVIEW_STROKE_PX = 3f
private const val OVERVIEW_MARKER_STROKE_PX = 1.5f
private const val OVERVIEW_PAD_PX = 8f
private const val OVERVIEW_MIN_ELEV_RANGE_M = 50f

// Preview-only: sweep the dot at a fixed ~3 px/s. On-screen jump = speed × the host's redraw
// interval, so a slow fixed speed keeps each visible step small (~3 px even if Karoo only redraws
// data fields ~1 Hz). Full-width cells therefore traverse slowly; raise PX_PER_SEC to speed it up
// at the cost of larger jumps. On a real ride the dot uses the live distance-to-destination.
private const val OVERVIEW_PREVIEW_PX_PER_SEC = 3f
private const val OVERVIEW_PREVIEW_TICK_MS = 125L

/**
 * Draw the whole route as a single uncolored polyline with a position dot. No grade coloring, no
 * fill, no climbs/POIs — the minimal "you are here". `points` are (distanceM, elevationM), already
 * decoded and simplified.
 */
fun renderOverviewSparkline(
    points: List<Pair<Float, Float>>,
    positionM: Float,
    widthPx: Int,
    heightPx: Int,
    dotColor: Int,
    isNightMode: Boolean,
): Bitmap? {
    if (points.size < 2 || widthPx <= 0 || heightPx <= 0) return null
    val startM = points.first().first
    val endM = points.last().first
    val spanM = (endM - startM).coerceAtLeast(1f)
    val elevMin = points.minOf { it.second }
    val elevMax = points.maxOf { it.second }
    val elevRange = (elevMax - elevMin).coerceAtLeast(OVERVIEW_MIN_ELEV_RANGE_M)

    fun toX(d: Float) = ((d - startM) / spanM * widthPx).coerceIn(0f, widthPx.toFloat())
    fun toY(e: Float) =
        (heightPx - (e - elevMin) / elevRange * (heightPx - 2 * OVERVIEW_PAD_PX) - OVERVIEW_PAD_PX)
            .coerceIn(0f, heightPx.toFloat())

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = OVERVIEW_STROKE_PX
    paint.color = if (isNightMode) Color.WHITE else Color.rgb(0x5b, 0x61, 0x66)
    val path = Path()
    points.forEachIndexed { i, (d, e) ->
        val x = toX(d)
        val y = toY(e)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)

    val dotX = toX(positionM.coerceIn(startM, endM))
    val dotY = toY(elevationAt(points, positionM) ?: elevMin)
    paint.style = Paint.Style.FILL
    paint.color = dotColor
    canvas.drawCircle(dotX, dotY, OVERVIEW_DOT_RADIUS_PX, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = OVERVIEW_MARKER_STROKE_PX
    paint.color = if (isNightMode) Color.BLACK else Color.WHITE
    canvas.drawCircle(dotX, dotY, OVERVIEW_DOT_RADIUS_PX, paint)
    return bitmap
}

/** Preview of the overview sparkline from the bundled fixture, dot at [positionFraction] (0..1). */
fun overviewPreviewBitmap(
    widthPx: Int,
    heightPx: Int,
    isNightMode: Boolean,
    targetCount: Int,
    positionFraction: Float = 0.45f,
): Bitmap? {
    val points = visvalingamToCount(previewElevationFixture(), targetCount)
    if (points.size < 2) return null
    val len = points.last().first - points.first().first
    val positionM = points.first().first + len * positionFraction.coerceIn(0f, 1f)
    return renderOverviewSparkline(
        points,
        positionM,
        widthPx,
        heightPx,
        BarberfishYellow.toArgb(),
        isNightMode
    )
}

@OptIn(FlowPreview::class)
fun overviewBitmapFlow(
    karooSystem: KarooSystemService,
    context: Context,
    widthPx: Int,
    heightPx: Int,
    isPreview: Boolean,
): Flow<Bitmap?> {
    var cachedKey: String? = null
    var cachedPoints: List<Pair<Float, Float>> = emptyList()

    val distFlow: Flow<StreamState> =
        karooSystem
            .streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)
            .sample(HUD_UPDATE_INTERVAL_MS)

    // Drives the dot in preview only; a single constant in live mode so it adds no emissions.
    val sweepFlow: Flow<Float> =
        if (isPreview) {
            val fracPerTick =
                OVERVIEW_PREVIEW_PX_PER_SEC * OVERVIEW_PREVIEW_TICK_MS / 1000f /
                    widthPx.coerceAtLeast(1).toFloat()
            flow {
                var frac = 0f
                while (true) {
                    emit(frac)
                    frac = if (frac + fracPerTick > 1f) 0f else frac + fracPerTick
                    delay(OVERVIEW_PREVIEW_TICK_MS)
                }
            }
        } else {
            flowOf(0f)
        }

    return combine(
        karooSystem.streamNavigationState().sample(HUD_UPDATE_INTERVAL_MS),
        distFlow,
        context.streamRouteRemainingConfig(),
        sweepFlow,
    ) { navState, distState, routeConfig, sweep ->
        val isNight =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val route = navState.state as? OnNavigationState.NavigationState.NavigatingRoute
        val encoded = route?.routeElevationPolyline ?: ""
        if (encoded.isBlank() && !isPreview) return@combine null

        // Cache by route AND simplification so changing the knob re-simplifies.
        val key = (if (isPreview) "preview" else encoded) + "|" + routeConfig.simplification.name
        if (key != cachedKey) {
            val raw = if (isPreview) previewElevationFixture() else decodeElevationPolyline(encoded)
            cachedPoints = visvalingamToCount(raw, routeConfig.simplification.targetCount)
            cachedKey = key
        }
        val points = cachedPoints
        if (points.size < 2) return@combine null

        val routeLengthM = route?.routeDistance?.toFloat() ?: points.last().first
        val streaming = distState as? StreamState.Streaming
        val distToDest =
            streaming?.dataPoint?.values?.get(DataType.Field.DISTANCE_TO_DESTINATION)?.toFloat()
        val onRoute =
            streaming?.dataPoint?.values?.get(DataType.Field.ON_ROUTE)?.let { it >= 0.5 } ?: true
        val positionM =
            when {
                isPreview -> routeLengthM * sweep
                distToDest != null -> (routeLengthM - distToDest).coerceIn(0f, routeLengthM)
                else -> 0f
            }
        // Red only when actually off a navigated route; yellow otherwise (matches
        // sparklineBitmapFlow).
        val dotColor =
            if (route != null && !onRoute) KAROO_REJOIN_RED.toArgb() else BarberfishYellow.toArgb()
        renderOverviewSparkline(points, positionM, widthPx, heightPx, dotColor, isNight)
    }
}
