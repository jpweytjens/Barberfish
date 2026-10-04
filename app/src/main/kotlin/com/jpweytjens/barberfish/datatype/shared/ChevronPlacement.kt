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

// Bearing chord half-width used when no window is configured, so callers that leave
// windowHalfM at zero still get a forward-pointing chevron.
private const val DEFAULT_BEARING_HALF_M = 10.0

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
