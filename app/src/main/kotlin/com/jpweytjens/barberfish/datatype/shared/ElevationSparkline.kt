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

    val pastM  = LOOKAHEAD_M * POSITION_FRACTION
    val aheadM = LOOKAHEAD_M * (1f - POSITION_FRACTION)
    val windowStart = positionM - pastM
    val windowEnd   = positionM + aheadM

    val visible = elevationPoints
        .filter { (d, _) -> d in windowStart..windowEnd }
        .ifEmpty { return null }

    val elevMin   = visible.minOf { it.second }
    val elevMax   = visible.maxOf { it.second }
    val elevRange = (elevMax - elevMin).coerceAtLeast(1f)

    fun toX(d: Float) = ((d - windowStart) / LOOKAHEAD_M * widthPx).coerceIn(0f, widthPx.toFloat())
    fun toY(e: Float) = (heightPx - (e - elevMin) / elevRange * (heightPx - 2) - 1f).coerceIn(0f, heightPx.toFloat())

    val dotX = toX(positionM)

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
        it.density = (density * 160f).toInt()
    }
    val canvas = Canvas(bitmap)
    val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Base silhouette
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.argb(15, 255, 255, 255)
    val silhouette = Path().apply {
        moveTo(toX(visible.first().first), toY(visible.first().second))
        visible.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
        lineTo(toX(visible.last().first), heightPx.toFloat())
        lineTo(toX(visible.first().first), heightPx.toFloat())
        close()
    }
    canvas.drawPath(silhouette, paint)

    // 2. Climb fills (grade ≥ threshold, no descents)
    val threshold = gradeThreshold(palette)
    paint.style = Paint.Style.FILL
    for (i in 0 until visible.lastIndex) {
        val (d1, e1) = visible[i]
        val (d2, e2) = visible[i + 1]
        val distDelta = d2 - d1
        if (distDelta <= 0f) continue
        val grade = (e2 - e1) / distDelta * 100.0
        if (grade < threshold) continue
        paint.color = gradeColor(grade, palette, readable)?.toArgb() ?: continue
        val x1 = toX(d1); val y1 = toY(e1)
        val x2 = toX(d2); val y2 = toY(e2)
        val fill = Path().apply {
            moveTo(x1, y1); lineTo(x2, y2)
            lineTo(x2, heightPx.toFloat()); lineTo(x1, heightPx.toFloat()); close()
        }
        canvas.drawPath(fill, paint)
    }

    // 3 & 4. Outlines (past = dimmed, ahead = bright)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f
    paint.strokeJoin = Paint.Join.ROUND
    val pastPts = visible.filter { (d, _) -> toX(d) <= dotX }
    if (pastPts.isNotEmpty()) {
        paint.color = android.graphics.Color.argb(56, 255, 255, 255)
        val pastPath = Path().apply {
            moveTo(toX(pastPts.first().first), toY(pastPts.first().second))
            pastPts.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
        }
        canvas.drawPath(pastPath, paint)
    }
    val aheadPts = visible.filter { (d, _) -> toX(d) >= dotX }
    if (aheadPts.isNotEmpty()) {
        paint.color = android.graphics.Color.argb(184, 255, 255, 255)
        val aheadPath = Path().apply {
            moveTo(toX(aheadPts.first().first), toY(aheadPts.first().second))
            aheadPts.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
        }
        canvas.drawPath(aheadPath, paint)
    }

    // 5. Distance tick at +5 km ahead
    val tickX = toX(positionM + 5_000f)
    if (tickX in 0f..widthPx.toFloat()) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = android.graphics.Color.argb(77, 255, 255, 255)
        canvas.drawLine(tickX, 0f, tickX, 6f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 8f * density
        paint.color = android.graphics.Color.argb(71, 255, 255, 255)
        canvas.drawText("5", tickX - paint.textSize * 0.3f, heightPx * 0.85f, paint)
    }

    // 6. Position dot
    val dotY = visible.minByOrNull { (d, _) -> kotlin.math.abs(d - positionM) }
        ?.let { (_, e) -> toY(e) } ?: heightPx * 0.9f
    paint.style = Paint.Style.FILL
    paint.color = ICON_TINT_TEAL.toArgb()
    canvas.drawCircle(dotX, dotY, 2.5f, paint)

    return bitmap
}
