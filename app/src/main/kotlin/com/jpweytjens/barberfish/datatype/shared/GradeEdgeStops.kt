package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.abs

// The clamped axis every proportional grade visualization shares.
// Terminal open bands run to these edges; see the 2026-08-01 spec, Decision 2.
internal const val GRADE_AXIS_MIN = -15.0
internal const val GRADE_AXIS_MAX = 25.0

// An edge parked past every stop, so that side colours nothing. Double.MAX_VALUE rather than
// POSITIVE_INFINITY because the edge is persisted and JSON has no infinity literal.
internal const val GRADE_EDGE_OFF = Double.MAX_VALUE

/** One snap position: where the thumb sits on the axis and the edge selecting it stores. */
internal data class EdgeStop(val axisGrade: Double, val edge: Double)

// The palette's true zero boundary, if it has one: a band starting at 0 (Turbo's flattest
// climb band) or a floor at 0 (the one-sided palettes). There an edge of 0.0 means no
// filtering on that side, so the handle gets a stop for it. A band merely straddling zero
// (Barberfish's flat band) has no zero boundary; its far edges carry the crossover stops
// instead.
private fun hasClimbZeroStop(palette: GradePalette): Boolean =
    gradeBands(palette, readable = false).any { it.lo == 0.0 } ||
        gradeFloor(palette, readable = false) == 0.0

private fun hasDescentZeroStop(palette: GradePalette): Boolean =
    gradeBands(palette, readable = false).any { band ->
        band.hi == 0.0 && (band.lo ?: Double.NEGATIVE_INFINITY) < 0.0
    }

// The climb slider's positions: the crossover stop at a zero-straddling flat band's lo
// (everything from there up, flat band included), or fully-on at 0 where the palette has a
// real zero edge; then the palette's climb stops; then Off at the axis end. The two leading
// stops are mutually exclusive by construction: a band table has a zero edge or a
// zero-straddling band, never both.
internal fun climbEdgeStops(palette: GradePalette): List<EdgeStop> =
    listOfNotNull(
        zeroStraddlingBand(palette, readable = false)?.lo?.let { EdgeStop(it, it) },
        if (hasClimbZeroStop(palette)) EdgeStop(0.0, 0.0) else null,
    ) +
        gradeBandStops(palette).climb.map { EdgeStop(it, it) } +
        EdgeStop(GRADE_AXIS_MAX, GRADE_EDGE_OFF)

// The descent slider's positions: Off at the axis end, the palette's descent stops, then
// fully-on at 0 or the crossover stop at a zero-straddling flat band's hi, mirroring the
// climb side.
internal fun descentEdgeStops(palette: GradePalette): List<EdgeStop> =
    listOf(EdgeStop(GRADE_AXIS_MIN, -GRADE_EDGE_OFF)) +
        gradeBandStops(palette).descent.sorted().map { EdgeStop(it, it) } +
        listOfNotNull(
            if (hasDescentZeroStop(palette)) EdgeStop(0.0, 0.0) else null,
            zeroStraddlingBand(palette, readable = false)?.hi?.let { EdgeStop(it, it) },
        )

// The position a stored edge lands on: nearest stop by axis distance, so a stale edge (a
// retired stop, a parked sentinel) snaps rather than strands the thumb. On a distance tie (a
// stored 3.5 on Barberfish sits exactly between 2 and 5) the stop nearer Off wins: the
// conservative reading colours less. An edge at or across zero never reaches here;
// selectGradeEdges resolves it as fully on by sign first. A null edge means that side
// colours nothing, which is the Off position.
internal fun nearestEdgeStop(stops: List<EdgeStop>, edge: Double?): EdgeStop {
    val off = stops.first { abs(it.edge) == GRADE_EDGE_OFF }
    if (edge == null) return off
    val clamped = edge.coerceIn(GRADE_AXIS_MIN, GRADE_AXIS_MAX)
    return stops.minWith(
        compareBy({ abs(it.axisGrade - clamped) }, { abs(it.axisGrade - off.axisGrade) })
    )
}

/**
 * The stops a side may reach without passing the other handle. Handles may meet (a shared position
 * colours everything, the two half-lines overlap) but never cross. Off sits at the axis end caps,
 * always on its own side of any selection, so it always survives.
 */
internal fun reachableClimbStops(stops: List<EdgeStop>, descentSel: EdgeStop?): List<EdgeStop> =
    if (descentSel == null) stops else stops.filter { it.axisGrade >= descentSel.axisGrade }

internal fun reachableDescentStops(stops: List<EdgeStop>, climbSel: EdgeStop): List<EdgeStop> =
    stops.filter {
        it.axisGrade <= climbSel.axisGrade
    }

/**
 * The stops a stored (climb, descent) pair lands on. Null descent: the palette has no descent
 * stops.
 */
internal data class GradeEdgeSelection(val climb: EdgeStop, val descent: EdgeStop?)

/**
 * The one resolution of a stored edge pair against [palette]: the slider's handles and every
 * renderer read through this, so they cannot disagree. Climb resolves first, from the full stop
 * list, then bounds the descent side: a crossed stored pair (hand-edited DataStore, version skew, a
 * palette switch) normalizes into a legal meet instead of crossed handles. Climb-first is arbitrary
 * but deterministic.
 */
internal fun selectGradeEdges(
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
): GradeEdgeSelection {
    val climbStops = climbEdgeStops(palette)
    // An edge at or across zero asks for everything on its side: fully on, at whatever this
    // palette's innermost stop is (a real zero edge, or a flat band's crossover). Only a
    // threshold strictly inside the climb or descent range needs the nearest-stop guess.
    val climb =
        if (climbEdge != null && climbEdge <= 0.0) climbStops.first()
        else nearestEdgeStop(climbStops, climbEdge)
    val descentStops = descentEdgeStops(palette).takeIf { it.size > 1 }
    val descent = descentStops?.let { stops ->
        val reachable = reachableDescentStops(stops, climb)
        if (descentEdge != null && descentEdge >= 0.0) reachable.last()
        else nearestEdgeStop(reachable, descentEdge)
    }
    return GradeEdgeSelection(climb, descent)
}

/**
 * [selectGradeEdges] as the edge pair the renderers consume. A null side stays null (that side is
 * uncoloured); a stored descent edge on a palette with no descent stops resolves to null too.
 */
internal fun snapGradeEdges(
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
): Pair<Double?, Double?> {
    val selection = selectGradeEdges(palette, climbEdge, descentEdge)
    return climbEdge?.let { selection.climb.edge } to descentEdge?.let { selection.descent?.edge }
}
