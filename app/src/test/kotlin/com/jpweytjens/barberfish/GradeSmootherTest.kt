package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.GRADE_BASELINE_M
import com.jpweytjens.barberfish.datatype.shared.GradeSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GradeSmootherTest {

    /** Feed (elev, dist, speed) in order; return outputs in the same order. */
    private fun run(samples: List<Triple<Float, Float, Float>>): List<Float?> {
        val s = GradeSmoother()
        return samples.map { (e, d, v) -> s.update(e, d, v) }
    }

    // --- Warm-up: a short ride that has not yet accumulated baseline length must return null. ---

    @Test
    fun construction_doesNotThrow() {
        // sanity: just constructing it must not throw
        GradeSmoother()
    }

    @Test
    fun returnsNull_whileBelowBaseline() {
        // 10 moving samples spanning 9 m of road (well under the 30 m baseline) → all null.
        val samples = (0..9).map { i ->
            Triple(100.0f, i.toFloat(), 5.0f) // flat, advancing 1 m per tick at 5 m/s
        }
        val out = run(samples)
        assertEquals(10, out.size)
        out.forEach { assertNull(it) }
    }

    // --- Constant-grade tests ---

    @Test
    fun flatRoad_returnsZeroOnceBaselineReached() {
        // 35 m of flat road at 5 m/s: dist = 0,1,2,...,34, elev = 100 throughout.
        val samples = (0..34).map { i -> Triple(100.0f, i.toFloat(), 5.0f) }
        val out = run(samples)
        val finalGrade = out.last()
        assertNotNull(finalGrade)
        assertEquals(0.0f, finalGrade!!, 0.01f)
    }

    @Test
    fun fivePercentClimb_returnsFivePercent() {
        // 60 m of 5% climb at 5 m/s: dist = 0,1,...,59, elev = 100 + 0.05 * dist.
        val samples = (0..59).map { i ->
            Triple(100.0f + 0.05f * i, i.toFloat(), 5.0f)
        }
        val out = run(samples)
        val finalGrade = out.last()
        assertNotNull(finalGrade)
        assertEquals(5.0f, finalGrade!!, 0.01f)
    }

    @Test
    fun negativeFivePercentDescent_returnsNegativeFivePercent() {
        val samples = (0..59).map { i ->
            Triple(100.0f - 0.05f * i, i.toFloat(), 5.0f)
        }
        val out = run(samples)
        val finalGrade = out.last()
        assertNotNull(finalGrade)
        assertEquals(-5.0f, finalGrade!!, 0.01f)
    }

    // --- Window sliding ---

    @Test
    fun slopeTracksLeadingEdge_whenWindowSlidesPastOldRoad() {
        // 60 m of flat road, then 60 m of 10% climb.
        val flat = (0..59).map { i -> Triple(100.0f, i.toFloat(), 5.0f) }
        val climb = (1..60).map { i ->
            Triple(100.0f + 0.10f * i, 59.0f + i.toFloat(), 5.0f)
        }
        val samples = flat + climb
        val out = run(samples)
        // Final 30 m of road are pure 10% climb → output should equal 10% within tolerance.
        val finalGrade = out.last()
        assertNotNull(finalGrade)
        assertEquals(10.0f, finalGrade!!, 0.1f)
    }

    @Test
    fun ringBufferDoesNotOverflow_onLongRide() {
        // 5 minutes at 5 m/s = 1500 m / 1500 samples. Capacity is 64.
        // Eviction must keep buffer ≤ capacity throughout.
        val s = GradeSmoother()
        repeat(1500) { i ->
            s.update(100.0f, i.toFloat(), 5.0f) // throws if ring buffer overflows
        }
    }

    // --- Moving guard ---

    @Test
    fun pausedSamples_returnNullAndDoNotPollute() {
        // 60 m of flat road, then 30 stopped ticks at the same coordinates, then resume.
        val moving = (0..59).map { i -> Triple(100.0f, i.toFloat(), 5.0f) }
        val stopped = (1..30).map { Triple(100.0f, 59.0f, 0.0f) }       // speed 0 → not moving
        val resume  = (1..10).map { i -> Triple(100.0f, 59.0f + i.toFloat(), 5.0f) }
        val out = run(moving + stopped + resume)

        val pausedSlice = out.subList(moving.size, moving.size + stopped.size)
        pausedSlice.forEach { assertNull(it) }

        // After resume, since elevation didn't change during the pause, no barrier should fire.
        // The buffer still holds 30 m of pre-pause road, so the smoother should return ~0% promptly.
        val firstAfterResume = out[moving.size + stopped.size]
        assertNotNull(firstAfterResume)
        assertEquals(0.0f, firstAfterResume!!, 0.01f)
    }

    @Test
    fun belowSpeedThreshold_skipsBufferEvenIfDistanceAdvances() {
        // Pathological input: distance advances while speed is below threshold (e.g. GPS drift).
        // The sample must be dropped.
        val s = GradeSmoother()
        repeat(40) { i ->
            s.update(100.0f, i.toFloat(), 0.3f) // speed 0.3 m/s < 0.5 threshold
        }
        // No moving samples → still null after enough distance.
        assertNull(s.update(100.0f, 40.0f, 0.3f))
    }

    // --- Barrier on elev drift across pause ---

    @Test
    fun pauseWithElevDrift_insertsBarrier_and_postResumeWarmsUp() {
        // 60 m flat at 100 m. Stop. During the stop, baro reports 105 m. Resume.
        val moving = (0..59).map { i -> Triple(100.0f, i.toFloat(), 5.0f) }
        // Pause: speed drops to 0, and during the pause the elevation drifts to 105.
        // The transition-into-pause uses elev[i-1] (= 100) as the snapshot. During the pause
        // we report elev = 105; that doesn't matter — it's the elev on RESUME that's compared.
        val stopped = listOf(
            Triple(100.0f, 59.0f, 0.0f),  // first not-moving sample; snapshot = 100 (last moving)
            Triple(102.0f, 59.0f, 0.0f),
            Triple(105.0f, 59.0f, 0.0f),
        )
        // Resume with elev 105 — differs from snapshot 100 → barrier at dist = 59.
        val resume = (1..40).map { i ->
            Triple(105.0f, 59.0f + i.toFloat(), 5.0f)
        }
        val out = run(moving + stopped + resume)

        // First post-resume sample: barrier triggers, all pre-barrier samples evicted.
        // The smoother must return null until the buffer accumulates a fresh 30 m of road.
        val firstAfterResume = out[moving.size + stopped.size]
        assertNull(firstAfterResume)

        // After 30 more moving samples (covering 30 m at 1 m / sample), we should have a value again.
        val afterWarmup = out[moving.size + stopped.size + 30]
        assertNotNull(afterWarmup)
        assertEquals(0.0f, afterWarmup!!, 0.01f)
    }

    @Test
    fun snapshotUsesLastMovingElev_notFirstStoppedElev() {
        // Mimic a recording gap: the rider was moving at 100 m, the device went idle for 60 s
        // (during which the rider walked up 10 m), and resumed at 110 m. The FIRST not-moving
        // sample already reflects the new altitude — but lastMovingElev should still be 100.
        val moving = (0..59).map { i -> Triple(100.0f, i.toFloat(), 5.0f) }
        // Transition: first not-moving sample is already at 110 (post-gap elevation).
        val stopped = listOf(
            Triple(110.0f, 59.0f, 0.0f), // snapshot must be 100 (lastMovingElev), NOT 110.
        )
        // Resume with elev 110 — differs from correct snapshot 100 → barrier at dist 59.
        val resume = (1..40).map { i -> Triple(110.0f, 59.0f + i.toFloat(), 5.0f) }
        val out = run(moving + stopped + resume)

        val firstAfterResume = out[moving.size + stopped.size]
        assertNull(firstAfterResume) // barrier fired

        val afterWarmup = out[moving.size + stopped.size + 30]
        assertNotNull(afterWarmup)
        assertEquals(0.0f, afterWarmup!!, 0.01f)
    }

    // --- Numerical stability ---

    @Test
    fun longRide_doesNotLoseSignificance_at_100km() {
        // Simulate 100 km of flat-then-5%-climb road. The OLS denominator
        // `n * sumXX - sumX * sumX` becomes a difference of two ~1e15-scale doubles, which is
        // why sumX/sumY/sumXY/sumXX are stored as Double (~16 sig digits). Verify the slope
        // is still accurate to 0.05% at 100 km.
        val s = GradeSmoother()
        var result: Float? = null
        // 95 km flat
        for (i in 0..94_999) {
            s.update(100.0f, i.toFloat(), 5.0f)
        }
        // 5 km of 5% climb
        for (i in 1..5_000) {
            result = s.update(100.0f + 0.05f * i, 94_999.0f + i.toFloat(), 5.0f)
        }
        assertNotNull(result)
        assertEquals(5.0f, result!!, 0.05f)
    }
}
