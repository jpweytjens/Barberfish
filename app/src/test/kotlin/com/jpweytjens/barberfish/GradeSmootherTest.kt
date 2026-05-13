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
}
