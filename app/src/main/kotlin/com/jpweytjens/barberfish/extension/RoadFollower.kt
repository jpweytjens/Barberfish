package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.MatchPiece
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.alignChords
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.matchPieces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import timber.log.Timber

/** What matching has given the route being followed. */
internal enum class RoadFollowStatus {
    /** Nothing followed, or no piece has an answer yet. */
    IDLE,
    /** At least one chord follows the road. */
    FOLLOWING,
    /** Every piece is done and no chord follows the road. */
    NO_MAP,
}

/** What the route being followed has got from matching; the config screen reads it. */
internal val roadFollowStatus = MutableStateFlow(RoadFollowStatus.IDLE)

/** A saved route line and the direction it is ridden in. */
internal data class RouteLine(val polyline: String, val reversed: Boolean)

/**
 * Matches the followed route to the road piece by piece, from the rider outward, and publishes the
 * result as it grows.
 *
 * [wanted] is the route whose road should be followed right now, null for none. The follower
 * collects it for as long as it follows a route, and stops when it names anything else, so stopping
 * does not depend on whoever called [follow] still being there.
 */
internal class RoadFollower(
    private val matcher: RoadMatcher,
    private val scope: CoroutineScope,
    private val wanted: Flow<RouteLine?>,
    private val status: MutableStateFlow<RoadFollowStatus> = roadFollowStatus,
    // One match at a time, whichever line it is for: the engine is heavy, and a match that is
    // running cannot be interrupted. Shared with every other follower that asks the engine.
    private val oneMatch: Mutex = Mutex(),
) {
    private val _line = MutableStateFlow<MatchedRoadLine?>(null)

    /** Null until the first piece of the current route is in; a new snapshot per piece. */
    val line: StateFlow<MatchedRoadLine?> = _line

    // Guards everything below it, and every write to [_line] and [status].
    private val lock = Any()

    // Token of the route being followed, null when stopped. A job may write only while its own
    // token is the current one.
    private var current: Any? = null
    private var followed: RouteLine? = null
    private var job: Job? = null

    /** Starts matching [routePolyline]; a no-op when it is already the route being followed. */
    fun follow(routePolyline: String, reversed: Boolean, riderM: Double) {
        val route = RouteLine(routePolyline, reversed)
        synchronized(lock) {
            if (current != null && followed == route) return
            val token = Any()
            reset(token, route)
            job = scope.launch {
                launch { wanted.collect { if (it != route) stopIf(token) } }
                matchPieces(token, route, riderM)
            }
        }
    }

    /** Cancels pieces not yet started and drops the line. */
    fun stop() = synchronized(lock) { reset(null, null) }

    private fun stopIf(token: Any) = synchronized(lock) { if (current === token) reset(null, null) }

    // Caller holds [lock].
    private fun reset(token: Any?, route: RouteLine?) {
        job?.cancel()
        job = null
        current = token
        followed = route
        _line.value = null
        status.value = RoadFollowStatus.IDLE
    }

    /**
     * The only place a job writes. False when the job's route is no longer the one followed: the
     * test and the write are one step with respect to [follow] and [stop], so once either has
     * returned no job of an earlier route can write, whatever point it had reached.
     */
    private fun publish(token: Any, snapshot: MatchedRoadLine?, newStatus: RoadFollowStatus) =
        synchronized(lock) {
            if (current === token) {
                _line.value = snapshot
                status.value = newStatus
            }
            current === token
        }

    /**
     * [line] with [piece]'s chords laid onto [road]. The whole matched stretch is laid onto the
     * road and only the piece's own chords are kept: the overlap is there to let the match settle,
     * not to be drawn.
     */
    // Suppressed: this runs in the service's process, which hosts every field. A road that
    // cannot be laid out, whatever the reason, must cost its piece and nothing else.
    @Suppress("TooGenericExceptionCaught")
    private fun withPiece(
        line: MatchedRoadLine,
        cumDist: DoubleArray,
        piece: MatchPiece,
        road: MatchedRoad,
    ): MatchedRoadLine =
        try {
            val chords = alignChords(line.points, cumDist, piece.points, road)
            val skipped = piece.chords.first - piece.points.first
            line.with(piece.chords.first, chords.subList(skipped, skipped + piece.chords.count()))
        } catch (e: RuntimeException) {
            Timber.w(e, "could not lay a matched piece onto its road")
            line
        }

    private suspend fun matchPieces(token: Any, route: RouteLine, riderM: Double) {
        val points = decodeGpsPolyline(route.polyline)
        val cumDist = cumulativeDistancesM(points)
        // Pieces are ordered along the ride, and named in saved order for the matcher.
        val rideCum = if (route.reversed) cumulativeDistancesM(points.asReversed()) else cumDist
        val pieces: List<MatchPiece> =
            matchPieces(rideCum, riderM).map {
                if (route.reversed) it.mirrored(points.size) else it
            }
        // This job's own snapshot, never read back from [line].
        var mine = MatchedRoadLine(points, cumDist)
        for (piece in pieces) {
            val road = oneMatch.withLock {
                matcher.match(points.subList(piece.points.first, piece.points.last + 1))
            }
            if (road != null) mine = withPiece(mine, cumDist, piece, road)
            val following = mine.followingChords > 0
            val live =
                publish(
                    token,
                    mine.takeIf { following },
                    if (following) RoadFollowStatus.FOLLOWING else RoadFollowStatus.IDLE,
                )
            if (!live) return
            // Lets the watch and a cancellation in between two pieces.
            yield()
        }
        if (mine.followingChords == 0) publish(token, null, RoadFollowStatus.NO_MAP)
    }
}
