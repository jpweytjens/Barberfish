package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ClimbCacheHolder
import com.jpweytjens.barberfish.datatype.shared.updateClimbCache
import io.hammerhead.karooext.models.OnNavigationState.NavigationState.Climb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun climb(startM: Double, lengthM: Double = 1000.0) =
    Climb(startDistance = startM, length = lengthM, grade = 5.0, totalElevation = 50.0)

class ClimbCacheTest {
    private val a = climb(1000.0)
    private val b = climb(5000.0)
    private val c = climb(9000.0)

    // Pure update rule

    @Test
    fun growth_on_route_is_accepted() {
        assertEquals(listOf(a, b), updateClimbCache(listOf(a), listOf(a, b), true, false))
    }

    @Test
    fun shrink_is_ignored() {
        // Entering climb a drops it from the SDK list; the cache keeps it.
        assertEquals(listOf(a, b), updateClimbCache(listOf(a, b), listOf(b), true, false))
    }

    @Test
    fun equal_size_is_ignored() {
        // Same size, different climbs: accepted staleness per the spec.
        assertEquals(listOf(a, b), updateClimbCache(listOf(a, b), listOf(b, c), true, false))
    }

    @Test
    fun growth_off_route_is_ignored() {
        // Off-route start distances are rider-relative; never commit them.
        assertEquals(listOf(a), updateClimbCache(listOf(a), listOf(a, b), false, false))
    }

    @Test
    fun route_change_clears_before_the_rule() {
        // The emission arriving with a route change is only trusted on-route.
        assertNull(updateClimbCache(listOf(a, b), listOf(c), false, true))
    }

    @Test
    fun route_change_accepts_an_on_route_list() {
        assertEquals(listOf(c), updateClimbCache(listOf(a, b), listOf(c), true, true))
    }

    @Test
    fun null_incoming_keeps_the_cache() {
        assertEquals(listOf(a), updateClimbCache(listOf(a), null, true, false))
    }

    @Test
    fun null_incoming_with_route_change_clears() {
        assertNull(updateClimbCache(listOf(a), null, true, true))
    }

    // Holder

    @Test
    fun holder_populates_then_survives_a_shrink() {
        val holder = ClimbCacheHolder()
        assertEquals(listOf(a, b), holder.resolve(1L, listOf(a, b), true))
        assertEquals(listOf(a, b), holder.resolve(1L, listOf(b), true))
    }

    @Test
    fun holder_ignores_off_route_growth() {
        val holder = ClimbCacheHolder()
        holder.resolve(1L, listOf(a), true)
        assertEquals(listOf(a), holder.resolve(1L, listOf(a, b), false))
    }

    @Test
    fun holder_clears_on_a_new_route_key_until_on_route() {
        val holder = ClimbCacheHolder()
        holder.resolve(1L, listOf(a, b), true)
        // New route (reversal flips the key too), arriving off-route: nothing cached.
        assertEquals(emptyList<Climb>(), holder.resolve(2L, listOf(c), false))
        // First on-route emission repopulates.
        assertEquals(listOf(c), holder.resolve(2L, listOf(c), true))
    }

    @Test
    fun holder_clears_on_null_key() {
        val holder = ClimbCacheHolder()
        holder.resolve(1L, listOf(a), true)
        assertEquals(emptyList<Climb>(), holder.resolve(null, null, false))
        // Reloading the same route after a nav clear is a route change: off-route
        // emissions stay ignored until the rider is on it.
        assertEquals(emptyList<Climb>(), holder.resolve(1L, listOf(a), false))
        assertEquals(listOf(a), holder.resolve(1L, listOf(a), true))
    }
}
