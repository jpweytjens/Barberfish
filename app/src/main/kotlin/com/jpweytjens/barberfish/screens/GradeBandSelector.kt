package com.jpweytjens.barberfish.screens

import com.jpweytjens.barberfish.datatype.shared.GradeBand
import kotlin.math.ceil
import kotlin.math.floor

// The clamped axis every proportional grade visualization shares.
// Terminal open bands run to these edges; see the 2026-08-01 spec, Decision 2.
internal const val GRADE_AXIS_MIN = -15.0
internal const val GRADE_AXIS_MAX = 25.0

/** One renderable cell: a band clamped to the axis, with its exemplar reading. */
internal data class GradeCell(
    val lo: Double,
    val hi: Double,
    val exemplar: String,
    val band: GradeBand,
) {
    val weight: Float get() = (hi - lo).toFloat()
}

/**
 * Bands clamped to the axis. A palette with no descent bands starts at 0 rather
 * than the clamp, so missing descent coverage reads as absence.
 */
internal fun gradeCells(bands: List<GradeBand>): List<GradeCell> {
    val hasDescent = bands.any { (it.hi ?: 1.0) <= 0.0 }
    val floor = if (hasDescent) GRADE_AXIS_MIN else 0.0
    return bands.mapNotNull { band ->
        val lo = maxOf(band.lo ?: floor, floor)
        val hi = minOf(band.hi ?: GRADE_AXIS_MAX, GRADE_AXIS_MAX)
        if (hi <= lo) return@mapNotNull null
        GradeCell(lo = lo, hi = hi, exemplar = exemplarLabel((lo + hi) / 2.0), band = band)
    }
}

/** Real thresholds for the tick axis: every band edge, never the clamp. */
internal fun gradeTickStops(bands: List<GradeBand>): List<Double> {
    val cells = gradeCells(bands)
    val inner = cells.drop(1).map { it.lo }
    val lead = cells.first().lo.takeIf { it >= 0.0 }
    return (listOfNotNull(lead) + inner)
}

/** Fraction of the axis at [grade], for tick placement. */
internal fun axisFraction(grade: Double): Float =
    ((grade - GRADE_AXIS_MIN) / (GRADE_AXIS_MAX - GRADE_AXIS_MIN)).toFloat()

/** Round half away from zero, so -12.5 reads -13 and 22.5 reads 23. */
private fun exemplarLabel(mid: Double): String {
    val rounded = if (mid >= 0.0) floor(mid + 0.5) else ceil(mid - 0.5)
    return rounded.toInt().toString()
}
