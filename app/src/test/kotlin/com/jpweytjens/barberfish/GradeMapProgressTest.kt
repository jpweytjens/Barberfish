package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.GradeMapProgress
import com.jpweytjens.barberfish.datatype.shared.gradeMapRouteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapProgressTest {

    private val key = gradeMapRouteKey("abc", reversed = false)

    private fun tracked(): GradeMapProgress = GradeMapProgress().also { it.trackRoute(key) }

    @Test
    fun no_route_tracked_never_advances() {
        val progress = GradeMapProgress()
        assertFalse(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        assertEquals(0.0, progress.progressM, 0.0)
    }

    @Test
    fun advance_moves_in_buckets() {
        val progress = tracked()
        // 10 km route, 9880 m remaining -> 120 m ridden -> bucket 2 -> 100 m.
        assertTrue(progress.advance(9_880.0, onRoute = true, routeDistanceM = 10_000.0))
        assertEquals(100.0, progress.progressM, 0.0)
    }

    @Test
    fun sub_bucket_movement_does_not_advance() {
        val progress = tracked()
        assertTrue(progress.advance(9_880.0, onRoute = true, routeDistanceM = 10_000.0))
        // 130 m ridden is still bucket 2.
        assertFalse(progress.advance(9_870.0, onRoute = true, routeDistanceM = 10_000.0))
        assertEquals(100.0, progress.progressM, 0.0)
    }

    @Test
    fun backward_movement_never_lowers_progress() {
        val progress = tracked()
        assertTrue(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        assertFalse(progress.advance(9_500.0, onRoute = true, routeDistanceM = 10_000.0))
        assertEquals(1_000.0, progress.progressM, 0.0)
    }

    @Test
    fun off_route_samples_are_ignored() {
        val progress = tracked()
        assertTrue(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        // Off course the distance field is rejoin-relative; freeze instead of trusting it.
        assertFalse(progress.advance(500.0, onRoute = false, routeDistanceM = 10_000.0))
        assertEquals(1_000.0, progress.progressM, 0.0)
    }

    @Test
    fun null_distance_is_ignored() {
        val progress = tracked()
        assertFalse(progress.advance(null, onRoute = true, routeDistanceM = 10_000.0))
    }

    @Test
    fun distance_beyond_route_clamps_to_route_end() {
        val progress = tracked()
        // Ride to 9 km first (two agreeing samples) so arrival is a plausible step, not a
        // lone jump.
        assertFalse(progress.advance(1_000.0, onRoute = true, routeDistanceM = 10_000.0))
        assertTrue(progress.advance(990.0, onRoute = true, routeDistanceM = 10_000.0))
        assertTrue(progress.advance(-50.0, onRoute = true, routeDistanceM = 10_000.0))
        assertEquals(10_000.0, progress.progressM, 0.0)
    }

    @Test
    fun a_lone_jump_is_held_back() {
        val progress = tracked()
        // Observed on-device: removing the route delivers one last distance sample that
        // reads as arrival, before the stream goes unavailable. A single sample must not
        // commit a jump no rider could make between two ticks.
        assertFalse(progress.advance(0.0, onRoute = true, routeDistanceM = 34_151.0))
        assertEquals(0.0, progress.progressM, 0.0)
    }

    @Test
    fun a_repeated_jump_is_committed() {
        val progress = tracked()
        // Loading a route mid-way, or a long GPS gap: the jump is real when the next
        // sample lands within a bucket of it.
        assertFalse(progress.advance(20_000.0, onRoute = true, routeDistanceM = 34_151.0))
        assertTrue(progress.advance(19_980.0, onRoute = true, routeDistanceM = 34_151.0))
        assertEquals(14_150.0, progress.progressM, 0.0)
    }

    @Test
    fun a_contradicted_jump_is_dropped() {
        val progress = tracked()
        assertFalse(progress.advance(0.0, onRoute = true, routeDistanceM = 34_151.0))
        // The next sample disagrees, so the held jump is forgotten and this one is held.
        assertFalse(progress.advance(20_000.0, onRoute = true, routeDistanceM = 34_151.0))
        assertEquals(0.0, progress.progressM, 0.0)
        assertTrue(progress.advance(19_990.0, onRoute = true, routeDistanceM = 34_151.0))
        assertEquals(14_150.0, progress.progressM, 0.0)
    }

    @Test
    fun a_small_step_clears_a_held_jump() {
        val progress = tracked()
        assertFalse(progress.advance(0.0, onRoute = true, routeDistanceM = 34_151.0))
        assertTrue(progress.advance(34_031.0, onRoute = true, routeDistanceM = 34_151.0))
        assertEquals(100.0, progress.progressM, 0.0)
        // A later sample near the old held value is again a lone jump.
        assertFalse(progress.advance(0.0, onRoute = true, routeDistanceM = 34_151.0))
        assertEquals(100.0, progress.progressM, 0.0)
    }

    @Test
    fun route_change_resets_progress() {
        val progress = tracked()
        assertTrue(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        progress.trackRoute(gradeMapRouteKey("other", reversed = false))
        assertEquals(0.0, progress.progressM, 0.0)
    }

    @Test
    fun reversal_is_a_route_change() {
        val progress = tracked()
        assertTrue(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        progress.trackRoute(gradeMapRouteKey("abc", reversed = true))
        assertEquals(0.0, progress.progressM, 0.0)
    }

    @Test
    fun same_route_retrack_keeps_progress() {
        val progress = tracked()
        assertTrue(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        progress.trackRoute(key)
        assertEquals(1_000.0, progress.progressM, 0.0)
    }

    @Test
    fun clear_resets_progress_even_for_the_same_route() {
        val progress = tracked()
        assertTrue(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        progress.clear()
        assertEquals(0.0, progress.progressM, 0.0)
        assertFalse(progress.advance(9_000.0, onRoute = true, routeDistanceM = 10_000.0))
        progress.trackRoute(key)
        assertEquals(0.0, progress.progressM, 0.0)
    }

    @Test
    fun zero_route_distance_never_advances() {
        val progress = tracked()
        assertFalse(progress.advance(0.0, onRoute = true, routeDistanceM = 0.0))
        assertEquals(0.0, progress.progressM, 0.0)
    }

    @Test
    fun trackRoute_reports_whether_it_reset() {
        val progress = GradeMapProgress()
        assertTrue(progress.trackRoute(key))
        assertFalse(progress.trackRoute(key))
        assertTrue(progress.trackRoute(gradeMapRouteKey("other", reversed = false)))
        progress.clear()
        assertTrue(progress.trackRoute(key))
    }

    @Test
    fun arrival_covers_the_final_partial_bucket() {
        val progress = tracked()
        // 998 m route: floor bucketing alone would cap progress at 950 m and never
        // reach a chevron placed in the last 48 m.
        assertTrue(progress.advance(0.0, onRoute = true, routeDistanceM = 998.0))
        assertTrue(progress.progressM >= 998.0)
    }
}
