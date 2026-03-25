package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.GradePalette

// Elevation polyline: Google Encoded Polyline, precision=1 (divisor=10),
// lat = cumulative distance in metres, lng = elevation in metres.
// Confirmed by Task 5 spike and consistent with karoo-routegraph source (LineString.fromPolyline(it, 1)).

/**
 * Decodes the Karoo elevation polyline into a list of (distanceM, elevationM) pairs.
 * Returns empty list for blank or invalid input.
 */
internal fun decodeElevationPolyline(encoded: String): List<Pair<Float, Float>> {
    if (encoded.isBlank()) return emptyList()
    val result = mutableListOf<Pair<Float, Float>>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var b: Int
        var result1 = 0
        do {
            b = encoded[index++].code - 63
            result1 = result1 or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (result1 and 1 != 0) (result1 shr 1).inv() else result1 shr 1

        shift = 0
        result1 = 0
        do {
            b = encoded[index++].code - 63
            result1 = result1 or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (result1 and 1 != 0) (result1 shr 1).inv() else result1 shr 1

        result.add(Pair(lat / 10f, lng / 10f))
    }
    return result
}

private const val LOOKAHEAD_M = 10_000f
private const val POSITION_FRACTION = 0.25f
private const val MIN_FILL_PX = 4f        // skip colour fills narrower than this many pixels
private const val MIN_ELEV_RANGE_M = 200f // floor for y-axis so flat terrain doesn't inflate

/**
 * Renders a Tufte-style elevation sparkline strip.
 * Returns null if [elevationPoints] is empty.
 *
 * Rendering layers (bottom to top):
 *  1. Subtle base silhouette fill (~6% white)
 *  2. Climb fills for grade ≥ gradeThreshold(palette), from gradeColor()
 *  3. Past outline (left of dot): 22% white, strokeWidth 1.5px
 *  4. Ahead outline (right of dot): 72% white, strokeWidth 1.5px
 *  5. Distance tick at +5 km ahead: 1px stroke, "5" label, 30% white
 *  6. Position dot: circle radius 2.5px, ICON_TINT_TEAL (#31E09A)
 */
internal fun renderElevationSparkline(
    elevationPoints: List<Pair<Float, Float>>, // (distanceM, elevationM)
    positionM: Float,
    widthPx: Int,
    heightPx: Int,
    density: Float,
    palette: GradePalette,
    readable: Boolean,
): Bitmap? {
    if (elevationPoints.isEmpty()) return null

    // Clamp window to route bounds so the sparkline fills full width even at the start.
    // The dot migrates from the left edge to the 25% position as you accumulate past distance.
    val firstDist = elevationPoints.first().first
    val lastDist  = elevationPoints.last().first
    val rawEnd    = positionM - LOOKAHEAD_M * POSITION_FRACTION + LOOKAHEAD_M
    val windowEnd = rawEnd.coerceAtMost(lastDist)
    val windowStart = (windowEnd - LOOKAHEAD_M).coerceAtLeast(firstDist)

    val visible = elevationPoints
        .filter { (d, _) -> d in windowStart..windowEnd }
        .ifEmpty { return null }

    val elevMin   = visible.minOf { it.second }
    val elevMax   = visible.maxOf { it.second }
    val elevRange = (elevMax - elevMin).coerceAtLeast(MIN_ELEV_RANGE_M)

    fun toX(d: Float) = ((d - windowStart) / LOOKAHEAD_M * widthPx).coerceIn(0f, widthPx.toFloat())
    fun toY(e: Float) = (heightPx - (e - elevMin) / elevRange * (heightPx - 2) - 1f).coerceIn(0f, heightPx.toFloat())

    val dotX = toX(positionM)
    val dotY = visible.minByOrNull { (d, _) -> kotlin.math.abs(d - positionM) }
        ?.let { (_, e) -> toY(e) } ?: heightPx * 0.9f

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
        it.density = Bitmap.DENSITY_NONE  // prevent RemoteViews auto-scaling; fitXY handles fill
    }
    val canvas = Canvas(bitmap)
    val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

    // Past points — reused for grade fill overlay (step 2b) and outline (step 3).
    val pastSilPts = visible.filter { (d, _) -> d <= positionM }

    // 1. Ahead silhouette fill — subtle (~6% white)
    val aheadSilPts = visible.filter { (d, _) -> d >= positionM }
    if (aheadSilPts.isNotEmpty()) {
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(15, 255, 255, 255)
        val path = Path().apply {
            moveTo(dotX, dotY)
            aheadSilPts.forEach { (d, e) -> lineTo(toX(d), toY(e)) }
            lineTo(toX(aheadSilPts.last().first), heightPx.toFloat())
            lineTo(dotX, heightPx.toFloat())
            close()
        }
        canvas.drawPath(path, paint)
    }

    // 2. Climb fills — merge consecutive same-color segments into one polygon to eliminate seams.
    val threshold = gradeThreshold(palette)
    paint.style = Paint.Style.FILL
    run {
        var runColor: Int? = null
        val runPts = mutableListOf<Pair<Float, Float>>()

        fun flushRun() {
            val color = runColor ?: return
            if (runPts.size < 2) { runPts.clear(); runColor = null; return }
            val fillWidth = toX(runPts.last().first) - toX(runPts.first().first)
            if (fillWidth < MIN_FILL_PX) { runPts.clear(); runColor = null; return }
            val path = Path()
            path.moveTo(toX(runPts.first().first), toY(runPts.first().second))
            runPts.drop(1).forEach { (d, e) -> path.lineTo(toX(d), toY(e)) }
            path.lineTo(toX(runPts.last().first), heightPx.toFloat())
            path.lineTo(toX(runPts.first().first), heightPx.toFloat())
            path.close()
            paint.color = color
            canvas.drawPath(path, paint)
            runPts.clear()
            runColor = null
        }

        for (i in 0 until visible.lastIndex) {
            val (d1, e1) = visible[i]
            val (d2, e2) = visible[i + 1]
            val distDelta = d2 - d1
            if (distDelta <= 0f) { flushRun(); continue }
            val grade = (e2 - e1) / distDelta * 100.0
            val segColor = if (grade >= threshold) gradeColor(grade, palette, readable)?.toArgb() else null
            if (segColor == null) { flushRun(); continue }
            if (segColor != runColor) { flushRun(); runColor = segColor }
            if (runPts.isEmpty()) runPts.add(d1 to e1)
            runPts.add(d2 to e2)
        }
        flushRun()
    }

    // 2b. Dark overlay on past region to grey out grade fills
    if (pastSilPts.isNotEmpty()) {
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(140, 0, 0, 0)
        val path = Path().apply {
            moveTo(toX(pastSilPts.first().first), toY(pastSilPts.first().second))
            pastSilPts.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
            lineTo(dotX, dotY)
            lineTo(dotX, heightPx.toFloat())
            lineTo(toX(pastSilPts.first().first), heightPx.toFloat())
            close()
        }
        canvas.drawPath(path, paint)
    }

    // 3 & 4. Outlines (past = dimmed, ahead = bright)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.strokeJoin = Paint.Join.ROUND
    val pastPts = visible.filter { (d, _) -> toX(d) <= dotX }
    if (pastPts.isNotEmpty()) {
        paint.color = android.graphics.Color.argb(255, 100, 100, 100)
        val pastPath = Path().apply {
            moveTo(toX(pastPts.first().first), toY(pastPts.first().second))
            pastPts.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
        }
        canvas.drawPath(pastPath, paint)
    }
    val aheadPts = visible.filter { (d, _) -> toX(d) >= dotX }
    if (aheadPts.isNotEmpty()) {
        paint.color = android.graphics.Color.WHITE
        val aheadPath = Path().apply {
            moveTo(toX(aheadPts.first().first), toY(aheadPts.first().second))
            aheadPts.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
        }
        canvas.drawPath(aheadPath, paint)
    }

    // 5. Distance labels at 5 km intervals ahead — adaptive placement, no tick lines
    paint.style = Paint.Style.FILL
    paint.textSize = 8f * density
    paint.color = android.graphics.Color.argb(220, 255, 255, 255)
    val labelGap = 4f * density
    var tickDist = (kotlin.math.ceil((positionM + 1f) / 5_000f) * 5_000f)
    while (tickDist <= positionM + LOOKAHEAD_M) {
        val tickX = toX(tickDist)
        if (tickX in 0f..widthPx.toFloat()) {
            val elevAtTick = visible.minByOrNull { (d, _) -> kotlin.math.abs(d - tickDist) }
                ?.second ?: visible.last().second
            val yAtTick = toY(elevAtTick)
            val label = ((tickDist - positionM) / 1_000f).toInt().toString()
            val labelW = paint.measureText(label)
            val labelY = if (yAtTick > heightPx / 2f)
                yAtTick - labelGap                      // profile low → label above
            else
                yAtTick + labelGap + paint.textSize     // profile high → label below
            canvas.drawText(label, tickX - labelW / 2f, labelY.coerceIn(paint.textSize, heightPx.toFloat()), paint)
        }
        tickDist += 5_000f
    }

    // 6. Position dot
    paint.style = Paint.Style.FILL
    paint.color = ICON_TINT_TEAL.toArgb()
    canvas.drawCircle(dotX, dotY, 5f, paint)

    return bitmap
}
