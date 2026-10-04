package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import kotlin.math.PI

/**
 * Synthetic routes on a local metre grid at the equator: x runs east, y north, and one metre is 1 /
 * [M_PER_DEG] degrees on both axes, matching the equirectangular distance the code uses.
 */
internal object RouteFixtures {
    const val M_PER_DEG = 6_371_000.0 * PI / 180.0

    fun point(xM: Double, yM: Double) = LatLng(lat = yM / M_PER_DEG, lng = xM / M_PER_DEG)

    /** Vertices every [stepM] from x = 0 to x = [lengthM] along y = [yM]. */
    fun straight(lengthM: Double, stepM: Double, yM: Double = 0.0): List<LatLng> {
        val n = (lengthM / stepM).toInt()
        return (0..n).map { point(it * stepM, yM) }
    }

    /** Out along x to [legM] and back the same way, one vertex at the turn. Length 2 x [legM]. */
    fun outAndBack(legM: Double, stepM: Double): List<LatLng> {
        val out = straight(legM, stepM)
        return out + out.dropLast(1).asReversed()
    }

    /**
     * A square lap of perimeter [lapM] with vertices every [stepM], ridden [count] times. Every lap
     * shares every vertex. [lapM] / 4 must be a multiple of [stepM].
     */
    fun laps(lapM: Double, count: Int, stepM: Double): List<LatLng> {
        val side = lapM / 4.0
        val perSide = (side / stepM).toInt()
        val lap = mutableListOf<LatLng>()
        for (i in 0 until perSide) lap += point(i * stepM, 0.0)
        for (i in 0 until perSide) lap += point(side, i * stepM)
        for (i in 0 until perSide) lap += point(side - i * stepM, side)
        for (i in 0 until perSide) lap += point(0.0, side - i * stepM)
        val out = mutableListOf<LatLng>()
        repeat(count) { out += lap }
        out += lap.first()
        return out
    }

    /** Two legs [gapM] apart: out along y = 0, back along y = [gapM]. */
    fun switchback(legM: Double, gapM: Double, stepM: Double): List<LatLng> =
        straight(legM, stepM) + straight(legM, stepM, yM = gapM).asReversed()

    fun encoded(points: List<LatLng>): String = encodeGpsPolyline(points)

    /** A flat profile over [lengthM]: one colour run for the whole route. */
    fun flatElevation(lengthM: Double): String =
        encodeElevation(listOf(0f to 100f, lengthM.toFloat() to 100f))

    /** Elevation polyline as the Karoo encodes it: precision 1, distance then elevation. */
    fun encodeElevation(points: List<Pair<Float, Float>>): String {
        val sb = StringBuilder()
        var prevDist = 0L
        var prevElev = 0L
        for ((d, e) in points) {
            val dInt = Math.round(d * 10.0).toLong()
            val eInt = Math.round(e * 10.0).toLong()
            encodeSigned(dInt - prevDist, sb)
            encodeSigned(eInt - prevElev, sb)
            prevDist = dInt
            prevElev = eInt
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
}
