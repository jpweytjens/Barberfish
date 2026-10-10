package com.jpweytjens.barberfish.datatype.shared

import de.timklge.headwind.client.HeadwindForecastPoint
import de.timklge.headwind.client.HeadwindSnapshot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Wind as forecasts state it: where it blows from (degrees, 0 = north) and its speed in m/s. */
internal data class Wind(val fromDeg: Double, val speedMs: Double)

/** How often the blend moves on with the clock; Headwind recomputes its own fields as often. */
private const val FORECAST_CLOCK_MS = 60_000L

/** The time in epoch seconds, now and then once a minute, for [windAt]. */
internal fun forecastClock(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis() / 1000)
        delay(FORECAST_CLOCK_MS)
    }
}

/** Headwind downloads hourly; two hours without a download means it has lost the feed. */
private const val FORECAST_STALE_AFTER_S = 2 * 3600L

/**
 * Whether a forecast last downloaded at [fetchedAt] is too old to vouch for at [now], both in epoch
 * seconds. An unknown download time counts as stale. The Wind field and the map sock both grey on
 * it.
 */
internal fun isForecastStale(fetchedAt: Long?, now: Long): Boolean =
    fetchedAt == null || now - fetchedAt > FORECAST_STALE_AFTER_S

/** [windAt] over a whole snapshot: null without one, or before Headwind has a forecast. */
internal fun HeadwindSnapshot?.windAt(position: LatLng?, epochSeconds: Long): Wind? =
    this?.forecast?.let { windAt(it, position, epochSeconds) }

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
