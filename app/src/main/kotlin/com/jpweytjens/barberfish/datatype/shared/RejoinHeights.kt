package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.abs

/**
 * The most line length, in metres, that may lack a road before the first or after the last anchor.
 */
internal const val MAX_UNSUPPORTED_END_M = 30.0

/** The most line length, in metres, that may lack a road in one run between two anchors. */
internal const val MAX_UNSUPPORTED_STRETCH_M = 60.0

/** The most share of the line's length that may lack a road, all runs together. */
internal const val MAX_UNSUPPORTED_SHARE = 0.10

/** The rejoin [line] with the road's [heights] on its own distance axis. */
internal class LineHeights(val line: MatchedRoadLine, val heights: List<Pair<Float, Float>>)

// Whether the chords leave little enough of the line without a road, measured between [anchors].
private fun isSupportedEnough(
    cumDist: DoubleArray,
    chords: List<ChordRoad?>,
    anchors: List<Int>,
): Boolean {
    if (anchors.isEmpty()) return false
    val totalM = cumDist.last()
    val endM = cumDist[anchors.first()] + (totalM - cumDist[anchors.last()])
    val runs = anchors.zipWithNext { a, b ->
        (a until b).sumOf { j -> if (chords[j] == null) cumDist[j + 1] - cumDist[j] else 0.0 }
    }
    return cumDist[anchors.first()] <= MAX_UNSUPPORTED_END_M &&
        totalM - cumDist[anchors.last()] <= MAX_UNSUPPORTED_END_M &&
        runs.all { it <= MAX_UNSUPPORTED_STRETCH_M } &&
        endM + runs.sum() <= MAX_UNSUPPORTED_SHARE * totalM
}

// The profile vertices: both ends and every road sample strictly inside each following chord.
private fun profileOnLine(
    cumDist: DoubleArray,
    alignment: RoadAlignment,
    anchors: List<Int>,
    roadHeights: List<Pair<Float, Float>>,
): List<Pair<Float, Float>> {
    val arcs = alignment.arcs
    // Distances strictly increase as floats, the profile's own type.
    val profile = mutableListOf<Pair<Float, Float>>()
    fun add(distanceM: Double, height: Double) {
        if (profile.isEmpty() || distanceM.toFloat() > profile.last().first) {
            profile += distanceM.toFloat() to height.toFloat()
        }
    }
    add(0.0, elevationAtM(roadHeights, arcs[anchors.first()]))
    for ((j, chord) in alignment.chords.withIndex()) {
        if (chord == null) continue
        val startM = cumDist[j]
        val chordM = cumDist[j + 1] - startM
        val arcRun = arcs[j + 1] - arcs[j]
        add(startM, elevationAtM(roadHeights, arcs[j]))
        for ((sampleArc, sampleHeight) in roadHeights) {
            if (sampleArc > arcs[j] && sampleArc < arcs[j + 1]) {
                add(startM + (sampleArc - arcs[j]) / arcRun * chordM, sampleHeight.toDouble())
            }
        }
        add(cumDist[j + 1], elevationAtM(roadHeights, arcs[j + 1]))
    }
    add(cumDist.last(), elevationAtM(roadHeights, arcs[anchors.last()]))
    return profile
}

/**
 * The road's heights on the distance axis of the line [points] with distances [cumDist]. An anchor
 * is a point on the road that belongs to a chord following it. Heights between two anchors follow
 * the road within each following chord and are bridged straight across the rest; before the first
 * anchor and after the last they are held flat. Null when the road has no heights, or when too much
 * of the line lacks a road: more than [MAX_UNSUPPORTED_END_M] before the first anchor or after the
 * last, a run over [MAX_UNSUPPORTED_STRETCH_M] between two anchors, or more than
 * [MAX_UNSUPPORTED_SHARE] of the line's length in all.
 */
internal fun heightsOnLine(
    points: List<LatLng>,
    cumDist: DoubleArray,
    road: MatchedRoad,
): LineHeights? {
    val roadHeights = road.heights
    if (roadHeights.isNullOrEmpty() || points.size < 2 || cumDist.last() <= 0.0) return null
    val alignment = alignToRoad(points, cumDist, points.indices, road)
    val chords = alignment.chords
    val anchors =
        points.indices.filter { i ->
            !alignment.arcs[i].isNaN() &&
                (chords.getOrNull(i) != null || chords.getOrNull(i - 1) != null)
        }
    return if (isSupportedEnough(cumDist, chords, anchors)) {
        val line = MatchedRoadLine(points, cumDist).with(0, chords)
        LineHeights(line, profileOnLine(cumDist, alignment, anchors, roadHeights))
    } else {
        null
    }
}

/** The farthest, in metres, the rejoin line's landing may lie from the route. */
internal const val MAX_LANDING_OFFSET_M = 30.0

/** The most, in metres, the line's last height may differ from the route's profile there. */
internal const val MAX_LANDING_HEIGHT_DIFF_M = 30.0

/**
 * Whether the last of the rejoin line's [heights] is within [MAX_LANDING_HEIGHT_DIFF_M] of the
 * route's own profile where the line lands at [landing]. False when the landing is farther than
 * [MAX_LANDING_OFFSET_M] from the route or the route has no profile. [heights] is not empty.
 * [routePoints] and [routeElevation] are both in ride order.
 */
internal fun landingHeightAgrees(
    heights: List<Pair<Float, Float>>,
    landing: LatLng,
    routePoints: List<LatLng>,
    routeElevation: List<Pair<Float, Float>>,
): Boolean {
    val cumDist = cumulativeDistancesM(routePoints)
    val landingM = projectPoiAlongRoute(landing, routePoints, cumDist, MAX_LANDING_OFFSET_M)
    if (landingM == null || routeElevation.isEmpty()) return false
    val elevToGps = profileToGpsScale(cumDist.last(), routeElevation.last().first.toDouble())
    val routeHeightM = elevationAtM(routeElevation, landingM / elevToGps)
    return abs(heights.last().second - routeHeightM) <= MAX_LANDING_HEIGHT_DIFF_M
}
