package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.ceil

/**
 * Monotonic rider progress along the active route, bucketed so consumers act once per [bucketM] of
 * road instead of once per GPS tick.
 *
 * Progress is `routeDistance - DISTANCE_TO_DESTINATION`: ride-order metres on the GPS axis, the
 * same axis chevron placements are measured on. The latch only ever advances, so GPS noise and
 * riding backward never lower it. Off route the distance field is rejoin-relative, so those samples
 * are ignored and the latch holds its last on-route value. [trackRoute] resets the latch when the
 * route identity changes (new polyline or reversal).
 */
internal class GradeMapProgress(private val bucketM: Double = 50.0) {
    private var routeKey: Long? = null
    private var bucket = 0

    val progressM: Double
        get() = bucket * bucketM

    /** Pins the latch to a route; a changed key resets progress to zero. */
    fun trackRoute(key: Long) {
        if (routeKey != key) {
            routeKey = key
            bucket = 0
        }
    }

    /** Forgets the tracked route and progress: navigation was cleared. */
    fun clear() {
        routeKey = null
        bucket = 0
    }

    /** True when the bucketed progress advanced, i.e. the rider crossed a bucket edge. */
    fun advance(
        distanceToDestinationM: Double?,
        onRoute: Boolean,
        routeDistanceM: Double,
    ): Boolean {
        if (
            routeKey == null || !onRoute || distanceToDestinationM == null || routeDistanceM <= 0.0
        ) {
            return false
        }
        val raw = (routeDistanceM - distanceToDestinationM).coerceIn(0.0, routeDistanceM)
        // Mid-route the floor keeps progress from ever running ahead of the rider; on
        // arrival (raw clamped to the route end) the ceiling covers the final partial
        // bucket, which the floor alone could never reach.
        val newBucket =
            if (raw >= routeDistanceM) ceil(routeDistanceM / bucketM).toInt()
            else (raw / bucketM).toInt()
        if (newBucket <= bucket) return false
        bucket = newBucket
        return true
    }
}

/** Route identity for [GradeMapProgress.trackRoute]: the polyline plus travel direction. */
internal fun gradeMapRouteKey(routePolyline: String, reversed: Boolean): Long =
    (routePolyline.hashCode().toLong() shl 1) or (if (reversed) 1L else 0L)
