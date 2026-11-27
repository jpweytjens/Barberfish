package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher

// Records what it is asked; [answer] gets the call's number (from 0) and its points.
internal class FakeMatcher(
    val answer: (call: Int, points: List<LatLng>) -> MatchedRoad? = { _, points ->
        MatchedRoad(points)
    }
) : RoadMatcher {
    val calls = mutableListOf<List<LatLng>>()

    override fun match(points: List<LatLng>): MatchedRoad? {
        calls += points
        return answer(calls.size - 1, points)
    }
}

// A road whose points run [action] the first time one is read. The followers read them only
// after the match has returned, when they lay the chords onto the road.
internal class TripwireList(private val points: List<LatLng>, private val action: () -> Unit) :
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
