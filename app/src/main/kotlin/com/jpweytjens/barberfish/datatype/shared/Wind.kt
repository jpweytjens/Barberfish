package com.jpweytjens.barberfish.datatype.shared

import de.timklge.headwind.client.HeadwindForecastPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Wind as forecasts state it: where it blows from (degrees, 0 = north) and its speed in m/s. */
internal data class Wind(val fromDeg: Double, val speedMs: Double)

/**
 * The Headwind forecast's wind at [position] and [epochSeconds]. Each point's wind is first blended
 * linearly between the hourly entries around the time (from its current reading before the first
 * hour, held after the last); then the two points nearest the rider are blended by inverse
 * distance, the nearer weighted d2 / (d1 + d2). Headwind's own fields blend the same way but treat
 * speed and direction as separate numbers; here the wind is blended as a vector, so opposing winds
 * cancel instead of turning into a crosswind. Without a position the first point stands in.
 */
internal fun windAt(
    forecast: List<HeadwindForecastPoint>,
    position: LatLng?,
    epochSeconds: Long,
): Wind? {
    // Without a position every distance is 0, so the first point stands in with full weight.
    val ranked =
        forecast
            .map {
                it to (position?.let { p -> latLngDistanceM(p, LatLng(it.lat, it.lon)) } ?: 0.0)
            }
            .sortedBy { (_, distanceM) -> distanceM }
    val (nearest, nearestM) = ranked.firstOrNull() ?: return null
    val (second, secondM) = ranked.getOrNull(1) ?: ranked.first()
    val totalM = nearestM + secondM
    val towardSecond = if (totalM > 0.0) nearestM / totalM else 0.0
    return lerp(nearest.windVectorAt(epochSeconds), second.windVectorAt(epochSeconds), towardSecond)
        .toWind()
}

/** A wind as east and north components of its "from" direction, scaled by speed; m/s. */
private data class WindVector(val east: Double, val north: Double)

private fun HeadwindForecastPoint.windVectorAt(epochSeconds: Long): WindVector {
    val start = hourly.lastOrNull { it.time < epochSeconds } ?: current
    val end = hourly.firstOrNull { it.time >= epochSeconds } ?: start
    val spanS = end.time - start.time
    val fraction =
        if (spanS > 0) ((epochSeconds - start.time).toDouble() / spanS).coerceIn(0.0, 1.0) else 0.0
    return lerp(
        Wind(start.windDirection, start.windSpeed).toVector(),
        Wind(end.windDirection, end.windSpeed).toVector(),
        fraction,
    )
}

private fun lerp(a: WindVector, b: WindVector, fraction: Double) =
    WindVector(
        a.east + (b.east - a.east) * fraction,
        a.north + (b.north - a.north) * fraction,
    )

private fun Wind.toVector(): WindVector {
    val rad = fromDeg * PI / 180.0
    return WindVector(speedMs * sin(rad), speedMs * cos(rad))
}

private fun WindVector.toWind(): Wind {
    val fromDeg = atan2(east, north) * 180.0 / PI
    return Wind((fromDeg % 360.0 + 360.0) % 360.0, hypot(east, north))
}
