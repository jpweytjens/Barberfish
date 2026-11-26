package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.PIECE_M
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.heightsOnLine
import com.jpweytjens.barberfish.datatype.shared.landingHeightAgrees
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** A rejoin line to match, and the route it lands on, whose profile checks its heights. */
internal data class RejoinTarget(
    val rejoinPolyline: String,
    val route: RouteLine,
    val routeElevationPolyline: String?,
)

/**
 * What matching gave the rejoin line [rejoinPolyline]: the [line] following the road, and the
 * road's [heights] on the line's own distance axis, which is [lengthM] long.
 */
internal class RejoinRoad(
    val rejoinPolyline: String,
    val line: MatchedRoadLine,
    val heights: List<Pair<Float, Float>>,
    val lengthM: Double,
)

/** The current rejoin line as matched, null until its match is in and when it fails. */
internal val rejoinRoad = MutableStateFlow<RejoinRoad?>(null)

/**
 * Matches the current rejoin line to the road as a whole and publishes it to [published].
 *
 * Latest wins: a running match cannot be interrupted, so a line that arrives meanwhile waits, and
 * of several that arrive only the newest is matched. A result is published only while its line is
 * still the current one.
 */
internal class RejoinFollower(
    private val matcher: RoadMatcher,
    private val scope: CoroutineScope,
    // Shared with the route follower. A mutex serves its waiters in turn and the route follower
    // takes it once per piece, so a waiting rejoin line goes ahead of pieces not yet started.
    private val oneMatch: Mutex,
    private val published: MutableStateFlow<RejoinRoad?> = rejoinRoad,
) {
    // One per change of target. A state flow skips a value equal to the one its collector last
    // saw, so a target that comes back after another, or after none, must be a new value.
    private class Wanted(val target: RejoinTarget?)

    private val wanted = MutableStateFlow(Wanted(null))

    // Guards [job], and every write to [wanted] and [published].
    private val lock = Any()
    private var job: Job? = null

    /** Makes [target] the line to match, null for none, and withdraws a result for another line. */
    fun follow(target: RejoinTarget?) {
        synchronized(lock) {
            if (wanted.value.target == target) return
            wanted.value = Wanted(target)
            if (published.value?.rejoinPolyline != target?.rejoinPolyline) published.value = null
            // A state flow hands a slow collector only its newest value: that is latest wins.
            if (job == null && target != null) {
                job = scope.launch {
                    wanted.collect { request ->
                        request.target?.let { publish(request, match(request, it)) }
                    }
                }
            }
        }
    }

    /** Drops the line and the worker; a later [follow] starts afresh. */
    fun stop() =
        synchronized(lock) {
            job?.cancel()
            job = null
            wanted.value = Wanted(null)
            published.value = null
        }

    // The test and the write are one step with respect to [follow], so once it has returned no
    // match of an earlier target can write.
    private fun publish(request: Wanted, road: RejoinRoad?) =
        synchronized(lock) { if (wanted.value === request) published.value = road }

    private suspend fun match(request: Wanted, target: RejoinTarget): RejoinRoad? {
        val points = pointsToMatch(target) ?: return null
        // A line replaced while it waited for the engine is not matched: only the newest is.
        val road = oneMatch.withLock {
            if (wanted.value === request) matcher.match(points) else null
        }
        return road?.let { guarded { checked(target, points, it) } }
    }

    // The line's points, null when it has fewer than two or is longer than one route piece.
    private fun pointsToMatch(target: RejoinTarget): List<LatLng>? = guarded {
        decodeGpsPolyline(target.rejoinPolyline)
    }
        ?.takeIf { it.size >= 2 && cumulativeDistancesM(it).last() <= PIECE_M }

    // The road's heights on the line, published only when they agree with the route's profile.
    private fun checked(
        target: RejoinTarget,
        points: List<LatLng>,
        road: MatchedRoad,
    ): RejoinRoad? {
        val cumDist = cumulativeDistancesM(points)
        val lineHeights = heightsOnLine(points, cumDist, road) ?: return null
        val saved = decodeGpsPolyline(target.route.polyline)
        // The elevation profile comes in ride order; the route line in saved order.
        val routePoints = if (target.route.reversed) saved.asReversed() else saved
        val routeElevation = decodeElevationPolyline(target.routeElevationPolyline.orEmpty())
        val agrees =
            landingHeightAgrees(lineHeights.heights, points.last(), routePoints, routeElevation)
        return if (agrees) {
            RejoinRoad(target.rejoinPolyline, lineHeights.line, lineHeights.heights, cumDist.last())
        } else {
            null
        }
    }

    // Suppressed: this runs in the service's process, which hosts every field. A line or road
    // that cannot be read, whatever the reason, must cost this line and nothing else.
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> guarded(block: () -> T?): T? =
        try {
            block()
        } catch (e: RuntimeException) {
            Timber.w(e, "could not read a rejoin line or its road")
            null
        }
}
