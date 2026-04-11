package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

internal data class LatLng(val lat: Double, val lng: Double)

/** Earth radius in metres (mean). */
private const val EARTH_RADIUS_M = 6_371_000.0

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
private fun interpolateAt(points: List<LatLng>, cumDist: DoubleArray, distanceM: Double): LatLng {
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
