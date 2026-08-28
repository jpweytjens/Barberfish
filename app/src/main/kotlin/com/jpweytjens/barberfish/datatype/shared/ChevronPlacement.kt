package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One chevron position along the route, before any grade colouring. [distanceM] is route distance
 * from the start; [bearingDeg] is the direction the chevron points, clockwise from north.
 */
internal data class ChevronPlacement(
    val distanceM: Double,
    val lat: Double,
    val lng: Double,
    val bearingDeg: Float,
)

/**
 * Zoom-derived geometry for one placement pass. All distances are ground metres at the zoom level
 * being rendered.
 *
 * [headingThresholdDeg] left at its default 0.0 means the post-loop acceptance in [placeChevrons]
 * can never fire (no spread is below 0.0), so a caller that sets [windowHalfM] without also setting
 * [headingThresholdDeg] gets near-total suppression on any curving route.
 */
internal data class ChevronTuning(
    val spacingM: Double,
    val windowHalfM: Double,
    val collisionRadiusM: Double,
    val headingThresholdDeg: Double,
)

// Candidate offsets in spacings, tried in this order around each cursor position, so a
// chevron slides off a bend rather than vanishing.
private val CANDIDATE_OFFSETS = doubleArrayOf(0.0, -0.25, 0.25, -0.125, 0.125, -0.375, 0.375)

// A candidate whose local bearing spread is below this is taken at once, without trying
// the remaining offsets.
private const val EARLY_ACCEPT_SPREAD_DEG = 10.0

// Bearing chord half-width used when no window is configured, so callers that leave
// windowHalfM at zero still get a forward-pointing chevron.
private const val DEFAULT_BEARING_HALF_M = 10.0

/**
 * Places direction chevrons along a route.
 *
 * A cursor starts half a spacing into the route and walks to its end. At each step seven candidate
 * positions are tried in [CANDIDATE_OFFSETS] order. A candidate is skipped when it falls within
 * [ChevronTuning.collisionRadiusM] of an already-placed chevron. Otherwise its local bearing spread
 * decides: below [EARLY_ACCEPT_SPREAD_DEG] it is taken immediately, otherwise it is remembered only
 * if it is the straightest so far. After the seven, the straightest candidate is taken when its
 * spread is below [ChevronTuning.headingThresholdDeg], and nothing is placed when it is not.
 *
 * On a placement the cursor re-phases to that position plus one spacing, so the cadence is measured
 * from what was actually placed rather than from a fixed grid. On a rejection the cursor advances
 * by one spacing and the grid phase is preserved.
 *
 * Placement knows nothing about grade runs. Callers colour and filter the result.
 */
internal fun placeChevrons(
    gps: List<LatLng>,
    cumDist: DoubleArray,
    tuning: ChevronTuning,
): List<ChevronPlacement> {
    if (gps.size < 2 || cumDist.size != gps.size || tuning.spacingM <= 0.0) return emptyList()
    val totalM = cumDist.last()
    if (totalM <= 0.0) return emptyList()
    val placed = mutableListOf<ChevronPlacement>()
    var cursorM = tuning.spacingM * 0.5
    while (cursorM < totalM) {
        var chosenM = -1.0
        var bestM = -1.0
        var bestSpread = 360.0
        for (offset in CANDIDATE_OFFSETS) {
            // Clamped rather than skipped, so a candidate running past either end of the
            // route collapses onto the first or last vertex instead of being dropped.
            val candidateM = (cursorM + offset * tuning.spacingM).coerceIn(0.0, totalM)
            if (collides(placed, gps, cumDist, candidateM, tuning.collisionRadiusM)) continue
            val spread = bearingSpreadInWindow(gps, cumDist, candidateM, tuning.windowHalfM)
            if (spread < EARLY_ACCEPT_SPREAD_DEG) {
                chosenM = candidateM
                break
            }
            if (spread < bestSpread) {
                bestSpread = spread
                bestM = candidateM
            }
        }
        if (chosenM < 0.0 && bestM >= 0.0 && bestSpread < tuning.headingThresholdDeg) {
            chosenM = bestM
        }
        if (chosenM >= 0.0) {
            placed += placementAt(gps, cumDist, chosenM, tuning.windowHalfM)
            cursorM = chosenM + tuning.spacingM
        } else {
            cursorM += tuning.spacingM
        }
    }
    return placed
}

private fun collides(
    placed: List<ChevronPlacement>,
    gps: List<LatLng>,
    cumDist: DoubleArray,
    candidateM: Double,
    radiusM: Double,
): Boolean {
    if (radiusM <= 0.0 || placed.isEmpty()) return false
    val here = interpolateAt(gps, cumDist, candidateM)
    // Checked against every placed chevron, not just the previous one: where the route
    // switchbacks or loops, two positions far apart in route distance can sit on top of
    // each other on screen.
    return placed.any { latLngDistanceM(LatLng(it.lat, it.lng), here) < radiusM }
}

private fun placementAt(
    gps: List<LatLng>,
    cumDist: DoubleArray,
    distanceM: Double,
    windowHalfM: Double,
): ChevronPlacement {
    val totalM = cumDist.last()
    val halfM = if (windowHalfM > 0.0) windowHalfM else DEFAULT_BEARING_HALF_M
    val here = interpolateAt(gps, cumDist, distanceM)
    val back = interpolateAt(gps, cumDist, (distanceM - halfM).coerceAtLeast(0.0))
    val ahead = interpolateAt(gps, cumDist, (distanceM + halfM).coerceAtMost(totalM))
    // Chord across the window rather than a short forward tangent, so the rotation follows
    // the road through a bend instead of whichever vertex happens to be next.
    val bearing = if (back == ahead) 0f else bearingDeg(back, ahead)
    return ChevronPlacement(
        distanceM = distanceM,
        lat = here.lat,
        lng = here.lng,
        bearingDeg = bearing,
    )
}

/**
 * Spread in degrees between the largest and smallest edge bearing over the route segments spanning
 * `[centerM - halfM, centerM + halfM]`. Folded to `[0, 180]` so a span crossing the 0/360
 * wraparound reports the shorter arc. A window inside a single segment reports 0.
 */
private fun bearingSpreadInWindow(
    gps: List<LatLng>,
    cumDist: DoubleArray,
    centerM: Double,
    halfM: Double,
): Double {
    if (halfM <= 0.0) return 0.0
    val lastEdge = gps.size - 2
    if (lastEdge < 0) return 0.0
    val from = segmentIndexAt(cumDist, centerM - halfM).coerceIn(0, lastEdge)
    val to = segmentIndexAt(cumDist, centerM + halfM).coerceIn(0, lastEdge)
    var minB = Double.MAX_VALUE
    var maxB = -Double.MAX_VALUE
    for (i in from..to) {
        val b = bearingDeg(gps[i], gps[i + 1]).toDouble()
        if (b < minB) minB = b
        if (b > maxB) maxB = b
    }
    val raw = maxB - minB
    return if (raw > 180.0) abs(raw - 360.0) else raw
}

/** Index of the route segment containing [distanceM], clamped to the route ends. */
private fun segmentIndexAt(cumDist: DoubleArray, distanceM: Double): Int {
    val last = cumDist.size - 1
    if (last <= 0 || distanceM <= 0.0) return 0
    if (distanceM >= cumDist[last]) return last
    var lo = 0
    var hi = last
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (cumDist[mid] <= distanceM && distanceM < cumDist[mid + 1]) return mid
        if (distanceM < cumDist[mid]) hi = mid else lo = mid + 1
    }
    return last
}

/**
 * Initial bearing in degrees from [from] to [to], measured clockwise from North (0 = N, 90 = E, 180
 * = S, 270 = W). Standard spherical forward-azimuth formula.
 */
private fun bearingDeg(from: LatLng, to: LatLng): Float {
    val lat1 = from.lat * PI / 180.0
    val lat2 = to.lat * PI / 180.0
    val dLon = (to.lng - from.lng) * PI / 180.0
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val deg = atan2(y, x) * 180.0 / PI
    return ((deg + 360.0) % 360.0).toFloat()
}

/**
 * Blended placement intensity in `[0, 1]`. [gradePct] and [changePctPerM] are normalised against
 * their "full" values and mixed by [alpha]: 0 follows grade magnitude alone, 1 follows the rate of
 * grade change alone. Colour already carries grade, so pushing [alpha] up makes the cadence carry
 * something colour does not — where the road is changing.
 */
internal fun chevronIntensity(
    gradePct: Double,
    changePctPerM: Double,
    alpha: Double,
    gradeFullPct: Double,
    changeFullPctPerM: Double,
): Double {
    val gMag = if (gradeFullPct > 0.0) (abs(gradePct) / gradeFullPct).coerceIn(0.0, 1.0) else 0.0
    val cMag =
        if (changeFullPctPerM > 0.0) (abs(changePctPerM) / changeFullPctPerM).coerceIn(0.0, 1.0)
        else 0.0
    return ((1.0 - alpha) * gMag + alpha * cMag).coerceIn(0.0, 1.0)
}

/** Metres between consecutive marks: the sparse ceiling at intensity 0, the dense floor at 1. */
internal fun chevronSpacingM(intensity: Double, spacingMaxM: Double, spacingMinM: Double): Double =
    spacingMaxM + (spacingMinM - spacingMaxM) * intensity.coerceIn(0.0, 1.0)

/**
 * Places chevrons along the route at a spacing that varies with local [gradeAtM] and [changeAtM]. A
 * cursor walks the route; at each step the local intensity sets the distance to the next step. A
 * candidate within [collisionRadiusM] of a placed mark is skipped (the cursor still advances), so
 * switchbacks never stack marks. Placement knows nothing about grade runs — callers colour and
 * filter the result.
 */
internal fun placeChevronsByCadence(
    gps: List<LatLng>,
    cumDist: DoubleArray,
    gradeAtM: (Double) -> Double,
    changeAtM: (Double) -> Double,
    alpha: Double,
    gradeFullPct: Double,
    changeFullPctPerM: Double,
    spacingMaxM: Double,
    spacingMinM: Double,
    collisionRadiusM: Double,
    windowHalfM: Double,
): List<ChevronPlacement> {
    if (gps.size < 2 || cumDist.size != gps.size) return emptyList()
    val totalM = cumDist.last()
    if (totalM <= 0.0 || spacingMinM <= 0.0) return emptyList()
    fun spacingAt(d: Double): Double =
        chevronSpacingM(
            chevronIntensity(gradeAtM(d), changeAtM(d), alpha, gradeFullPct, changeFullPctPerM),
            spacingMaxM,
            spacingMinM,
        )
    val placed = mutableListOf<ChevronPlacement>()
    // Start half a (local) spacing in, so the first mark is not pinned to the route origin.
    var cursorM = spacingAt(0.0) * 0.5
    while (cursorM < totalM) {
        if (!collides(placed, gps, cumDist, cursorM, collisionRadiusM)) {
            placed += placementAt(gps, cumDist, cursorM, windowHalfM)
        }
        cursorM += spacingAt(cursorM)
    }
    return placed
}

// --- Zoom-derived tuning ---------------------------------------------------------
//
// Spacing and collision radius are fixed pixel counts scaled to ground metres by the map's
// ground resolution, so the on-screen rhythm holds as the map scales.

/**
 * Spacing in metres between consecutive direction chevrons, tuned to sit close to the native arrow
 * cadence at typical zooms. On a Karoo 3 (xdpi 320.842) at zoom 15 and latitude 44 this is about
 * 220 m, roughly a quarter of the screen width.
 */
internal fun nativeChevronSpacingM(xdpi: Float, lat: Double, zoomLevel: Double): Double =
    xdpi * 0.4 * groundResolution(lat, zoomLevel)

/**
 * Half-width in metres of the neighbourhood around a candidate position used both to measure local
 * bearing spread and as the chord for the chevron's rotation. Half the collision radius, so `xdpi *
 * 0.05 * groundResolution`.
 */
internal fun nativeChevronWindowHalfM(xdpi: Float, lat: Double, zoomLevel: Double): Double =
    xdpi * 0.05 * groundResolution(lat, zoomLevel)

/**
 * Ground length in metres of a chevron icon [heightDp] tall at display [density] and the given
 * [zoomLevel]. Two chevrons closer than this overlap on screen, so it doubles as the collision
 * radius. Tracks our own icon, 17 dp tall at density 1.875.
 */
internal fun chevronIconLengthM(
    heightDp: Float,
    density: Float,
    lat: Double,
    zoomLevel: Double,
): Double = heightDp * density * groundResolution(lat, zoomLevel)

/**
 * Maximum bearing spread in degrees allowed inside the local window for a chevron to be placed.
 * Spreads at or above this suppress the position because the route is curving too sharply for a
 * single rotation to indicate direction faithfully.
 */
internal fun nativeChevronHeadingThresholdDeg(zoomLevel: Double): Double =
    when {
        zoomLevel > 12.0 -> 30.0
        zoomLevel > 10.0 -> 45.0
        else -> 60.0
    }
