package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineMode

// Elevation polyline: Google Encoded Polyline, precision=1 (divisor=10),
// lat = cumulative distance in metres, lng = elevation in metres.
// Confirmed by Task 5 spike and consistent with karoo-routegraph source (LineString.fromPolyline(it, 1)).

/**
 * Decodes the Karoo elevation polyline into a list of (distanceM, elevationM) pairs.
 * Returns empty list for blank or invalid input. Returns whatever was decoded up to
 * the truncation point if [encoded] ends mid-varint.
 */
internal fun decodeElevationPolyline(encoded: String): List<Pair<Float, Float>> {
    if (encoded.isBlank()) return emptyList()
    val result = mutableListOf<Pair<Float, Float>>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        val latDelta = readVarint(encoded, index) ?: break
        lat += latDelta.value
        index = latDelta.nextIndex

        val lngDelta = readVarint(encoded, index) ?: break
        lng += lngDelta.value
        index = lngDelta.nextIndex

        result.add(Pair(lat / 10f, lng / 10f))
    }
    return result
}

private data class VarintResult(val value: Int, val nextIndex: Int)

/** Reads one zig-zag varint starting at [start]. Returns null if the input is truncated. */
private fun readVarint(encoded: String, start: Int): VarintResult? {
    var index = start
    var shift = 0
    var value = 0
    while (true) {
        if (index >= encoded.length) return null
        val b = encoded[index++].code - 63
        value = value or ((b and 0x1f) shl shift)
        if (b < 0x20) break
        shift += 5
    }
    val decoded = if (value and 1 != 0) (value shr 1).inv() else value shr 1
    return VarintResult(decoded, index)
}

/**
 * Simplifies an elevation polyline using Visvalingam–Whyatt.
 *
 * Repeatedly removes the interior point whose triangle with its two live neighbours has
 * the smallest area, stopping when the smallest remaining area ≥ [minAreaM2]. Endpoints
 * are always preserved. Input is `(distanceM, elevationM)` pairs, so the triangle area is
 * in m² and has a direct physical meaning: roughly the smallest bump (width × height)
 * the simplifier refuses to erase.
 *
 * Returns [points] unchanged when `points.size < 3` or `minAreaM2 <= 0f`.
 */
internal fun visvalingamWhyatt(
    points: List<Pair<Float, Float>>,
    minAreaM2: Float,
): List<Pair<Float, Float>> {
    if (points.size < 3 || minAreaM2 <= 0f) return points

    val n = points.size
    val prev = IntArray(n) { it - 1 }
    val next = IntArray(n) { it + 1 }
    val alive = BooleanArray(n) { true }
    val version = IntArray(n)      // bump to invalidate stale heap entries

    fun triArea(i: Int): Float {
        val p = prev[i]
        val q = next[i]
        val (d1, e1) = points[p]
        val (d2, e2) = points[i]
        val (d3, e3) = points[q]
        return 0.5f * kotlin.math.abs((d2 - d1) * (e3 - e1) - (d3 - d1) * (e2 - e1))
    }

    data class HeapEntry(val index: Int, val area: Float, val ver: Int)
    val heap = java.util.PriorityQueue<HeapEntry>(n, compareBy { it.area })
    for (i in 1 until n - 1) heap.add(HeapEntry(i, triArea(i), version[i]))

    while (true) {
        val top = heap.poll() ?: break
        if (!alive[top.index] || top.ver != version[top.index]) continue
        if (top.area >= minAreaM2) break

        // Remove point `top.index` from the linked list.
        val l = prev[top.index]
        val r = next[top.index]
        alive[top.index] = false
        next[l] = r
        prev[r] = l

        // Re-enqueue each neighbour with a refreshed area, unless it is an endpoint.
        if (l > 0) {
            version[l]++
            heap.add(HeapEntry(l, triArea(l), version[l]))
        }
        if (r < n - 1) {
            version[r]++
            heap.add(HeapEntry(r, triArea(r), version[r]))
        }
    }

    val out = ArrayList<Pair<Float, Float>>(n)
    var i = 0
    while (i < n) {
        out.add(points[i])
        i = next[i]
    }
    return out
}

/**
 * Renders a Tufte-style elevation sparkline strip.
 * Returns a bitmap plus the current ratchet range; the bitmap is null if [elevationPoints] is empty.
 *
 * Rendering layers (bottom to top):
 *  1. Ahead silhouette fill (~6% alpha; white on night, black on day)
 *  2. Climb (and optionally descent) fills via gradeFillRange(palette), coloured by gradeColor(); consecutive
 *     same-colour segments are merged into a single polygon to eliminate seams.
 *  2b. Overlay on the past region to grey out grade fills behind the dot
 *      (night: 55% alpha black; day: 78% alpha mid-grey — grey desaturates
 *      bright grade colours more effectively than white, which just pastels them)
 *  3. Past outline (left of dot): opaque grey(100,100,100), strokeWidth 3px
 *  4. Ahead outline (right of dot): opaque white on night / black on day, strokeWidth 3px
 *  5. Position dot: circle radius [DOT_RADIUS_PX], colour from [dotColor] (default teal)
 */

private const val MIN_FILL_PX = 1f          // skip colour fills narrower than this many pixels
private const val RATCHET_DECAY_M_PER_M = 40f / 1000f  // 40 m scale decay per 1000 m ridden
private const val WARP_STEP_TARGET_M = 25f  // finer than typical elevation polyline spacing (~80-100m), GPS movement per render irrelevant
private const val DOT_RADIUS_PX = 7f
private const val POI_RADIUS_PX = 7f
private const val MARKER_STROKE_PX = 1.5f
// Half-stroke + radius, ceil'd: keeps the stroked outer edge of the dot/POI inside the bitmap.
private const val MARKER_PAD_PX = 8f

/** Result of [renderElevationSparkline]. Destructurable for call-site convenience. */
internal data class ElevationSparklineResult(val bitmap: Bitmap?, val displayedRange: Float)

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
    skipBandsDescent: Int = 0,
    displayedRange: Float = 0f,
    distanceDeltaM: Float = 0f,
    dotColor: Int = BarberfishYellow.toArgb(),
    isNightMode: Boolean = true,
    minElevRangeM: Float = 50f,
    logWarpK: Float = 8f,
    positionFraction: Float = 0.05f,
    climbRanges: List<Pair<Float, Float>> = emptyList(),
    showClimbs: Boolean = false,
    poiDistances: List<Float> = emptyList(),
    showPois: Boolean = false,
    windowOverride: Pair<Float, Float>? = null,
): ElevationSparklineResult {
    if (elevationPoints.isEmpty()) return ElevationSparklineResult(null, displayedRange)

    val firstDist = elevationPoints.first().first
    val lastDist  = elevationPoints.last().first
    val windowStart: Float
    val windowEnd: Float
    val effWarpK: Float
    if (windowOverride != null) {
        // Climb-only mode: pin the frame to the climb (foot → top) and map linearly —
        // warp centred on the rider is meaningless while approaching from outside the window.
        windowStart = windowOverride.first.coerceAtLeast(firstDist)
        windowEnd   = windowOverride.second.coerceAtMost(lastDist)
        effWarpK    = 0f
    } else {
        // Clamp window to route bounds so the sparkline fills full width even at the start.
        // The dot migrates from the left edge to the 25% position as you accumulate past distance.
        val rawEnd  = positionM - lookaheadM * positionFraction + lookaheadM
        windowEnd   = rawEnd.coerceAtMost(lastDist)
        windowStart = (windowEnd - lookaheadM).coerceAtLeast(firstDist)
        effWarpK    = logWarpK
    }

    // Include one point beyond each edge so segments spanning the window boundary
    // are partially drawn instead of popping in only when fully visible.
    val firstInside = elevationPoints.indexOfFirst { it.first >= windowStart }
    val lastInside  = elevationPoints.indexOfLast  { it.first <= windowEnd }
    if (firstInside < 0 || lastInside < 0 || firstInside > lastInside + 1) {
        return ElevationSparklineResult(null, displayedRange)
    }
    val sliceStart = (firstInside - 1).coerceAtLeast(0)
    val sliceEnd   = (lastInside + 1).coerceAtMost(elevationPoints.lastIndex)
    val visible = elevationPoints.subList(sliceStart, sliceEnd + 1)
    if (visible.isEmpty()) return ElevationSparklineResult(null, displayedRange)

    // Y-axis: derived from the visible window only. 
    // The ratchet below still stabilises the scale during climbs that *are* on screen.
    val elevMin   = visible.minOf { it.second }
    val elevMax   = visible.maxOf { it.second }
    val elevRange = (elevMax - elevMin).coerceAtLeast(minElevRangeM)

    // Ratchet: grow instantly, decay slowly as distance is ridden.
    val newDisplayedRange = if (elevRange > displayedRange) elevRange
        else (displayedRange - RATCHET_DECAY_M_PER_M * distanceDeltaM).coerceAtLeast(elevRange)

    val toX = buildWarpedXMapper(windowStart, windowEnd, positionM, lookaheadM, widthPx, effWarpK)
    fun toY(e: Float) = (heightPx - (e - elevMin) / newDisplayedRange * (heightPx - 2 * MARKER_PAD_PX) - MARKER_PAD_PX).coerceIn(0f, heightPx.toFloat())

    // Partition `visible` around positionM once. Points exactly at positionM appear in
    // both lists so past/ahead polygons meet cleanly at the dot (mirrors the old
    // `d <= positionM` / `d >= positionM` filter semantics).
    val pastEnd    = visible.indexOfFirst { it.first > positionM }.let { if (it < 0) visible.size else it }
    val aheadStart = visible.indexOfFirst { it.first >= positionM }.let { if (it < 0) visible.size else it }
    val pastSilPts  = visible.subList(0, pastEnd)
    val aheadSilPts = visible.subList(aheadStart, visible.size)

    val dotX = toX(positionM)
    // Linear-interpolated elevation at positionM keeps the dot on the outline since
    // the outline pass below uses the same interpolation at the positionM breakpoint.
    // When the rider is outside the window (climb-only approach phase), anchor the dot to
    // the nearest edge (the climb foot) instead of dropping to the bottom fallback.
    val dotAnchorM = positionM.coerceIn(windowStart, windowEnd)
    val dotY = elevationAt(visible, dotAnchorM)?.let { toY(it) } ?: (heightPx * 0.9f)

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
        it.density = Bitmap.DENSITY_NONE  // prevent RemoteViews auto-scaling; fitXY handles fill
    }
    val canvas = Canvas(bitmap)
    val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Ahead silhouette fill — subtle (~6% alpha)
    if (aheadSilPts.isNotEmpty()) {
        paint.style = Paint.Style.FILL
        paint.color = (if (isNightMode) SPARKLINE_SILHOUETTE_NIGHT else SPARKLINE_SILHOUETTE_DAY).toArgb()
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
    val fillRange = gradeFillRange(palette, skipBandsClimb = skipBands, skipBandsDescent = skipBandsDescent)
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
            val withinFill =
                (fillRange.posMin != null && grade >= fillRange.posMin) ||
                (fillRange.negMax != null && grade < fillRange.negMax)
            val segColor = if (withinFill) gradeColor(grade, palette, readable, isNightMode)?.toArgb() else null
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
        paint.color = (if (isNightMode) SPARKLINE_PAST_OVERLAY_NIGHT else SPARKLINE_PAST_OVERLAY_DAY).toArgb()
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

    // 3+4. Outline — single walk over `visible` with per-segment colour. Splits each
    // polyline segment at positionM and at climb boundaries so colour transitions land
    // on the exact breakpoint rather than at polyline-sample midpoints. Colour roles:
    //   past + climb  → muted blue   (CLIMBER_BLUE blended halfway with the past grey)
    //   past          → past grey    (matches the existing dim-past treatment)
    //   ahead + climb → CLIMBER_BLUE
    //   ahead         → white (night) / black (day)
    if (visible.size >= 2) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeJoin = Paint.Join.ROUND

        val pastGrey = SPARKLINE_PAST_OUTLINE.toArgb()
        val aheadColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val aheadClimb = CLIMBER_BLUE.toArgb()
        val pastClimb = SPARKLINE_PAST_CLIMB.toArgb()

        val visibleStart = visible.first().first
        val visibleEnd = visible.last().first
        val breakpoints = sortedSetOf<Float>().apply {
            if (positionM in visibleStart..visibleEnd) add(positionM)
            if (showClimbs) {
                climbRanges.forEach { (s, e) ->
                    if (s in visibleStart..visibleEnd) add(s)
                    if (e in visibleStart..visibleEnd) add(e)
                }
            }
        }

        // Build a walkable polyline that includes interpolated points at every breakpoint.
        val walkable = ArrayList<Pair<Float, Float>>(visible.size + breakpoints.size)
        walkable.add(visible[0])
        for (i in 0 until visible.lastIndex) {
            val (d1, e1) = visible[i]
            val (d2, e2) = visible[i + 1]
            if (d2 > d1) {
                breakpoints.subSet(d1, false, d2, false).forEach { c ->
                    val t = (c - d1) / (d2 - d1)
                    walkable.add(c to (e1 + (e2 - e1) * t))
                }
            }
            walkable.add(visible[i + 1])
        }

        fun colorAt(d: Float): Int {
            val isPast = d < positionM
            val isClimb = showClimbs && climbRanges.any { (s, e) -> d in s..e }
            return when {
                isPast && isClimb -> pastClimb
                isPast -> pastGrey
                isClimb -> aheadClimb
                else -> aheadColor
            }
        }

        // Walk walkable, batching consecutive same-colour sub-segments into one Path each.
        var runStart = 0
        while (runStart < walkable.lastIndex) {
            val midD = (walkable[runStart].first + walkable[runStart + 1].first) / 2f
            val runColor = colorAt(midD)
            var runEnd = runStart + 1
            while (runEnd < walkable.lastIndex) {
                val nextMidD = (walkable[runEnd].first + walkable[runEnd + 1].first) / 2f
                if (colorAt(nextMidD) != runColor) break
                runEnd++
            }
            val path = Path().apply {
                val (d0, e0) = walkable[runStart]
                moveTo(toX(d0), toY(e0))
                for (j in runStart + 1..runEnd) {
                    val (d, e) = walkable[j]
                    lineTo(toX(d), toY(e))
                }
            }
            paint.color = runColor
            canvas.drawPath(path, paint)
            runStart = runEnd
        }
    }

    // 4c. POI markers — generic filled circle, drawn under the position dot.
    // Past markers use the past-outline grey to match the muting applied to the past
    // outline; ahead markers keep their bright fill so upcoming POIs stay legible.
    if (showPois && poiDistances.isNotEmpty()) {
        val aheadFill = (if (isNightMode) SPARKLINE_POI_FILL_NIGHT else SPARKLINE_POI_FILL_DAY).toArgb()
        val aheadStroke = if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        val pastFill = SPARKLINE_PAST_OUTLINE.toArgb()
        val pastStroke = if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        for (d in poiDistances) {
            if (d < windowStart || d > windowEnd) continue
            val elev = elevationAt(visible, d) ?: continue
            val cx = toX(d)
            val cy = toY(elev).coerceIn(MARKER_PAD_PX, heightPx - MARKER_PAD_PX)
            val isPast = d < positionM
            paint.style = Paint.Style.FILL
            paint.color = if (isPast) pastFill else aheadFill
            canvas.drawCircle(cx, cy, POI_RADIUS_PX, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = MARKER_STROKE_PX
            paint.color = if (isPast) pastStroke else aheadStroke
            canvas.drawCircle(cx, cy, POI_RADIUS_PX, paint)
        }
    }

    // 5. Position dot — matches POI markers in size and outline so they share visual weight.
    paint.style = Paint.Style.FILL
    paint.color = dotColor
    canvas.drawCircle(dotX, dotY, DOT_RADIUS_PX, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = MARKER_STROKE_PX
    paint.color = if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    canvas.drawCircle(dotX, dotY, DOT_RADIUS_PX, paint)

    return ElevationSparklineResult(bitmap, newDisplayedRange)
}

/**
 * Linear-interpolates the elevation at a given route distance from a sorted-by-distance list
 * of `(distanceM, elevationM)` polyline points. Returns null on an empty input. Distances
 * outside the polyline range clamp to the first/last sample.
 */
internal fun elevationAt(points: List<Pair<Float, Float>>, distanceM: Float): Float? {
    if (points.isEmpty()) return null
    if (distanceM <= points.first().first) return points.first().second
    if (distanceM >= points.last().first) return points.last().second
    for (i in 0 until points.lastIndex) {
        val (d1, e1) = points[i]
        val (d2, e2) = points[i + 1]
        if (distanceM in d1..d2) {
            val t = if (d2 > d1) (distanceM - d1) / (d2 - d1) else 0f
            return e1 + (e2 - e1) * t
        }
    }
    return null
}

internal data class ClimbReveal(val windowOverride: Pair<Float, Float>?, val visible: Boolean)

/**
 * Climb-only visibility for the HUD sparkline, shared by the live flow and the config preview.
 * In [SparklineMode.CLIMBS] the strip reveals once the rider is within a climb's
 * difficulty-scaled approach and stays until its top, pinned to that climb (foot → top); when
 * no climb is in range it hides. [SparklineMode.OFF] hides it; [SparklineMode.ON] shows it with
 * no window override. Approach distance scales with the PCS climb score (see [climbApproachM]).
 */
internal fun resolveClimbReveal(
    mode: SparklineMode,
    climbRanges: List<Pair<Float, Float>>,
    elevationPoints: List<Pair<Float, Float>>,
    positionM: Float,
): ClimbReveal = when (mode) {
    SparklineMode.OFF -> ClimbReveal(null, false)
    SparklineMode.ON -> ClimbReveal(null, true)
    SparklineMode.CLIMBS -> {
        val active = climbRanges
            .mapNotNull { (startM, endM) ->
                val startElev = elevationAt(elevationPoints, startM) ?: return@mapNotNull null
                val endElev = elevationAt(elevationPoints, endM) ?: return@mapNotNull null
                if (endElev <= startElev) return@mapNotNull null
                val lengthM = (endM - startM).toDouble()
                val gradePct = if (lengthM > 0) (endElev - startElev) / lengthM * 100.0 else 0.0
                val approachM = climbApproachM(pcsClimbScore(gradePct, lengthM))
                Triple(startM, endM, approachM)
            }
            // Reveal within the approach and hold to the top; nearest finish wins on overlap.
            .filter { (startM, endM, approachM) -> positionM in (startM - approachM)..endM }
            .minByOrNull { it.second }
        if (active == null) ClimbReveal(null, false) else ClimbReveal(active.first to active.second, true)
    }
}

/**
 * Builds a monotonic function mapping a route distance in metres to a screen x-coordinate
 * in [0, widthPx]. Applies log-warp so pixels near [positionM] get more density than
 * pixels farther away: `mag(d) = 1 + K·exp(-normalised_distance · K/2)`.
 * With `logWarpK = 0f` the mapping collapses to linear (every metre gets one pixel budget).
 */
private fun buildWarpedXMapper(
    windowStart: Float,
    windowEnd: Float,
    positionM: Float,
    lookaheadM: Float,
    widthPx: Int,
    logWarpK: Float,
): (Float) -> Float {
    val steps = (lookaheadM / WARP_STEP_TARGET_M).toInt()
    val cumulative = FloatArray(steps + 1)
    val stepM = (windowEnd - windowStart) / steps
    for (step in 0 until steps) {
        val routeDistanceM = windowStart + step * stepM
        val normalisedDistanceFromDot = kotlin.math.abs(routeDistanceM - positionM) / lookaheadM
        val pixelsPerMetre = 1f + logWarpK * kotlin.math.exp(-normalisedDistanceFromDot * logWarpK * 0.5f).toFloat()
        cumulative[step + 1] = cumulative[step] + pixelsPerMetre * stepM
    }
    val totalBudget = cumulative[steps]
    return { routeDistanceM ->
        val windowFraction = ((routeDistanceM - windowStart) / (windowEnd - windowStart)).coerceIn(0f, 1f)
        val lookupIndex = windowFraction * steps
        val lowerStep = lookupIndex.toInt().coerceIn(0, steps - 1)
        val interpolationFraction = lookupIndex - lowerStep
        val budgetConsumed = cumulative[lowerStep] +
            interpolationFraction * (cumulative[lowerStep + 1] - cumulative[lowerStep])
        ((budgetConsumed / totalBudget) * widthPx).coerceIn(0f, widthPx.toFloat())
    }
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
    // Round climbEnd once so the "top of climb" point and the run-out start from the
    // same x-coordinate (previously the top point was rounded but the run-out base was not).
    val climbEnd = round1(climbStart + gainM / grade)
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
        points.add(d to round1(elev))
        d += 20f
    }
    points.add(climbEnd to topElev)

    // Flat run-out: every 50m
    d = climbEnd + 50f
    while (d < routeEnd) { points.add(round1(d) to topElev); d += 50f }
    points.add(routeEnd to topElev)

    return points
}

/**
 * Generates a synthetic elevation fixture from a sequence of segments.
 * Each segment is a (lengthM, grade) pair. Positive grade = uphill, 0 = flat.
 * Points sampled every 50m on flat, every 20m on climbs.
 */
private fun syntheticProfileFixture(
    segments: List<Pair<Float, Float>>,
    baseElev: Float = 50f,
): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()
    var dist = 0f
    var elev = baseElev

    for ((lengthM, grade) in segments) {
        val step = if (grade == 0f) 50f else 20f
        points.add(round1(dist) to round1(elev))
        var covered = step
        while (covered < lengthM) {
            dist += step
            elev += step * grade
            points.add(round1(dist) to round1(elev))
            covered += step
        }
        // Exact segment end — always close, skipping only the degenerate case where
        // the final iteration already landed exactly on the segment boundary.
        val remaining = lengthM - (covered - step)
        if (remaining > 0.01f) {
            dist += remaining
            elev += remaining * grade
            points.add(round1(dist) to round1(elev))
        }
    }
    return points
}

private fun round1(v: Float) = Math.round(v * 10f) / 10f

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

// Double ramp: 50m gain @ 10% → 2km flat → 50m gain @ 10%
internal fun doubleRampFixture(): List<Pair<Float, Float>> = syntheticProfileFixture(
    listOf(
        5_000f to 0f,       // 5km flat lead-in
        500f to 0.10f,      // 500m @ 10% → +50m
        2_000f to 0f,       // 2km flat gap
        500f to 0.10f,      // 500m @ 10% → +50m
        12_000f to 0f,      // flat run-out to 20km
    )
)

// Steep wall then gentle: 20m @ 20% → 1km flat → 20m @ 3%
internal fun wallThenGentleFixture(): List<Pair<Float, Float>> = syntheticProfileFixture(
    listOf(
        5_000f to 0f,       // 5km flat lead-in
        100f to 0.20f,      // 100m @ 20% → +20m
        1_000f to 0f,       // 1km flat gap
        667f to 0.03f,      // 667m @ 3% → +20m
        13_233f to 0f,      // flat run-out
    )
)

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
    "2× 50m @ 10%, 2km gap" to ::doubleRampFixture,
    "20m wall→flat→20m gentle" to ::wallThenGentleFixture,
)

/**
 * Mock climb ranges for [rvvElevationFixture] — used in debug/preview to verify the
 * blue climb overlay without needing a live route. Both ranges are clearly uphill in
 * the polyline so the renderer's polyline-verified uphill check accepts them.
 */
internal fun rvvClimbsFixture(): List<Pair<Float, Float>> = listOf(
    1100f to 4340f,   // Muur — climb begins at the ~10 m trough, peaks at ~112 m
    6300f to 6875f,   // second climb — foot at the ~33 m dip, peaks at ~73 m
)

/**
 * Mock POI distances for [rvvElevationFixture] — used in debug/preview to verify POI
 * marker rendering without needing a live route. Includes a marker at the top of the
 * Muur climb plus a couple of others so we can see clustering and spacing behaviour.
 */
internal fun rvvPoisFixture(): List<Float> = listOf(
    4340f,    // top of the Muur (highest point in the fixture)
    6875f,    // top of the second climb
    14000f,   // mid-route plateau
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

/**
 * Opening climb of the Col de Rates (Costa Blanca), from a real GPX trace, used by the
 * debug sweep when the HUD sparkline is in Climbs mode. A ~3.8 km descent lead-in, the
 * ~9.8 km / 649 m sustained climb (avg ~6.6%, PCS ≈ 107 → large approach tier), then a
 * ~3.8 km descent lead-out — so the climb-only reveal both appears and disappears as the
 * sweep rolls past. Downsampled to ~100 m spacing to match [rvvElevationFixture].
 */
internal fun colDeRatesElevationFixture(): List<Pair<Float, Float>> = listOf(
    0.0f to 350.0f, 108.4f to 342.0f, 201.0f to 335.0f, 310.8f to 329.0f, 409.4f to 325.0f, 501.4f to 321.0f,
    600.8f to 317.0f, 706.0f to 313.0f, 808.1f to 310.0f, 906.6f to 308.0f, 1001.3f to 305.0f, 1110.4f to 299.0f,
    1206.2f to 296.0f, 1311.9f to 294.0f, 1407.7f to 293.0f, 1505.6f to 296.0f, 1605.7f to 299.0f, 1700.7f to 299.0f,
    1800.6f to 302.0f, 1900.4f to 304.0f, 2002.3f to 306.0f, 2107.8f to 306.0f, 2207.1f to 304.0f, 2310.8f to 301.0f,
    2404.1f to 298.0f, 2508.8f to 296.0f, 2609.8f to 294.0f, 2701.8f to 292.0f, 2811.2f to 288.0f, 2918.0f to 286.0f,
    3010.9f to 285.0f, 3115.5f to 282.0f, 3204.9f to 282.0f, 3307.2f to 281.0f, 3402.8f to 279.0f, 3502.8f to 277.0f,
    3606.9f to 276.0f, 3700.9f to 273.0f, 3801.2f to 271.0f, 3907.1f to 271.0f, 4002.4f to 274.0f, 4104.3f to 278.0f,
    4201.0f to 284.0f, 4302.6f to 289.0f, 4403.2f to 293.0f, 4502.4f to 296.0f, 4602.2f to 299.0f, 4700.5f to 303.0f,
    4807.0f to 306.0f, 4905.2f to 311.0f, 5001.9f to 316.0f, 5100.6f to 322.0f, 5203.0f to 328.0f, 5301.5f to 332.0f,
    5400.1f to 338.0f, 5503.9f to 344.0f, 5600.3f to 349.0f, 5700.1f to 353.0f, 5803.2f to 358.0f, 5900.4f to 365.0f,
    6000.9f to 372.0f, 6101.9f to 376.0f, 6202.7f to 382.0f, 6301.2f to 388.0f, 6403.7f to 393.0f, 6502.4f to 396.0f,
    6603.9f to 403.0f, 6706.0f to 408.0f, 6804.0f to 413.0f, 6900.9f to 418.0f, 7000.4f to 424.0f, 7103.6f to 430.0f,
    7203.3f to 435.0f, 7303.7f to 441.0f, 7402.3f to 445.0f, 7503.4f to 450.0f, 7603.6f to 454.0f, 7700.8f to 459.0f,
    7802.9f to 464.0f, 7902.8f to 471.0f, 8003.0f to 479.0f, 8102.3f to 485.0f, 8202.9f to 492.0f, 8301.9f to 499.0f,
    8402.8f to 508.0f, 8502.2f to 514.0f, 8603.2f to 522.0f, 8702.1f to 529.0f, 8802.8f to 538.0f, 8902.3f to 544.0f,
    9001.0f to 549.0f, 9101.6f to 554.0f, 9201.1f to 559.0f, 9303.7f to 565.0f, 9403.8f to 570.0f, 9501.0f to 575.0f,
    9603.9f to 579.0f, 9700.3f to 584.0f, 9802.2f to 587.0f, 9900.4f to 589.0f, 10000.3f to 596.0f, 10100.8f to 602.0f,
    10203.2f to 610.0f, 10300.7f to 615.0f, 10403.6f to 621.0f, 10502.3f to 630.0f, 10604.1f to 634.0f, 10700.4f to 638.0f,
    10801.6f to 638.0f, 10900.8f to 639.0f, 11001.1f to 651.0f, 11100.1f to 662.0f, 11200.3f to 675.0f, 11300.2f to 686.0f,
    11400.6f to 695.0f, 11500.4f to 707.0f, 11601.1f to 717.0f, 11703.6f to 726.0f, 11801.5f to 740.0f, 11900.8f to 753.0f,
    12002.1f to 767.0f, 12100.7f to 776.0f, 12201.9f to 787.0f, 12301.4f to 802.0f, 12401.5f to 816.0f, 12501.7f to 828.0f,
    12601.2f to 839.0f, 12701.9f to 850.0f, 12800.8f to 862.0f, 12900.4f to 874.0f, 13004.7f to 879.0f, 13106.0f to 884.0f,
    13202.1f to 887.0f, 13301.7f to 890.0f, 13400.7f to 899.0f, 13500.4f to 908.0f, 13600.1f to 920.0f, 13700.4f to 905.0f,
    13802.4f to 899.0f, 13900.7f to 909.0f, 14003.7f to 913.0f, 14100.5f to 902.0f, 14203.0f to 892.0f, 14305.2f to 888.0f,
    14400.4f to 885.0f, 14503.2f to 878.0f, 14600.9f to 874.0f, 14702.8f to 865.0f, 14805.3f to 854.0f, 14901.1f to 844.0f,
    15007.1f to 829.0f, 15108.2f to 816.0f, 15203.4f to 804.0f, 15302.5f to 788.0f, 15403.8f to 777.0f, 15502.4f to 767.0f,
    15608.7f to 753.0f, 15703.1f to 741.0f, 15802.1f to 725.0f, 15902.9f to 717.0f, 16004.3f to 706.0f, 16101.6f to 694.0f,
    16205.0f to 684.0f, 16301.2f to 672.0f, 16402.6f to 661.0f, 16509.4f to 645.0f, 16600.1f to 636.0f, 16703.1f to 635.0f,
    16804.5f to 635.0f, 16903.2f to 632.0f, 17002.0f to 626.0f, 17105.0f to 621.0f, 17205.9f to 616.0f, 17305.1f to 608.0f,
    17411.8f to 600.0f,
)

/** Climb range (foot → summit) for [colDeRatesElevationFixture]. */
internal fun colDeRatesClimbsFixture(): List<Pair<Float, Float>> = listOf(3801f to 13600f)

/** Summit POI for [colDeRatesElevationFixture]. */
internal fun colDeRatesPoisFixture(): List<Float> = listOf(13600f)
