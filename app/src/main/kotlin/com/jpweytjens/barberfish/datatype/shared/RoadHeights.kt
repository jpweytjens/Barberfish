package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.abs
import kotlin.math.min

/** The lowest height the engine can report, in metres; it is the same in both units. */
internal const val ROAD_HEIGHT_FLOOR_M = -500.0

/** The height step of the engine as shipped, in metres. */
internal const val STOCK_HEIGHT_STEP_M = 0.25

/** The height step of the Karoo's own road data, in metres. */
internal const val KAROO_HEIGHT_STEP_M = 2.0

/** Distance between height samples along the road, in metres. */
internal const val ROAD_HEIGHT_INTERVAL_M = 30

/**
 * The most, in metres, that the samples' span at [ROAD_HEIGHT_INTERVAL_M] may differ from the
 * road's length.
 */
internal const val MAX_ROAD_HEIGHT_SPAN_DIFF_M = 2 * ROAD_HEIGHT_INTERVAL_M

/** The steepest step between two samples, in metres per metre, that is believed to be a road. */
internal const val MAX_ROAD_HEIGHT_SLOPE = 0.5

// More than this share of dropped samples says the whole profile cannot be trusted.
private const val MAX_DROPPED_SHARE = 0.25

/**
 * The height in metres for a [reported] value. The engine counts in steps of [STOCK_HEIGHT_STEP_M]
 * above [ROAD_HEIGHT_FLOOR_M], the Karoo's road data in steps of [KAROO_HEIGHT_STEP_M].
 */
internal fun karooHeightM(reported: Double): Double =
    ROAD_HEIGHT_FLOOR_M +
        (reported - ROAD_HEIGHT_FLOOR_M) * (KAROO_HEIGHT_STEP_M / STOCK_HEIGHT_STEP_M)

/**
 * (distance m, height m) samples for the [reported] values of a road [roadLengthM] long. The values
 * are assumed to come one every [ROAD_HEIGHT_INTERVAL_M], the last at the road's end. That spacing
 * is not measured, so the count is checked against the road: null when the samples, spaced so, span
 * a length more than [MAX_ROAD_HEIGHT_SPAN_DIFF_M] from the road's. A bad junction shows as a step
 * steeper than [MAX_ROAD_HEIGHT_SLOPE]: both samples of every such step are dropped, and what is
 * left is returned as it is, for whoever interpolates the profile to bridge. Null when fewer than
 * two samples are left or more than a quarter were dropped.
 */
internal fun roadHeights(reported: List<Double>, roadLengthM: Double): List<Pair<Float, Float>>? {
    val spanM = (reported.size - 1) * ROAD_HEIGHT_INTERVAL_M.toDouble()
    val fitsTheRoad = abs(spanM - roadLengthM) <= MAX_ROAD_HEIGHT_SPAN_DIFF_M
    val samples = reported.mapIndexed { i, value ->
        min(i * ROAD_HEIGHT_INTERVAL_M.toDouble(), roadLengthM) to karooHeightM(value)
    }
    val dropped = BooleanArray(samples.size)
    for (i in 1 until samples.size) {
        val run = samples[i].first - samples[i - 1].first
        if (run <= 0.0) continue
        if (abs(samples[i].second - samples[i - 1].second) / run > MAX_ROAD_HEIGHT_SLOPE) {
            dropped[i - 1] = true
            dropped[i] = true
        }
    }
    val kept = samples.filterIndexed { i, _ -> !dropped[i] }
    if (!fitsTheRoad || kept.size < 2 || dropped.count { it } > samples.size * MAX_DROPPED_SHARE)
        return null
    return kept.map { (distance, height) -> distance.toFloat() to height.toFloat() }
}
