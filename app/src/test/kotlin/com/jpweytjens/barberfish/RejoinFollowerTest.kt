package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.extension.RejoinFollower
import com.jpweytjens.barberfish.extension.RejoinRoad
import com.jpweytjens.barberfish.extension.RejoinTarget
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.RoadFollower
import com.jpweytjens.barberfish.extension.RouteLine
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RejoinFollowerTest {
    // The route runs 4 km east along y = 0; its profile climbs 1 m per 10 m from 100 m.
    private val routePoints = RouteFixtures.straight(lengthM = 4000.0, stepM = 100.0)
    private val route = RouteLine(encodeGpsPolyline(routePoints), reversed = false)
    private val profile = RouteFixtures.encodeElevation(listOf(0f to 100f, 4000f to 500f))

    // A rejoin line 1 km due south onto the route at [landingXM], in 100 m vertices.
    private fun rejoinLine(landingXM: Double): String =
        encodeGpsPolyline((0..10).map { point(landingXM, 1000.0 - it * 100.0) })

    private val lineA = rejoinLine(2000.0)
    private val lineB = rejoinLine(1500.0)
    private val lineC = rejoinLine(2500.0)

    private fun target(line: String, route: RouteLine = this.route, elevation: String? = profile) =
        RejoinTarget(line, route, elevation)

    private val targetA = target(lineA)
    private val targetB = target(lineB)
    private val targetC = target(lineC)

    private val published = MutableStateFlow<RejoinRoad?>(null)

    // The road under [points]: the points themselves, climbing 20 m to [landingHeightM], by
    // default the route profile's height under the line's last point.
    private fun echo(
        points: List<LatLng>,
        landingHeightM: Float = 100f + (points.last().lng * RouteFixtures.M_PER_DEG / 10).toFloat(),
    ): MatchedRoad {
        val lengthM = cumulativeDistancesM(points).last().toFloat()
        return MatchedRoad(points, listOf(0f to landingHeightM - 20f, lengthM to landingHeightM))
    }

    // Records what it is asked; [answer] gets the call's number (from 0) and its points.
    private inner class FakeMatcher(
        val answer: (call: Int, points: List<LatLng>) -> MatchedRoad? = { _, points ->
            echo(points)
        }
    ) : RoadMatcher {
        val calls = mutableListOf<List<LatLng>>()

        override fun match(points: List<LatLng>): MatchedRoad? {
            calls += points
            return answer(calls.size - 1, points)
        }
    }

    // A road whose points run [action] the first time one is read, after the match has returned.
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

    private fun TestScope.follower(matcher: RoadMatcher) =
        RejoinFollower(matcher, backgroundScope, Mutex(), published)

    // Every value [published] takes from now on.
    private fun TestScope.seen(): List<RejoinRoad?> {
        val seen = mutableListOf<RejoinRoad?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            published.toList(seen)
        }
        return seen
    }

    private fun List<RejoinRoad?>.lines() = mapNotNull { it?.rejoinPolyline }

    @Test
    fun a_matched_line_with_agreeing_heights_is_published() = runTest {
        val matcher = FakeMatcher()
        follower(matcher).follow(targetA)
        runCurrent()
        val road = checkNotNull(published.value)
        val points = decodeGpsPolyline(lineA)
        assertEquals(listOf(points), matcher.calls)
        assertEquals(lineA, road.rejoinPolyline)
        assertEquals(points, road.line.points)
        assertEquals(10, road.line.followingChords)
        assertEquals(cumulativeDistancesM(points).last(), road.lengthM, 1e-9)
        assertEquals(0f, road.heights.first().first)
        assertEquals(road.lengthM.toFloat(), road.heights.last().first)
        assertEquals(300f, road.heights.last().second, 1f)
    }

    @Test
    fun a_new_target_withdraws_the_old_result_at_once() = runTest {
        val follower = follower(FakeMatcher())
        follower.follow(targetA)
        runCurrent()
        assertEquals(lineA, published.value?.rejoinPolyline)
        follower.follow(targetB)
        // Before the worker has run at all.
        assertNull(published.value)
        runCurrent()
        assertEquals(lineB, published.value?.rejoinPolyline)
    }

    @Test
    fun latest_wins_under_a_slow_matcher() = runTest {
        lateinit var follower: RejoinFollower
        val matcher = FakeMatcher { call, points ->
            if (call == 0) {
                follower.follow(targetB)
                follower.follow(targetC)
            }
            echo(points)
        }
        follower = follower(matcher)
        val seen = seen()
        follower.follow(targetA)
        runCurrent()
        assertEquals(listOf(lineA, lineC).map(::decodeGpsPolyline), matcher.calls)
        assertEquals(listOf(lineC), seen.lines())
        assertEquals(lineC, published.value?.rejoinPolyline)
    }

    @Test
    fun a_line_replaced_while_it_waits_for_the_engine_is_not_matched() = runTest {
        val oneMatch = Mutex()
        val matcher = FakeMatcher()
        val follower = RejoinFollower(matcher, backgroundScope, oneMatch, published)
        // A route piece holds the engine while A, B and C arrive.
        oneMatch.lock()
        follower.follow(targetA)
        runCurrent()
        follower.follow(targetB)
        follower.follow(targetC)
        oneMatch.unlock()
        runCurrent()
        assertEquals(listOf(decodeGpsPolyline(lineC)), matcher.calls)
        assertEquals(lineC, published.value?.rejoinPolyline)
    }

    @Test
    fun a_stale_result_is_never_published() = runTest {
        lateinit var follower: RejoinFollower
        val matcher = FakeMatcher { call, points ->
            if (call == 0) {
                // A's match has returned; B arrives before it is checked.
                MatchedRoad(TripwireList(points) { follower.follow(targetB) }, echo(points).heights)
            } else {
                echo(points)
            }
        }
        follower = follower(matcher)
        val seen = seen()
        follower.follow(targetA)
        runCurrent()
        assertEquals(2, matcher.calls.size)
        assertEquals(listOf(lineB), seen.lines())
    }

    @Test
    fun a_null_target_stops_and_withdraws() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(targetA)
        runCurrent()
        follower.follow(null)
        assertNull(published.value)
        runCurrent()
        assertNull(published.value)
        assertEquals(1, matcher.calls.size)
    }

    @Test
    fun a_line_that_comes_back_is_matched_again() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(targetA)
        runCurrent()
        // No dispatch in between: the worker never sees the null.
        follower.follow(null)
        follower.follow(targetA)
        runCurrent()
        assertEquals(2, matcher.calls.size)
        assertEquals(lineA, published.value?.rejoinPolyline)
    }

    @Test
    fun the_same_target_again_matches_nothing_new() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(targetA)
        runCurrent()
        val road = published.value
        follower.follow(target(lineA))
        runCurrent()
        assertEquals(1, matcher.calls.size)
        assertTrue(road === published.value)
    }

    @Test
    fun stop_withdraws_and_a_later_target_is_matched() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(targetA)
        runCurrent()
        follower.stop()
        assertNull(published.value)
        follower.follow(targetA)
        runCurrent()
        assertEquals(2, matcher.calls.size)
        assertEquals(lineA, published.value?.rejoinPolyline)
    }

    @Test
    fun a_line_longer_than_one_route_piece_is_not_matched() = runTest {
        val matcher = FakeMatcher()
        val long = encodeGpsPolyline(RouteFixtures.straight(26_000.0, 1000.0, yM = 1000.0))
        follower(matcher).follow(target(long))
        runCurrent()
        assertTrue(matcher.calls.isEmpty())
        assertNull(published.value)
    }

    @Test
    fun a_failed_match_publishes_nothing() = runTest {
        val matcher = FakeMatcher { call, points -> echo(points).takeIf { call != 0 } }
        val follower = follower(matcher)
        val seen = seen()
        follower.follow(targetA)
        runCurrent()
        assertNull(published.value)
        // The worker is still there for the next line.
        follower.follow(targetB)
        runCurrent()
        assertEquals(listOf(lineB), seen.lines())
    }

    @Test
    fun heights_that_fail_the_landing_check_publish_nothing() = runTest {
        // The line lands at 300 m on the route's profile; its road says 600 m.
        val matcher = FakeMatcher { _, points -> echo(points, landingHeightM = 600f) }
        follower(matcher).follow(targetA)
        runCurrent()
        assertEquals(1, matcher.calls.size)
        assertNull(published.value)
    }

    @Test
    fun a_route_without_a_profile_publishes_nothing() = runTest {
        val follower = follower(FakeMatcher())
        follower.follow(target(lineA, elevation = null))
        runCurrent()
        assertNull(published.value)
        follower.follow(target(lineB, elevation = ""))
        runCurrent()
        assertNull(published.value)
    }

    @Test
    fun a_reversed_route_is_checked_in_ride_order() = runTest {
        // Saved west to east reversed, ridden east to west: the same ground as [route], profile
        // in ride order. Landing at x = 1500 is 1500 m into the ride.
        val reversed = RouteLine(encodeGpsPolyline(routePoints.asReversed()), reversed = true)
        val matcher = FakeMatcher()
        follower(matcher).follow(target(lineB, route = reversed))
        runCurrent()
        assertEquals(lineB, published.value?.rejoinPolyline)
    }

    @Test
    fun a_line_of_one_point_is_not_matched() = runTest {
        val matcher = FakeMatcher()
        val follower = follower(matcher)
        follower.follow(target(encodeGpsPolyline(listOf(point(2000.0, 0.0)))))
        runCurrent()
        follower.follow(target(""))
        runCurrent()
        assertTrue(matcher.calls.isEmpty())
        assertNull(published.value)
    }

    @Test
    fun an_unreadable_road_costs_the_line_and_nothing_else() = runTest {
        val matcher = FakeMatcher { call, points ->
            if (call == 0) {
                MatchedRoad(TripwireList(points) { error("unreadable road") }, echo(points).heights)
            } else {
                echo(points)
            }
        }
        val follower = follower(matcher)
        follower.follow(targetA)
        runCurrent()
        assertNull(published.value)
        follower.follow(targetB)
        runCurrent()
        assertEquals(lineB, published.value?.rejoinPolyline)
    }

    // Both followers on real threads. The route is three pieces long (200, 500 and 500 chords of
    // 50.1 m), the first of which is held at a gate until the rejoin line waits for the engine.

    private val longRoute = encodeGpsPolyline((0..1200).map { LatLng(0.0, it * 45 * 1e-5) })

    // Signals [waiting] each time someone asks for the lock.
    private class WatchedMutex(private val inner: Mutex = Mutex()) : Mutex by inner {
        val waiting = AtomicInteger()

        override suspend fun lock(owner: Any?) {
            waiting.incrementAndGet()
            inner.lock(owner)
        }
    }

    private class Harness(rejoinFirstPoint: LatLng) {
        val oneMatch = WatchedMutex()
        val gate = CountDownLatch(1)
        val done = CountDownLatch(4)
        val active = AtomicInteger()
        val mostActive = AtomicInteger()
        val order: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private var pieces = 0

        val matcher = RoadMatcher { points ->
            mostActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            val name = if (points.first() == rejoinFirstPoint) "rejoin" else "piece ${++pieces}"
            order += name
            if (name == "piece 1") gate.await(5, TimeUnit.SECONDS)
            active.decrementAndGet()
            done.countDown()
            MatchedRoad(points)
        }
    }

    private fun runBoth(): Harness {
        val harness = Harness(decodeGpsPolyline(lineA).first())
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val roadFollower =
                RoadFollower(
                    harness.matcher,
                    scope,
                    emptyFlow(),
                    MutableStateFlow(RoadFollowStatus.IDLE),
                    harness.oneMatch,
                )
            val rejoinFollower = RejoinFollower(harness.matcher, scope, harness.oneMatch, published)
            roadFollower.follow(longRoute, reversed = false, riderM = 0.0)
            awaitTrue { harness.order.isNotEmpty() }
            rejoinFollower.follow(targetA)
            // The rejoin worker has asked for the lock: the second request after piece 1's.
            awaitTrue { harness.oneMatch.waiting.get() >= 2 }
            harness.gate.countDown()
            assertTrue(harness.done.await(5, TimeUnit.SECONDS))
        } finally {
            scope.cancel()
        }
        return harness
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met in time" }
            Thread.yield()
        }
    }

    @Test
    fun it_and_the_route_follower_never_match_at_once() {
        val harness = runBoth()
        assertEquals(4, harness.order.size)
        assertEquals(1, harness.mostActive.get())
    }

    @Test
    fun a_waiting_rejoin_line_goes_ahead_of_route_pieces_not_yet_started() {
        val harness = runBoth()
        assertEquals(listOf("piece 1", "rejoin", "piece 2", "piece 3"), harness.order)
    }
}
