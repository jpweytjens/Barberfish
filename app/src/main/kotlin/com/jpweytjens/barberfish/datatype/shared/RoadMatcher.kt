package com.jpweytjens.barberfish.datatype.shared

/**
 * Gives the road under a line, as the Karoo's offline road data has it. Blocking; call off the main
 * thread. Null when there is no usable answer.
 */
internal fun interface RoadMatcher {
    fun match(points: List<LatLng>): List<LatLng>?
}
