package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeFillRange
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeThresholdTest {

    @Test fun wahoo_climb_threshold_is_4() {
        val range = gradeFillRange(GradePalette.WAHOO)
        assertEquals(4.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }

    @Test fun garmin_climb_threshold_is_3() {
        val range = gradeFillRange(GradePalette.GARMIN)
        assertEquals(3.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }

    @Test fun hsluv_climb_threshold_is_3() {
        val range = gradeFillRange(GradePalette.HSLUV)
        assertEquals(3.0, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }

    @Test fun karoo_climb_threshold_is_4_6() {
        val range = gradeFillRange(GradePalette.KAROO)
        assertEquals(4.6, range.posMin!!, 0.001)
        assertNull(range.negMax)
    }
}
