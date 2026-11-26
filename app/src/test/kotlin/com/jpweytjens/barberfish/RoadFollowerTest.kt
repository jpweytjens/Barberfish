package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.RoadFollower
import com.jpweytjens.barberfish.extension.RouteLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoadFollowerTest {
    // A line east along the polyline grid: [steps] chords of 50.1 m, [latE5] grid rows north.
    private fun route(steps: Int, latE5: Int = 0): String =
        encodeGpsPolyline((0..steps).map { LatLng(latE5 * 1e-5, it * 45 * 1e-5) })

    // 60.1 km: pieces of 200, 500 and 500 chords.
    private val routeA = route(1200)
    private val pointsA = decodeGpsPolyline(routeA)
    private val cumA = cumulativeDistancesM(pointsA)
    private val routeB = route(1200, latE5 = 90)
    private val pointsB = decodeGpsPolyline(routeB)

    // One piece each.
    private val shortA = route(100)
    private val shortB = route(100, latE5 = 90)

    private val status = MutableStateFlow(RoadFollowStatus.IDLE)

    // Records what it is asked; [answer] gets the call's number (from 0) and its points.
    private class FakeMatcher(
        val answer: (call: Int, points: List<LatLng>) -> List<LatLng>? = { _, points ->
            points
        }
    ) : RoadMatcher {
        val calls = mutableListOf<List<LatLng>>()

        override fun match(points: List<LatLng>): List<LatLng>? {
            calls += points
            return answer(calls.size - 1, points)
        }
    }

    // A road whose points run [action] the first time one is read. The follower reads them
    // only after the match has returned, when it lays the chords onto the road.
    private class TripwireList(private val points: List<LatLng>, private val action: () -> Unit) :
        AbstractList<LatLng>() {
        private var tripped = false

        override val size: Int
            get() = points.size

        override fun get(index: Int): LatLng {
            if (!tripped) {
                tripped = true
                action()
            }
            return points[index]
        }
    }

    private fun TestScope.follower(matcher: RoadMatcher, wanted: Flow<RouteLine?> = emptyFlow()) =
        RoadFollower(matcher, backgroundScope, wanted, status)

    private fun kmOfFirstPoint(call: List<LatLng>) = cumA[pointsA.indexOf(call.first())] / 1000

    @Test
    fun pieces_are_matched_from_the_rider_outward() = runTest {
        val matcher = FakeMatcher()
        follower(matcher).follow(routeA, reversed = false, riderM = 20_000.0)
        runCurrent()
        assertEquals(4, matcher.calls.size)
        listOf(19.7, 29.7, 54.8, 0.0).forEachIndexed { i, km ->
            assertEquals(km, kmOfFirstPoint(matcher.calls[i]), 0.1)
        }
    }

    @Test
    fun an_echoing_matcher_makes_every_chord_follow() = runTest {
        val follower = follower(FakeMatcher())
        assertNull(follower.line.value)
        follower.follow(routeA, false, 20_000.0)
        runCurrent()
        assertEquals(1200, follower.line.value!!.followingChords)
        assertEquals(pointsA, follower.line.value!!.points)
        assertEquals(RoadFollowStatus.FOLLOWING, status.value)
    }

    @Test
    fun overlap_chords_are_not_kept() = runTest {
        val follower = follower(FakeMatcher { call, points -> points.takeIf { call == 0 } })
        follower.follow(routeA, false, 20_000.0)
        runCurrent()
        // The first piece was sent with 300 m extra at both ends and keeps its 10 km only.
        assertEquals(200, follower.line.value!!.followingChords)
        assertEquals(RoadFollowStatus.FOLLOWING, status.value)
    }

    @Test
    fun a_piece_without_an_answer_leaves_the_others() = runTest {
        val matcher = FakeMatcher { call, points -> points.takeIf { call != 1 } }
        val follower = follower(matcher)
        follower.follow(routeA, false, 20_000.0)
        runCurrent()
        assertEquals(4, matcher.calls.size)
        assertEquals(1200 - 500, follower.line.value!!.followingChords)
    }

    @Test
    fun a_road_that_cannot_be_read_costs_its_piece_and_nothing_else() = runTest {
        val broken =
            object : AbstractList<LatLng>() {
                override val size = 5

                override fun get(index: Int): LatLng = error("unreadable road")
            }
        val matcher = FakeMatcher { call, points ->
            if (call == 0) broken else points
        }
        val follower = follower(matcher)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertEquals(3, matcher.calls.size)
        assertEquals(1200 - 200, follower.line.value!!.followingChords)
        assertEquals(RoadFollowStatus.FOLLOWING, status.value)
    }

    @Test
    fun no_answer_at_all_ends_as_no_map() = runTest {
        val matcher = FakeMatcher { _, _ -> null }
        val follower = follower(matcher)
        follower.follow(routeA, false, 20_000.0)
        runCurrent()
        assertEquals(4, matcher.calls.size)
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.NO_MAP, status.value)
    }

    @Test
    fun a_road_nowhere_near_the_route_ends_as_no_map() = runTest {
        val follower = follower(FakeMatcher { _, _ -> pointsB })
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.NO_MAP, status.value)
    }

    @Test
    fun a_route_change_drops_the_running_match_and_the_pieces_behind_it() = runTest {
        lateinit var follower: RoadFollower
        val matcher = FakeMatcher { call, points ->
            if (call == 0) follower.follow(routeB, false, 0.0)
            points
        }
        follower = follower(matcher)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertEquals(1, matcher.calls.count { it.first() in pointsA })
        assertEquals(pointsB, follower.line.value!!.points)
        assertEquals(1200, follower.line.value!!.followingChords)
    }

    // The next three change the route after the match has returned.

    @Test
    fun a_stop_after_the_match_returned_publishes_nothing() = runTest {
        lateinit var follower: RoadFollower
        val matcher = FakeMatcher { _, points ->
            TripwireList(points) { follower.stop() }
        }
        follower = follower(matcher)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.IDLE, status.value)
        assertEquals(1, matcher.calls.size)
    }

    @Test
    fun a_route_change_after_the_match_returned_keeps_the_new_route_clean() = runTest {
        lateinit var follower: RoadFollower
        val matcher = FakeMatcher { call, points ->
            if (call == 0) {
                TripwireList(points) { follower.follow(routeB, false, 0.0) }
            } else {
                points
            }
        }
        follower = follower(matcher)
        val seen = mutableListOf<MatchedRoadLine?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            follower.line.toList(seen)
        }
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertEquals(pointsB, follower.line.value!!.points)
        assertTrue(seen.filterNotNull().isNotEmpty())
        assertTrue(seen.filterNotNull().all { it.points == pointsB })
    }

    @Test
    fun a_late_no_map_does_not_overwrite_the_new_routes_status() = runTest {
        lateinit var follower: RoadFollower
        val matcher = FakeMatcher { call, points ->
            if (call == 0) {
                // Route A's only piece gets no answer, after route B has taken over.
                follower.follow(shortB, false, 0.0)
                null
            } else {
                points
            }
        }
        follower = follower(matcher)
        val seen = mutableListOf<RoadFollowStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { status.toList(seen) }
        follower.follow(shortA, false, 0.0)
        runCurrent()
        assertEquals(2, matcher.calls.size)
        assertEquals(RoadFollowStatus.FOLLOWING, status.value)
        assertFalse(RoadFollowStatus.NO_MAP in seen)
    }

    @Test
    fun stop_wins_against_a_job_on_another_thread() {
        // Real threads. Probabilistic: it can pass on a broken build, it cannot fail on a
        // correct one.
        val scope = CoroutineScope(Dispatchers.Default)
        val follower = RoadFollower(FakeMatcherThreadSafe, scope, emptyFlow(), status)
        val short = route(20)
        repeat(2_000) {
            follower.follow(short, false, 0.0)
            if (it % 2 == 0) Thread.yield()
            follower.stop()
            assertNull(follower.line.value)
            assertEquals(RoadFollowStatus.IDLE, status.value)
        }
        runBlocking { scope.coroutineContext.job.children.toList().joinAll() }
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.IDLE, status.value)
        scope.cancel()
    }

    private object FakeMatcherThreadSafe : RoadMatcher {
        override fun match(points: List<LatLng>) = points
    }

    // The watch.

    @Test
    fun a_route_no_longer_wanted_is_dropped() = runTest {
        val a = RouteLine(routeA, false)
        val wanted = MutableStateFlow<RouteLine?>(a)
        val follower = follower(FakeMatcher(), wanted)
        for (other in listOf(null, RouteLine(routeB, false), RouteLine(routeA, true))) {
            wanted.value = a
            follower.follow(routeA, false, 0.0)
            runCurrent()
            assertEquals(RoadFollowStatus.FOLLOWING, status.value)
            wanted.value = other
            runCurrent()
            assertNull(follower.line.value)
            assertEquals(RoadFollowStatus.IDLE, status.value)
        }
    }

    @Test
    fun an_unwanted_route_stops_being_matched() = runTest {
        val wanted = MutableStateFlow<RouteLine?>(RouteLine(routeA, false))
        val matcher = FakeMatcher { call, points ->
            if (call == 0) wanted.value = null
            points
        }
        val follower = follower(matcher, wanted)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertEquals(1, matcher.calls.size)
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.IDLE, status.value)
    }

    @Test
    fun a_wanted_route_is_left_alone() = runTest {
        val wanted = MutableSharedFlow<RouteLine?>(replay = 1)
        wanted.emit(RouteLine(routeA, false))
        val matcher = FakeMatcher()
        val follower = follower(matcher, wanted)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        wanted.emit(RouteLine(routeA, false))
        runCurrent()
        assertEquals(1200, follower.line.value!!.followingChords)
        assertEquals(3, matcher.calls.size)
    }

    @Test
    fun the_watch_of_an_old_route_does_not_stop_the_new_one() = runTest {
        val wanted = MutableStateFlow<RouteLine?>(RouteLine(routeA, false))
        val follower = follower(FakeMatcher(), wanted)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        // No dispatch in between: route A's watch wakes after route B is being followed.
        wanted.value = RouteLine(routeB, false)
        follower.follow(routeB, false, 0.0)
        runCurrent()
        assertEquals(pointsB, follower.line.value!!.points)
        assertEquals(RoadFollowStatus.FOLLOWING, status.value)
    }

    @Test
    fun a_route_followed_while_another_is_wanted_is_stopped() = runTest {
        val wanted = MutableStateFlow<RouteLine?>(RouteLine(routeA, false))
        val follower = follower(FakeMatcher(), wanted)
        follower.follow(routeB, false, 0.0)
        runCurrent()
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.IDLE, status.value)
    }

    @Test
    fun following_the_same_route_again_matches_nothing_new() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        val line = follower.line.value
        follower.follow(routeA, false, 30_000.0)
        runCurrent()
        assertEquals(3, matcher.calls.size)
        assertTrue(line === follower.line.value)
    }

    @Test
    fun a_reversed_route_starts_at_the_rider_and_matches_in_saved_order() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(routeA, reversed = true, riderM = 0.0)
        runCurrent()
        // The ride starts at the saved line's end: its last 10 km, plus 300 m of overlap.
        val first = matcher.calls.first()
        assertEquals(pointsA.last(), first.last())
        assertEquals(cumA.last() / 1000 - 10.3, kmOfFirstPoint(first), 0.1)
        assertEquals(pointsA.subList(pointsA.indexOf(first.first()), pointsA.size), first)
        assertEquals(1200, follower.line.value!!.followingChords)
        assertEquals(pointsA, follower.line.value!!.points)
    }

    @Test
    fun stop_drops_the_line_and_goes_idle() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(routeA, false, 0.0)
        runCurrent()
        follower.stop()
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.IDLE, status.value)
        // Stopped, the same route can be followed again.
        follower.follow(routeA, false, 0.0)
        runCurrent()
        assertEquals(6, matcher.calls.size)
        assertEquals(RoadFollowStatus.FOLLOWING, status.value)
    }

    @Test
    fun a_route_too_short_to_have_a_chord_ends_as_no_map() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(route(0), false, 0.0)
        runCurrent()
        follower.stop()
        follower.follow("", false, 0.0)
        runCurrent()
        assertTrue(matcher.calls.isEmpty())
        assertNull(follower.line.value)
        assertEquals(RoadFollowStatus.NO_MAP, status.value)
    }
}
