package com.jpweytjens.barberfish.datatype.shared

// Pure-Kotlin elevation-polyline geometry primitives shared by the HUD sparkline
// and the climber map overlay. No Android imports.
//
// Elevation polyline: Google Encoded Polyline, precision=1 (divisor=10),
// lat = cumulative distance in metres, lng = elevation in metres.
// Confirmed by Task 5 spike and consistent with karoo-routegraph source
// (LineString.fromPolyline(it, 1)).

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
    val heap = java.util.PriorityQueue<HeapEntry>(compareBy { it.area })
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

