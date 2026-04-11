package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

internal data class LatLng(val lat: Double, val lng: Double)

/** Earth radius in metres (mean). */
private const val EARTH_RADIUS_M = 6_371_000.0

// --- VTM/oscim viewport geometry (reverse-engineered from Karoo rideapp) --------
//
// The rideapp uses VTM (org.oscim) with Tile.SIZE = 512.
// groundResolution (metres per pixel) = cos(lat) * 40_075_016.686 / (512 * 2^zoom)
// Karoo 3 display: 480 × 800 px.

private const val VTM_TILE_SIZE = 512
private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686
private const val KAROO3_SCREEN_WIDTH_PX = 480
private const val KAROO3_SCREEN_HEIGHT_PX = 800

/**
 * Metres per pixel at [lat] degrees latitude and the given VTM [zoomLevel].
 */
private fun groundResolution(lat: Double, zoomLevel: Double): Double {
    val scale = 2.0.pow(zoomLevel)
    return cos(lat * PI / 180.0) * EARTH_CIRCUMFERENCE_M / (VTM_TILE_SIZE * scale)
}

/**
 * Computes an axis-aligned viewport bounding box centred on [lat]/[lng] at a given
 * Karoo map [zoomLevel] (range 8.0–18.0, see `OnMapZoomLevel`). Uses the VTM ground
 * resolution and the Karoo 3 screen size for accurate bounds.
 *
 * The bounds are widened by [paddingFactor] on each side so chevrons near the edge
 * don't pop in/out on small pans.
 */
internal fun mapViewportBounds(
    lat: Double,
    lng: Double,
    zoomLevel: Double,
    paddingFactor: Double = 1.5,
): LatLngBounds {
    val mpp = groundResolution(lat, zoomLevel)
    // Half-extent in metres, then convert to degrees.
    val halfWidthM = mpp * KAROO3_SCREEN_WIDTH_PX / 2.0 * paddingFactor
    val halfHeightM = mpp * KAROO3_SCREEN_HEIGHT_PX / 2.0 * paddingFactor
    // 1° lat ≈ 111_320 m; 1° lng ≈ 111_320 * cos(lat)
    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLng = 111_320.0 * cos(lat * PI / 180.0)
    return LatLngBounds(
        minLat = lat - halfHeightM / metersPerDegreeLat,
        maxLat = lat + halfHeightM / metersPerDegreeLat,
        minLng = lng - halfWidthM / metersPerDegreeLng,
        maxLng = lng + halfWidthM / metersPerDegreeLng,
    )
}

/**
 * Map diagonal in metres for the Karoo 3 display at [lat]/[lng] and the given VTM
 * [zoomLevel]. Uses the exact ground resolution to compute the pixel-to-metre mapping,
 * then Pythagoras on the screen dimensions.
 */
internal fun mapDiagonalMeters(lat: Double, lng: Double, zoomLevel: Double): Double {
    val mpp = groundResolution(lat, zoomLevel)
    return mpp * sqrt(
        (KAROO3_SCREEN_WIDTH_PX.toDouble()).pow(2) +
            (KAROO3_SCREEN_HEIGHT_PX.toDouble()).pow(2),
    )
}

/**
 * Decodes a Google encoded polyline at the given precision (default 5 for GPS).
 * Returns an empty list on blank input.
 */
internal fun decodeGpsPolyline(encoded: String, precision: Int = 5): List<LatLng> {
    if (encoded.isBlank()) return emptyList()
    val factor = 10.0.pow(precision)
    val result = mutableListOf<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var b: Int
        var r = 0
        do {
            b = encoded[index++].code - 63
            r = r or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (r and 1 != 0) (r shr 1).inv() else r shr 1
        shift = 0
        r = 0
        do {
            b = encoded[index++].code - 63
            r = r or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (r and 1 != 0) (r shr 1).inv() else r shr 1
        result.add(LatLng(lat / factor, lng / factor))
    }
    return result
}

/**
 * Encodes a list of points into a Google encoded polyline at the given precision.
 * Returns an empty string if [points] is empty.
 */
internal fun encodeGpsPolyline(points: List<LatLng>, precision: Int = 5): String {
    if (points.isEmpty()) return ""
    val factor = 10.0.pow(precision)
    val sb = StringBuilder()
    var prevLat = 0L
    var prevLng = 0L
    for (p in points) {
        val lat = Math.round(p.lat * factor)
        val lng = Math.round(p.lng * factor)
        encodeSigned(lat - prevLat, sb)
        encodeSigned(lng - prevLng, sb)
        prevLat = lat
        prevLng = lng
    }
    return sb.toString()
}

private fun encodeSigned(v: Long, sb: StringBuilder) {
    var value = if (v < 0) (v shl 1).inv() else (v shl 1)
    while (value >= 0x20) {
        sb.append(((0x20 or (value and 0x1f).toInt()) + 63).toChar())
        value = value shr 5
    }
    sb.append((value.toInt() + 63).toChar())
}

/**
 * Cumulative distance along [points] in metres using an equirectangular approximation
 * (acceptable for sub-kilometre segments at temperate latitudes; Barberfish targets
 * road cycling, not polar exploration). Returns a DoubleArray where `result[0] == 0.0`
 * and `result[i] = result[i-1] + distance(points[i-1], points[i])`.
 */
internal fun cumulativeDistancesM(points: List<LatLng>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val meanLatRad = (a.lat + b.lat) * 0.5 * (PI / 180.0)
        val dLatRad = (b.lat - a.lat) * (PI / 180.0)
        val dLngRad = (b.lng - a.lng) * (PI / 180.0)
        val x = dLngRad * cos(meanLatRad)
        out[i] = out[i - 1] + EARTH_RADIUS_M * sqrt(x * x + dLatRad * dLatRad)
    }
    return out
}

/**
 * Returns the sub-polyline covering [startM, endM] (clamped to the route bounds),
 * linearly interpolating lat/lng at each endpoint when the distance falls between
 * two vertices. The result always includes both endpoints in order and has size ≥ 2
 * when `endM > startM`.
 *
 * If `startM >= endM` after clamping, returns an empty list.
 * Linear lat/lng interpolation is fine for the visual overlay — elevation polyline
 * spacing is ~80-100 m which is far below the scale where spherical geometry matters.
 */
internal fun extractSubPolyline(
    points: List<LatLng>,
    cumDist: DoubleArray,
    startM: Double,
    endM: Double,
): List<LatLng> {
    if (points.size < 2 || cumDist.isEmpty()) return emptyList()
    val total = cumDist.last()
    val s = startM.coerceIn(0.0, total)
    val e = endM.coerceIn(0.0, total)
    if (e <= s) return emptyList()
    val out = mutableListOf<LatLng>()
    out += interpolateAt(points, cumDist, s)
    for (i in points.indices) {
        val d = cumDist[i]
        if (d > s && d < e) out += points[i]
    }
    out += interpolateAt(points, cumDist, e)
    return out
}

/**
 * Returns the LatLng at distance [distanceM] along [points]. If it falls exactly
 * on a vertex, returns that vertex; otherwise linearly interpolates between the
 * two bracketing vertices. Caller must ensure `0 ≤ distanceM ≤ cumDist.last()`.
 */
internal fun interpolateAt(points: List<LatLng>, cumDist: DoubleArray, distanceM: Double): LatLng {
    if (distanceM <= 0.0) return points.first()
    if (distanceM >= cumDist.last()) return points.last()
    // cumDist is strictly non-decreasing; find the first index i with cumDist[i] >= distanceM.
    var i = 1
    while (i < cumDist.size && cumDist[i] < distanceM) i++
    val d0 = cumDist[i - 1]
    val d1 = cumDist[i]
    if (d1 == d0) return points[i]
    val t = (distanceM - d0) / (d1 - d0)
    val a = points[i - 1]
    val b = points[i]
    return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
}
