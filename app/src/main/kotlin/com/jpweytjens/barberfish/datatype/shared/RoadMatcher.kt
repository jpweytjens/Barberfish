package com.jpweytjens.barberfish.datatype.shared

/** The road under a line, as the Karoo's offline road data has it. */
internal data class MatchedRoad(val points: List<LatLng>)

/** Blocking; call off the main thread. Null when there is no usable answer. */
internal fun interface RoadMatcher {
    fun match(points: List<LatLng>): MatchedRoad?
}
