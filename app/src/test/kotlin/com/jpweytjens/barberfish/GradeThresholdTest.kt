package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeThreshold
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeThresholdTest {

    @Test fun wahoo_threshold_is_4() =
        assertEquals(4.0, gradeThreshold(GradePalette.WAHOO), 0.001)

    @Test fun garmin_threshold_is_3() =
        assertEquals(3.0, gradeThreshold(GradePalette.GARMIN), 0.001)

    @Test fun hsluv_threshold_is_3() =
        assertEquals(3.0, gradeThreshold(GradePalette.HSLUV), 0.001)

    @Test fun karoo_threshold_is_4_6() =
        assertEquals(4.6, gradeThreshold(GradePalette.KAROO), 0.001)
}
