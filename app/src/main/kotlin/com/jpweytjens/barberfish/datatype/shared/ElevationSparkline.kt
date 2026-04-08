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

/**
 * Renders a Tufte-style elevation sparkline strip.
 * Returns a bitmap plus the current ratchet range; the bitmap is null if [elevationPoints] is empty.
 *
 * Rendering layers (bottom to top):
 *  1. Ahead silhouette fill (~6% alpha; white on night, black on day)
 *  2. Climb fills for grade ≥ gradeThreshold(palette), from gradeColor(); consecutive
 *     same-colour segments are merged into a single polygon to eliminate seams.
 *  2b. Dark overlay on the past region to grey out grade fills behind the dot
 *      (55% alpha; black on night, white on day)
 *  3. Past outline (left of dot): opaque grey(100,100,100), strokeWidth 3px
 *  4. Ahead outline (right of dot): opaque white on night / black on day, strokeWidth 3px
 *  5. Position dot: circle radius [DOT_RADIUS_PX], colour from [dotColor] (default LemonYellow)
 */

private const val POSITION_FRACTION = 0.05f
private const val MIN_FILL_PX = 1f          // skip colour fills narrower than this many pixels
private const val RATCHET_DECAY_M_PER_M = 40f / 1000f  // 40 m scale decay per 1000 m ridden
private const val LOG_WARP_K = 8f
private const val WARP_STEP_TARGET_M = 25f  // finer than typical elevation polyline spacing (~80-100m), GPS movement per render irrelevant
private const val DOT_RADIUS_PX = 5f

internal fun renderElevationSparkline(
    elevationPoints: List<Pair<Float, Float>>, // (distanceM, elevationM)
    positionM: Float,
    widthPx: Int,
    heightPx: Int,
    density: Float,
    palette: GradePalette,
    readable: Boolean,
    lookaheadM: Float = 10_000f,
    skipBands: Int = 1,
    displayedRange: Float = 0f,
    distanceDeltaM: Float = 0f,
    dotColor: Int = LemonYellow.toArgb(),
    isNightMode: Boolean = true,
): Pair<Bitmap?, Float> {
    if (elevationPoints.isEmpty()) return Pair(null, displayedRange)

    // Clamp window to route bounds so the sparkline fills full width even at the start.
    // The dot migrates from the left edge to the 25% position as you accumulate past distance.
    val firstDist = elevationPoints.first().first
    val lastDist  = elevationPoints.last().first
    val rawEnd    = positionM - lookaheadM * POSITION_FRACTION + lookaheadM
    val windowEnd = rawEnd.coerceAtMost(lastDist)
    val windowStart = (windowEnd - lookaheadM).coerceAtLeast(firstDist)

    val visible = elevationPoints
        .filter { (d, _) -> d in windowStart..windowEnd }
        .ifEmpty { return Pair(null, displayedRange) }

    // Y-axis: range from ±50% extended lookahead window (double window).
    val rangeStart = (windowStart - lookaheadM * 0.5f).coerceAtLeast(firstDist)
    val rangeEnd   = (windowEnd   + lookaheadM * 0.5f).coerceAtMost(lastDist)
    val rangePoints = elevationPoints.filter { (d, _) -> d in rangeStart..rangeEnd }
    val elevMin   = rangePoints.minOfOrNull { it.second } ?: visible.minOf { it.second }
    val elevMax   = rangePoints.maxOfOrNull { it.second } ?: visible.maxOf { it.second }
    val elevRange = (elevMax - elevMin).coerceAtLeast(50f)

    // Ratchet: grow instantly, decay slowly as distance is ridden.
    val newDisplayedRange = if (elevRange > displayedRange) elevRange
        else (displayedRange - RATCHET_DECAY_M_PER_M * distanceDeltaM).coerceAtLeast(elevRange)

    // Builds a cumulative pixel-density integral over the display window.
    // Points near positionM receive more pixels; distant points receive fewer.
    // mag(d) = 1 + K·exp(-normalised_distance · K/2) — peaks at dot, falls off exponentially.
    val warpIntegrationSteps = (lookaheadM / WARP_STEP_TARGET_M).toInt()
    val pixelDensityCumulative = FloatArray(warpIntegrationSteps + 1)
    val integrationStepM = (windowEnd - windowStart) / warpIntegrationSteps
    for (step in 0 until warpIntegrationSteps) {
        val routeDistanceM = windowStart + step * integrationStepM
        val normalisedDistanceFromDot = kotlin.math.abs(routeDistanceM - positionM) / lookaheadM
        val pixelsPerMetre = 1f + LOG_WARP_K * kotlin.math.exp(-normalisedDistanceFromDot * LOG_WARP_K * 0.5f).toFloat()
        pixelDensityCumulative[step + 1] = pixelDensityCumulative[step] + pixelsPerMetre * integrationStepM
    }
    val totalWarpedBudget = pixelDensityCumulative[warpIntegrationSteps]

    // Maps a route distance to a screen x-coordinate by looking up its share
    // of the total pixel budget accumulated up to that point.
    fun toX(routeDistanceM: Float): Float {
        val windowFraction = ((routeDistanceM - windowStart) / (windowEnd - windowStart)).coerceIn(0f, 1f)
        val lookupIndex = windowFraction * warpIntegrationSteps
        val lowerStep = lookupIndex.toInt().coerceIn(0, warpIntegrationSteps - 1)
        val interpolationFraction = lookupIndex - lowerStep
        val budgetConsumed = pixelDensityCumulative[lowerStep] +
            interpolationFraction * (pixelDensityCumulative[lowerStep + 1] - pixelDensityCumulative[lowerStep])
        return ((budgetConsumed / totalWarpedBudget) * widthPx).coerceIn(0f, widthPx.toFloat())
    }
    fun toY(e: Float) = (heightPx - (e - elevMin) / newDisplayedRange * (heightPx - 2 * DOT_RADIUS_PX) - DOT_RADIUS_PX).coerceIn(0f, heightPx.toFloat())

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
        paint.color = if (isNightMode) android.graphics.Color.argb(15, 255, 255, 255)
            else android.graphics.Color.argb(15, 0, 0, 0)
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
    val threshold = gradeThreshold(palette, skipBands)
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
        paint.color = if (isNightMode) android.graphics.Color.argb(140, 0, 0, 0)
            else android.graphics.Color.argb(140, 255, 255, 255)
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
        paint.color = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val aheadPath = Path().apply {
            moveTo(toX(aheadPts.first().first), toY(aheadPts.first().second))
            aheadPts.drop(1).forEach { (d, e) -> lineTo(toX(d), toY(e)) }
        }
        canvas.drawPath(aheadPath, paint)
    }

    // 5. Position dot
    paint.style = Paint.Style.FILL
    paint.color = dotColor
    canvas.drawCircle(dotX, dotY, DOT_RADIUS_PX, paint)

    return Pair(bitmap, newDisplayedRange)
}

/** Alias for [rvvElevationFixture]: the default fixture shown in config-screen previews. */
internal fun previewElevationFixture(): List<Pair<Float, Float>> = rvvElevationFixture()

/**
 * Generates a synthetic elevation fixture: flat lead-in → climb → flat run-out.
 * All fixtures span 0–20 km with the climb starting at 10 km.
 *
 * @param gainM total elevation gain on the climb
 * @param grade climb gradient (0.03 = 3%, 0.20 = 20%)
 */
private fun syntheticClimbFixture(gainM: Float, grade: Float): List<Pair<Float, Float>> {
    val baseElev = 50f
    val climbStart = 10_000f
    val routeEnd = 20_000f
    val climbLength = gainM / grade
    val climbEnd = climbStart + climbLength
    val topElev = baseElev + gainM

    val points = mutableListOf<Pair<Float, Float>>()

    // Flat sections: every 50m (matches typical Strava route density)
    var d = 0f
    while (d < climbStart) { points.add(d to baseElev); d += 50f }
    points.add(climbStart to baseElev)

    // Climb: every 20m
    d = climbStart + 20f
    while (d < climbEnd) {
        val elev = baseElev + (d - climbStart) * grade
        points.add(d to (Math.round(elev * 10f) / 10f))
        d += 20f
    }
    points.add((Math.round(climbEnd * 10f) / 10f) to topElev)

    // Flat run-out: every 50m
    d = climbEnd + 50f
    while (d < routeEnd) { points.add((Math.round(d * 10f) / 10f) to topElev); d += 50f }
    points.add(routeEnd to topElev)

    return points
}

// 20m gain — varying difficulty (L×G²)
internal fun gain20WallFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(20f, 0.20f)       // 100m @ 20%, L×G²=4.0
internal fun gain20SteepFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(20f, 0.10f)      // 200m @ 10%, L×G²=2.0
internal fun gain20ModerateFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(20f, 0.05f)   // 400m @  5%, L×G²=1.0
internal fun gain20GentleFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(20f, 0.03f)     // 667m @  3%, L×G²=0.6

// 50m gain — varying difficulty (L×G²)
internal fun gain50WallFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(50f, 0.20f)       // 250m @ 20%, L×G²=10.0
internal fun gain50SteepFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(50f, 0.10f)      // 500m @ 10%, L×G²=5.0
internal fun gain50ModerateFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(50f, 0.05f)   // 1km  @  5%, L×G²=2.5
internal fun gain50GentleFixture(): List<Pair<Float, Float>> = syntheticClimbFixture(50f, 0.03f)     // 1.7km@  3%, L×G²=1.5

internal val ELEVATION_FIXTURES: LinkedHashMap<String, () -> List<Pair<Float, Float>>> = linkedMapOf(
    "RvV (last 20km)" to ::rvvElevationFixture,
    "20m — 100m @ 20%" to ::gain20WallFixture,
    "20m — 200m @ 10%" to ::gain20SteepFixture,
    "20m — 400m @ 5%" to ::gain20ModerateFixture,
    "20m — 667m @ 3%" to ::gain20GentleFixture,
    "50m — 250m @ 20%" to ::gain50WallFixture,
    "50m — 500m @ 10%" to ::gain50SteepFixture,
    "50m — 1km @ 5%" to ::gain50ModerateFixture,
    "50m — 1.7km @ 3%" to ::gain50GentleFixture,
)

/** Last 20 km of Tour of Flanders 2025 (RvV). Used for debug builds. */
internal fun rvvElevationFixture(): List<Pair<Float, Float>> = listOf(
    97.1f to 13.0f, 171.4f to 13.0f, 213.6f to 14.0f, 247.8f to 14.0f, 300.6f to 14.0f, 399.5f to 14.0f,
    459.4f to 14.0f, 510.7f to 14.0f, 566.5f to 14.0f, 663.8f to 13.0f, 723.0f to 13.0f, 785.2f to 12.0f,
    877.7f to 11.0f, 917.6f to 11.0f, 974.9f to 11.0f, 1021.6f to 11.0f, 1101.8f to 10.0f, 1141.7f to 10.0f,
    1209.3f to 12.0f, 1271.6f to 13.0f, 1386.9f to 15.0f, 1432.1f to 17.0f, 1465.4f to 20.0f, 1547.5f to 23.0f,
    1577.6f to 25.0f, 1627.9f to 29.0f, 1718.7f to 33.0f, 1792.1f to 36.0f, 1845.5f to 40.0f, 1879.4f to 44.0f,
    1997.0f to 51.0f, 2029.7f to 58.0f, 2133.1f to 65.0f, 2167.5f to 69.0f, 2206.9f to 72.0f, 2257.6f to 75.0f,
    2313.2f to 79.0f, 2428.8f to 81.0f, 2483.3f to 82.0f, 2515.5f to 82.0f, 2601.2f to 82.0f, 2644.8f to 83.0f,
    2718.8f to 83.0f, 2749.7f to 84.0f, 2892.7f to 87.0f, 2973.4f to 91.0f, 3060.5f to 95.0f, 3153.2f to 99.0f,
    3210.5f to 99.0f, 3270.3f to 100.0f, 3302.1f to 100.0f, 3344.3f to 99.0f, 3399.8f to 98.0f, 3437.0f to 98.0f,
    3478.6f to 98.0f, 3590.1f to 98.0f, 3828.9f to 100.0f, 3940.6f to 102.0f, 4107.1f to 107.0f, 4159.2f to 109.0f,
    4257.8f to 111.0f, 4301.1f to 111.0f, 4340.9f to 112.0f, 4379.5f to 111.0f, 4428.2f to 109.0f, 4474.5f to 107.0f,
    4515.7f to 104.0f, 4576.6f to 103.0f, 4686.4f to 100.0f, 4739.3f to 96.0f, 4782.9f to 94.0f, 4827.4f to 93.0f,
    4885.6f to 91.0f, 4943.2f to 89.0f, 4995.9f to 86.0f, 5159.5f to 84.0f, 5218.9f to 83.0f, 5254.9f to 81.0f,
    5332.3f to 81.0f, 5383.5f to 80.0f, 5514.9f to 78.0f, 5572.7f to 75.0f, 5605.3f to 70.0f, 5672.0f to 65.0f,
    5719.9f to 59.0f, 5773.0f to 56.0f, 5893.5f to 51.0f, 5943.1f to 47.0f, 5986.4f to 44.0f, 6027.5f to 42.0f,
    6150.8f to 37.0f, 6197.0f to 35.0f, 6234.0f to 34.0f, 6269.5f to 33.0f, 6315.5f to 33.0f, 6371.3f to 33.0f,
    6410.0f to 34.0f, 6568.6f to 42.0f, 6606.8f to 52.0f, 6662.6f to 55.0f, 6712.7f to 61.0f, 6749.9f to 67.0f,
    6815.9f to 71.0f, 6874.6f to 73.0f, 6912.2f to 72.0f, 6952.4f to 70.0f, 7032.5f to 68.0f, 7080.3f to 65.0f,
    7169.3f to 60.0f, 7224.6f to 56.0f, 7273.5f to 54.0f, 7312.9f to 50.0f, 7426.2f to 44.0f, 7542.4f to 39.0f,
    7609.5f to 34.0f, 7656.6f to 29.0f, 7694.7f to 26.0f, 7730.6f to 23.0f, 7771.5f to 22.0f, 7801.6f to 20.0f,
    7831.9f to 19.0f, 7968.1f to 17.0f, 8011.6f to 15.0f, 8176.2f to 14.0f, 8225.6f to 14.0f, 8256.1f to 14.0f,
    8298.5f to 14.0f, 8372.2f to 14.0f, 8410.2f to 14.0f, 8452.4f to 13.0f, 8493.0f to 13.0f, 8662.7f to 13.0f,
    8698.9f to 12.0f, 8781.3f to 12.0f, 8813.2f to 12.0f, 8923.8f to 12.0f, 9012.2f to 12.0f, 9050.3f to 12.0f,
    9103.3f to 12.0f, 9155.4f to 12.0f, 9186.3f to 13.0f, 9437.0f to 13.0f, 9495.8f to 13.0f, 9550.8f to 14.0f,
    9597.1f to 14.0f, 9662.4f to 15.0f, 9695.7f to 15.0f, 9746.9f to 15.0f, 9833.1f to 14.0f, 9872.0f to 13.0f,
    9973.6f to 13.0f, 10080.0f to 13.0f, 10310.0f to 12.0f, 10415.2f to 12.0f, 10472.5f to 12.0f, 10563.2f to 11.0f,
    10610.3f to 11.0f, 10651.1f to 11.0f, 10696.3f to 11.0f, 10726.3f to 11.0f, 10772.7f to 11.0f, 10811.1f to 11.0f,
    10890.1f to 11.0f, 10974.8f to 12.0f, 11061.1f to 12.0f, 11166.2f to 13.0f, 11225.9f to 14.0f, 11356.9f to 14.0f,
    11504.0f to 13.0f, 11539.2f to 12.0f, 11600.9f to 12.0f, 11667.9f to 13.0f, 11704.8f to 13.0f, 11889.1f to 14.0f,
    11957.0f to 14.0f, 12021.2f to 14.0f, 12230.7f to 14.0f, 12322.0f to 15.0f, 12442.7f to 18.0f, 12506.2f to 18.0f,
    12557.7f to 19.0f, 12932.8f to 18.0f, 13177.1f to 18.0f, 13302.3f to 17.0f, 13527.7f to 17.0f, 13626.1f to 17.0f,
    13770.6f to 16.0f, 13954.5f to 16.0f, 14086.2f to 16.0f, 14169.7f to 16.0f, 14274.3f to 16.0f, 14361.6f to 15.0f,
    14422.6f to 14.0f, 14601.7f to 14.0f, 14739.0f to 14.0f, 14804.6f to 14.0f, 14866.4f to 14.0f, 14902.9f to 14.0f,
    14937.3f to 14.0f, 15187.2f to 14.0f, 15226.4f to 14.0f, 15264.7f to 14.0f, 15303.9f to 14.0f, 15343.9f to 14.0f,
    15520.2f to 14.0f, 15606.0f to 14.0f, 15636.3f to 14.0f, 15699.2f to 14.0f, 15796.2f to 14.0f, 15843.0f to 14.0f,
    15980.9f to 14.0f, 16039.1f to 14.0f, 16179.4f to 14.0f, 16250.8f to 15.0f, 16285.9f to 15.0f, 16413.0f to 16.0f,
    16447.3f to 16.0f, 16582.4f to 17.0f, 16652.6f to 17.0f, 16686.4f to 17.0f, 16721.3f to 18.0f, 16761.3f to 18.0f,
    16816.0f to 18.0f, 16851.5f to 18.0f, 16915.3f to 19.0f, 17040.2f to 19.0f, 17118.4f to 19.0f, 17185.6f to 19.0f,
    17286.5f to 19.0f, 17328.1f to 19.0f, 17402.4f to 19.0f, 17498.7f to 19.0f, 17599.4f to 19.0f, 17632.7f to 18.0f,
    17753.2f to 18.0f, 17797.4f to 18.0f, 17830.1f to 18.0f, 18092.5f to 18.0f, 18221.9f to 18.0f, 18336.5f to 16.0f,
    18378.7f to 15.0f, 18478.0f to 14.0f, 18550.5f to 14.0f, 18620.4f to 13.0f, 18675.1f to 12.0f, 18728.0f to 12.0f,
    18847.3f to 11.0f, 18907.7f to 10.0f, 19176.3f to 11.0f, 19478.3f to 11.0f, 19712.0f to 10.0f, 19772.7f to 9.0f,
    19869.4f to 9.0f, 19947.3f to 9.0f, 20000.0f to 10.0f,
)
