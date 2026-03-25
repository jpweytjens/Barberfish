package com.jpweytjens.barberfish.datatype.shared

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
