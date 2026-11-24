package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.PI
import kotlin.math.cos

/** A chord follows the road only when both its ends lie within this distance of the road. */
internal const val MAX_ROAD_OFFSET_M = 5.0

/**
 * A chord follows the road only when the road between its ends is at most this many chords long.
 */
internal const val MAX_ROAD_STRETCH = 2.0

// How far along the road a point is searched for, in multiples of the distance since the last
// point found. Above MAX_ROAD_STRETCH, so a stretch that is too long is found and refused by
// its own test without costing the next chord its start.
private const val SEARCH_STRETCH = 4.0

/** One chord's stretch of road, from the projection of its first end to that of its second. */
internal class ChordRoad(val points: List<LatLng>, val cumDist: DoubleArray)

private class RoadProjection(val arcM: Double, val offsetM: Double)

/** A matched road with the arc position of each of its points. */
private class Road(val points: List<LatLng>) {
    val cum = cumulativeDistancesM(points)

    /** The point of segment [k] nearest to [point], kept within arc positions [arcs]. */
    private fun nearestOnSegment(
        point: LatLng,
        k: Int,
        arcs: ClosedFloatingPointRange<Double>,
    ): RoadProjection {
        val a = points[k]
        val b = points[k + 1]
        // Local metric: longitude scaled by the cosine of the mean latitude, as
        // projectPoiAlongRoute.
        val kx = cos((a.lat + b.lat) * 0.5 * (PI / 180.0))
        val dx = (b.lng - a.lng) * kx
        val dy = b.lat - a.lat
        val segSq = dx * dx + dy * dy
        val along = (point.lng - a.lng) * kx * dx + (point.lat - a.lat) * dy
        val t = if (segSq == 0.0) 0.0 else (along / segSq).coerceIn(0.0, 1.0)
        val segM = cum[k + 1] - cum[k]
        val arc =
            (cum[k] + t * segM).coerceIn(arcs.start, arcs.endInclusive).coerceIn(cum[k], cum[k + 1])
        val u = if (segM == 0.0) 0.0 else (arc - cum[k]) / segM
        val near = LatLng(a.lat + (b.lat - a.lat) * u, a.lng + (b.lng - a.lng) * u)
        return RoadProjection(arc, latLngDistanceM(point, near))
    }

    /**
     * Where [point] meets the road going forward through [arcs]. Stops at the first approach within
     * [MAX_ROAD_OFFSET_M]: on an out-and-back the return pass lies on the same ground further along
     * the road, and must not win a tie.
     */
    fun projectForward(point: LatLng, arcs: ClosedFloatingPointRange<Double>): RoadProjection? {
        var k = cum.indexOfLast { it <= arcs.start }.coerceIn(0, points.lastIndex - 1)
        var best: RoadProjection? = null
        while (k < points.lastIndex && cum[k] <= arcs.endInclusive) {
            val here = nearestOnSegment(point, k, arcs)
            val soFar = best
            if (soFar == null || here.offsetM < soFar.offsetM) {
                best = here
            } else if (soFar.offsetM <= MAX_ROAD_OFFSET_M && here.offsetM > soFar.offsetM) {
                break
            }
            k++
        }
        return best
    }
}

/**
 * Lays the chords between the points of [pointRange] onto [road], the matched road for those
 * points. One entry per chord, null where the chord does not follow the road: an end further than
 * [MAX_ROAD_OFFSET_M] from it, or a stretch of road longer than [MAX_ROAD_STRETCH] chords.
 * [cumDist] is `cumulativeDistancesM(points)`.
 */
internal fun alignChords(
    points: List<LatLng>,
    cumDist: DoubleArray,
    pointRange: IntRange,
    road: MatchedRoad,
): List<ChordRoad?> {
    val chordCount = maxOf(pointRange.last - pointRange.first, 0)
    if (chordCount == 0 || road.points.size < 2) return List(chordCount) { null }
    val onRoad = Road(road.points)
    // Arc position on the road of each point that lies on it; NaN for one that does not.
    val arcs = DoubleArray(chordCount + 1) { Double.NaN }
    var anchorArc = 0.0
    var anchorM = cumDist[pointRange.first]
    for ((n, i) in pointRange.withIndex()) {
        val toArc = anchorArc + SEARCH_STRETCH * (cumDist[i] - anchorM) + 2 * MAX_ROAD_OFFSET_M
        val found = onRoad.projectForward(points[i], anchorArc..toArc)
        if (found != null && found.offsetM <= MAX_ROAD_OFFSET_M) {
            arcs[n] = found.arcM
            anchorArc = found.arcM
            anchorM = cumDist[i]
        }
    }
    return List(chordCount) { j ->
        val chordM = cumDist[pointRange.first + j + 1] - cumDist[pointRange.first + j]
        val stretchM = arcs[j + 1] - arcs[j]
        // A NaN end fails both comparisons.
        if (chordM > 0.0 && stretchM > 0.0 && stretchM <= MAX_ROAD_STRETCH * chordM) {
            val stretch = extractSubPolyline(onRoad.points, onRoad.cum, arcs[j], arcs[j + 1])
            ChordRoad(stretch, cumulativeDistancesM(stretch))
        } else {
            null
        }
    }
}

/**
 * The handed-over route line with, for each chord that follows the road, the stretch of road it
 * stands for. Immutable: [with] gives a new line. [points] and [cumDist] are the handed-over line
 * in saved order; distances are distances along it, and never change.
 *
 * Every saved point has one joint, the single position its distance maps to. It is the saved point
 * itself when either chord meeting there is straight, and otherwise the road end of the earlier
 * chord (at the route start, the road start of the first chord). A chord that follows the road runs
 * from its joint, along its road, to its next joint, so the step onto the road belongs to that
 * chord and a straight chord is drawn exactly as without a road.
 */
internal class MatchedRoadLine
private constructor(
    val points: List<LatLng>,
    private val cumDist: DoubleArray,
    private val roads: Array<ChordRoad?>,
) {
    constructor(
        points: List<LatLng>,
        cumDist: DoubleArray,
    ) : this(points, cumDist, arrayOfNulls(maxOf(points.size - 1, 0)))

    /** How many chords follow the road. */
    val followingChords: Int = roads.count { it != null }

    private val totalM = if (cumDist.isEmpty()) 0.0 else cumDist.last()

    // A following chord's path, joints included; null for a straight chord.
    private val paths: Array<ChordRoad?> =
        Array(roads.size) { j ->
            roads[j]?.let { road ->
                val path = mutableListOf(joint(j))
                for (p in road.points + joint(j + 1)) if (p != path.last()) path += p
                ChordRoad(path, cumulativeDistancesM(path))
            }
        }

    /**
     * The same line with the chords from [firstChord] on taking [chordRoads]. A null entry leaves
     * its chord as it is. The result does not depend on the order of calls on different chords.
     */
    fun with(firstChord: Int, chordRoads: List<ChordRoad?>): MatchedRoadLine {
        val next = roads.copyOf()
        chordRoads.forEachIndexed { n, road ->
            if (road != null && firstChord + n in next.indices) next[firstChord + n] = road
        }
        return MatchedRoadLine(points, cumDist, next)
    }

    private fun joint(i: Int): LatLng {
        val before = roads.getOrNull(i - 1)
        val after = roads.getOrNull(i)
        return when {
            i == 0 -> after?.points?.first() ?: points[i]
            i == points.lastIndex -> before?.points?.last() ?: points[i]
            before == null || after == null -> points[i]
            else -> before.points.last()
        }
    }

    // The first index i >= 1 with cumDist[i] >= distanceM; cumDist.size when there is none.
    private fun firstIndexAtOrAfter(distanceM: Double): Int {
        var lo = 1
        var hi = cumDist.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumDist[mid] < distanceM) lo = mid + 1 else hi = mid
        }
        return lo
    }

    // Position at [savedM], a distance along the saved line within its bounds.
    private fun positionAtSaved(savedM: Double): LatLng {
        val i = firstIndexAtOrAfter(savedM).coerceAtMost(points.lastIndex)
        val path = paths[i - 1]
        return when {
            savedM <= 0.0 -> joint(0)
            savedM >= totalM -> joint(points.lastIndex)
            cumDist[i] == savedM && (roads[i - 1] == null || roads.getOrNull(i) == null) ->
                interpolateAt(points, cumDist, savedM)
            cumDist[i] == savedM -> joint(i)
            path == null -> interpolateAt(points, cumDist, savedM)
            else -> {
                val t = (savedM - cumDist[i - 1]) / (cumDist[i] - cumDist[i - 1])
                interpolateAt(path.points, path.cumDist, t * path.cumDist.last())
            }
        }
    }

    /**
     * The position at [distanceM] along the line, clamped to its ends. With [reversed], the
     * distance runs from the saved line's end.
     */
    fun positionAt(distanceM: Double, reversed: Boolean = false): LatLng {
        if (points.size < 2) return points.firstOrNull() ?: LatLng(0.0, 0.0)
        val d = distanceM.coerceIn(0.0, totalM)
        return positionAtSaved(if (reversed) totalM - d else d)
    }

    // The road points of chord [j] that lie strictly between saved distances [s] and [e].
    private fun roadPointsInside(j: Int, s: Double, e: Double): List<LatLng> {
        val path = paths[j] ?: return emptyList()
        val chordM = cumDist[j + 1] - cumDist[j]
        val pathM = path.cumDist.last()
        return (1 until path.points.lastIndex).mapNotNull { k ->
            val at = cumDist[j] + path.cumDist[k] / pathM * chordM
            path.points[k].takeIf { at > s && at < e }
        }
    }

    /**
     * The points between two distances along the line, in ride order: road points where a chord
     * follows the road, the chord's ends where it does not. Clamps and returns empty as
     * [extractSubPolyline] does, and equals it when no chord follows the road. With [reversed],
     * distances run from the saved line's end.
     */
    fun pointsBetween(startM: Double, endM: Double, reversed: Boolean = false): List<LatLng> {
        val rideStart = startM.coerceIn(0.0, totalM)
        val rideEnd = endM.coerceIn(0.0, totalM)
        if (points.size < 2 || rideEnd <= rideStart) return emptyList()
        val s = if (reversed) totalM - rideEnd else rideStart
        val e = if (reversed) totalM - rideStart else rideEnd
        val out = mutableListOf(positionAtSaved(s))
        var i = firstIndexAtOrAfter(s)
        while (i < cumDist.size) {
            out += roadPointsInside(i - 1, s, e)
            if (cumDist[i] >= e) break
            if (cumDist[i] > s) out += joint(i)
            i++
        }
        out += positionAtSaved(e)
        if (reversed) out.reverse()
        return out
    }
}
