package com.jpweytjens.barberfish.datatype.shared

/**
 * The road under a line, as the Karoo's offline road data has it. [heights] are (distance m, height
 * m) on the road's own distance axis, null when the road has none.
 */
internal data class MatchedRoad(
    val points: List<LatLng>,
    val heights: List<Pair<Float, Float>>? = null,
)

/** Blocking; call off the main thread. Null when there is no usable answer. */
internal fun interface RoadMatcher {
    fun match(points: List<LatLng>): MatchedRoad?
}
