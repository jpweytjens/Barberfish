package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.AvgSpeedPrior
import com.jpweytjens.barberfish.datatype.shared.ETAInput
import com.jpweytjens.barberfish.datatype.shared.ETAState
import com.jpweytjens.barberfish.datatype.shared.blendedSpeed
import com.jpweytjens.barberfish.datatype.shared.computeRidingETA
import com.jpweytjens.barberfish.datatype.shared.initETAState
import com.jpweytjens.barberfish.datatype.shared.updateETAState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ETATest {

    private val defaultPrior = AvgSpeedPrior(speedKph = 25.0)
    private val disabledPrior = AvgSpeedPrior(speedKph = 0.0)

    private fun input(
        distanceRiddenM: Double = 0.0,
        elapsedTimeMs: Double = 0.0,
        distanceToDestM: Double = 50_000.0,
        pausedTimeMs: Double = 0.0,
        prior: AvgSpeedPrior = defaultPrior,
    ) = ETAInput(distanceRiddenM, elapsedTimeMs, distanceToDestM, pausedTimeMs, prior)

    // --- initETAState ---

    @Test
    fun init_setsEwmasToPrior() {
        val state = initETAState(defaultPrior)
        assertEquals(25.0 / 3.6, state.ewmaFastMs, 0.001)
        assertEquals(25.0 / 3.6, state.ewmaSlowMs, 0.001)
    }

    @Test
    fun init_disabledPrior_setsZero() {
        val state = initETAState(disabledPrior)
        assertEquals(0.0, state.ewmaFastMs, 0.001)
        assertEquals(0.0, state.ewmaSlowMs, 0.001)
    }

    // --- updateETAState ---

    @Test
    fun update_noMovement_stateUnchanged() {
        val state = initETAState(defaultPrior)
        val updated = updateETAState(input(), state)
        assertEquals(state, updated)
    }

    @Test
    fun update_withMovement_ewmasShift() {
        val state = initETAState(defaultPrior)
        // Simulate: 100m in 10s = 10 m/s (36 km/h), faster than 25 km/h prior
        val updated = updateETAState(
            input(distanceRiddenM = 100.0, elapsedTimeMs = 10_000.0),
            state,
        )
        // Both EWMAs should shift toward 10 m/s (above prior of 6.944 m/s)
        assertTrue(updated.ewmaFastMs > state.ewmaFastMs)
        assertTrue(updated.ewmaSlowMs > state.ewmaSlowMs)
        // Fast should shift more than slow
        assertTrue(updated.ewmaFastMs > updated.ewmaSlowMs)
    }

    @Test
    fun update_tracksDeltas() {
        val state = initETAState(defaultPrior)
        val s1 = updateETAState(
            input(distanceRiddenM = 100.0, elapsedTimeMs = 10_000.0),
            state,
        )
        assertEquals(10_000.0, s1.prevElapsedTimeMs, 0.001)
        assertEquals(100.0, s1.prevDistanceRiddenM, 0.001)

        // Second update: another 100m in another 10s
        val s2 = updateETAState(
            input(distanceRiddenM = 200.0, elapsedTimeMs = 20_000.0),
            s1,
        )
        assertEquals(20_000.0, s2.prevElapsedTimeMs, 0.001)
        assertEquals(200.0, s2.prevDistanceRiddenM, 0.001)
    }

    @Test
    fun update_paused_noChange() {
        val state = initETAState(defaultPrior)
        val s1 = updateETAState(
            input(distanceRiddenM = 1000.0, elapsedTimeMs = 120_000.0),
            state,
        )
        // Paused: elapsed time doesn't advance (ELAPSED_TIME excludes pauses), distance same
        val s2 = updateETAState(
            input(distanceRiddenM = 1000.0, elapsedTimeMs = 120_000.0),
            s1,
        )
        assertEquals(s1.ewmaFastMs, s2.ewmaFastMs, 0.0001)
        assertEquals(s1.ewmaSlowMs, s2.ewmaSlowMs, 0.0001)
    }

    // --- computeRidingETA ---

    @Test
    fun eta_purePrior_50km() {
        val state = initETAState(defaultPrior)
        // 50 km at 25 km/h = 7200 seconds
        val eta = computeRidingETA(input(), state)
        assertEquals(7200L, eta)
    }

    @Test
    fun eta_zeroDistanceToDest() {
        val state = initETAState(defaultPrior)
        assertEquals(0L, computeRidingETA(input(distanceToDestM = 0.0), state))
    }

    @Test
    fun eta_negativeDistanceToDest() {
        val state = initETAState(defaultPrior)
        assertEquals(0L, computeRidingETA(input(distanceToDestM = -100.0), state))
    }

    @Test
    fun eta_disabledPrior_noMovement_notComputable() {
        val state = initETAState(disabledPrior)
        assertEquals(-1L, computeRidingETA(input(prior = disabledPrior), state))
    }

    @Test
    fun eta_afterRiding_reflectsEwma() {
        var state = initETAState(defaultPrior)
        // Ride at 30 km/h (8.333 m/s) for 10 minutes in 1-second steps
        for (i in 1..600) {
            state = updateETAState(
                input(
                    distanceRiddenM = 8.333 * i,
                    elapsedTimeMs = i * 1000.0,
                    distanceToDestM = 50_000.0,
                ),
                state,
            )
        }
        val speed = blendedSpeed(state)
        // Blended speed should be between prior (6.944) and actual (8.333)
        assertTrue(speed > 25.0 / 3.6)
        assertTrue(speed < 30.0 / 3.6)
        // Fast EWMA should be closer to 8.333 than slow
        assertTrue(state.ewmaFastMs > state.ewmaSlowMs)
    }

    @Test
    fun eta_pausedTimeDoesNotAffectRidingETA() {
        val state = initETAState(defaultPrior)
        val withoutPause = computeRidingETA(input(distanceToDestM = 50_000.0), state)
        val withPause = computeRidingETA(
            input(distanceToDestM = 50_000.0, pausedTimeMs = 30.0 * 60 * 1000),
            state,
        )
        assertEquals(withoutPause, withPause)
    }
}
