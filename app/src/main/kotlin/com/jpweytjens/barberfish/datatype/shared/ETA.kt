package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.exp

const val EWMA_TAU_FAST_S = 300.0
const val EWMA_TAU_SLOW_S = 3600.0
const val EWMA_BLEND_W = 0.3

data class AvgSpeedPrior(
    val speedKph: Double = 25.0,
)

data class ETAInput(
    val distanceRiddenM: Double,
    val elapsedTimeMs: Double,
    val distanceToDestM: Double,
    val pausedTimeMs: Double,
    val prior: AvgSpeedPrior,
)

data class ETAState(
    val ewmaFastMs: Double,
    val ewmaSlowMs: Double,
    val prevElapsedTimeMs: Double = 0.0,
    val prevDistanceRiddenM: Double = 0.0,
)

fun initETAState(prior: AvgSpeedPrior): ETAState {
    val priorMs = if (prior.speedKph > 0) prior.speedKph / 3.6 else 0.0
    return ETAState(ewmaFastMs = priorMs, ewmaSlowMs = priorMs)
}

fun updateETAState(input: ETAInput, state: ETAState): ETAState {
    val dtMs = input.elapsedTimeMs - state.prevElapsedTimeMs
    val dDistM = input.distanceRiddenM - state.prevDistanceRiddenM

    if (dtMs <= 0 || dDistM <= 0) return state

    val instantSpeedMs = dDistM / (dtMs / 1000.0)
    val alphaFast = 1.0 - exp(-dtMs / 1000.0 / EWMA_TAU_FAST_S)
    val alphaSlow = 1.0 - exp(-dtMs / 1000.0 / EWMA_TAU_SLOW_S)

    return state.copy(
        ewmaFastMs = alphaFast * instantSpeedMs + (1.0 - alphaFast) * state.ewmaFastMs,
        ewmaSlowMs = alphaSlow * instantSpeedMs + (1.0 - alphaSlow) * state.ewmaSlowMs,
        prevElapsedTimeMs = input.elapsedTimeMs,
        prevDistanceRiddenM = input.distanceRiddenM,
    )
}

fun blendedSpeed(state: ETAState): Double =
    EWMA_BLEND_W * state.ewmaFastMs + (1.0 - EWMA_BLEND_W) * state.ewmaSlowMs

fun computeRidingETA(input: ETAInput, state: ETAState): Long {
    if (input.distanceToDestM <= 0) return 0L
    val speed = blendedSpeed(state)
    if (speed.isNaN() || speed <= 0) return -1L
    return (input.distanceToDestM / speed).toLong()
}
