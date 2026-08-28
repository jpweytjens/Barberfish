package com.jpweytjens.barberfish.datatype.shared

import io.hammerhead.karooext.models.OnNavigationState.NavigationState.Climb

/**
 * Ride-long climb inventory for the current route.
 *
 * The navigation state's climb list carries upcoming climbs only: entering a climb drops it from
 * the list, and while off-route the reported start distances track the rider rather than the route
 * start. On-route start distances are static route-frame values, so the fullest on-route list seen
 * is the route's inventory and never needs correcting afterwards.
 *
 * Update rule adopted from timklge/karoo-routegraph (Apache-2.0): a route identity change clears
 * first; an incoming list is accepted only while on route and only when it grows.
 */
internal fun updateClimbCache(
    cached: List<Climb>?,
    incoming: List<Climb>?,
    onRoute: Boolean,
    routeChanged: Boolean,
): List<Climb>? {
    val base = if (routeChanged) null else cached
    val grows = (incoming?.size ?: 0) > (base?.size ?: 0)
    return if (onRoute && grows) incoming else base
}

/**
 * Process-wide holder shared by every sparkline flow instance. Per-flow caches would diverge: a
 * field added mid-ride has no history and would show upcoming climbs only while another still shows
 * the full set. Route identity is [gradeMapRouteKey], the same derivation the grade-map progress
 * latch uses, so the surfaces cannot disagree on what "same route" means.
 */
internal class ClimbCacheHolder {
    private var routeKey: Long? = null
    private var cached: List<Climb>? = null

    /**
     * Feed one emission and read the cached inventory. A null [routeKey] (navigation cleared)
     * empties the cache; a changed key clears before the update rule runs.
     */
    @Synchronized
    fun resolve(routeKey: Long?, incoming: List<Climb>?, onRoute: Boolean): List<Climb> {
        val routeChanged = routeKey != this.routeKey
        this.routeKey = routeKey
        cached =
            if (routeKey == null) null
            else updateClimbCache(cached, incoming, onRoute, routeChanged)
        return cached ?: emptyList()
    }
}

/** The one process-wide instance; every consumer reads climbs through this. */
internal val sharedClimbCache = ClimbCacheHolder()
