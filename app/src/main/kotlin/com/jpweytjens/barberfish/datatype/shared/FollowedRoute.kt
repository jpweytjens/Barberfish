package com.jpweytjens.barberfish.datatype.shared

import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import io.hammerhead.karooext.models.Symbol

/**
 * The route the rider is following, whatever produced it: a loaded route, a reroute back onto it (a
 * loaded route with a [rejoinPolyline]), or navigation to a pin or POI (non-null [destination]).
 * Every route-dependent surface reads this instead of casting navigation states, so a new kind of
 * route reaches all of them at once.
 */
internal data class FollowedRoute(
    val polyline: String,
    val elevationPolyline: String?,
    val reversed: Boolean,
    /** Route length on the axis of the rider's distance-to-destination. */
    val distanceM: Double,
    val climbs: List<NavigationState.Climb>,
    val pois: List<Symbol.POI>,
    val rejoinPolyline: String?,
    val rejoinDistanceM: Double?,
    val name: String?,
    /** The pin or POI being navigated to; null for a loaded route. */
    val destination: Symbol.POI?,
)

/** The one place that decides which navigation states count as a followed route. */
internal fun NavigationState.followedRoute(): FollowedRoute? =
    when (this) {
        is NavigationState.NavigatingRoute ->
            FollowedRoute(
                polyline = routePolyline,
                elevationPolyline = routeElevationPolyline,
                reversed = reversed,
                distanceM = routeDistance,
                climbs = climbs,
                pois = pois,
                rejoinPolyline = rejoinPolyline,
                rejoinDistanceM = rejoinDistance,
                name = name,
                destination = null,
            )
        is NavigationState.NavigatingToDestination ->
            FollowedRoute(
                polyline = polyline,
                elevationPolyline = elevationPolyline,
                reversed = false,
                distanceM = destinationDistanceM(polyline, elevationPolyline),
                climbs = climbs,
                pois = emptyList(),
                rejoinPolyline = null,
                rejoinDistanceM = null,
                name = null,
                destination = destination,
            )
        else -> null
    }

/**
 * A destination route carries no length, so it is read from the end of its elevation profile (the
 * same axis the sparklines draw on), else measured along its polyline.
 */
private fun destinationDistanceM(polyline: String, elevationPolyline: String?): Double =
    decodeElevationPolyline(elevationPolyline.orEmpty()).lastOrNull()?.first?.toDouble()
        ?: cumulativeDistancesM(decodeGpsPolyline(polyline)).lastOrNull()
        ?: 0.0
