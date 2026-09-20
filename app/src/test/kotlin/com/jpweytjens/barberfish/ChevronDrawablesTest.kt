package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class ChevronDrawablesTest {

    @Test
    fun hsluv_takes_the_barberfish_yellow() {
        assertEquals(
            gradeChevronDrawable(GradePalette.BARBERFISH),
            gradeChevronDrawable(GradePalette.HSLUV),
        )
    }

    @Test
    fun barberfish_and_karoo_share_the_karoo_yellow() {
        assertEquals(
            gradeChevronDrawable(GradePalette.KAROO),
            gradeChevronDrawable(GradePalette.BARBERFISH),
        )
    }
}
