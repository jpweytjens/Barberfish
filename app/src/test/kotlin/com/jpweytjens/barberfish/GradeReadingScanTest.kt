package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.GradeReading
import com.jpweytjens.barberfish.datatype.shared.gradeReadingReducer
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeReadingScanTest {

    private fun reduce(initial: GradeReading, inputs: List<Float?>): GradeReading {
        var acc = initial
        for (x in inputs) acc = gradeReadingReducer(acc, x)
        return acc
    }

    @Test fun startsUnavailable_andStaysUnavailable_untilFirstFresh() {
        val result = reduce(GradeReading.Unavailable, listOf(null, null, null))
        assertEquals(GradeReading.Unavailable, result)
    }

    @Test fun firstFresh_producesFresh() {
        val result = reduce(GradeReading.Unavailable, listOf(null, 5.0f))
        assertEquals(GradeReading.Fresh(5.0f), result)
    }

    @Test fun freshThenNull_producesStaleHoldingLastValue() {
        val result = reduce(GradeReading.Unavailable, listOf(5.0f, null))
        assertEquals(GradeReading.Stale(5.0f), result)
    }

    @Test fun staleStaysStaleAtSameValue_acrossMultipleNulls() {
        val result = reduce(GradeReading.Unavailable, listOf(5.0f, null, null, null))
        assertEquals(GradeReading.Stale(5.0f), result)
    }

    @Test fun freshOverwritesStale_withNewValue() {
        val result = reduce(GradeReading.Unavailable, listOf(5.0f, null, 7.5f))
        assertEquals(GradeReading.Fresh(7.5f), result)
    }
}
