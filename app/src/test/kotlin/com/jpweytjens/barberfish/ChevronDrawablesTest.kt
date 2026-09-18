package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChevronDrawablesTest {

    @Test
    fun hsluv_takes_the_white_chevron() {
        assertEquals(R.drawable.ic_climber_chevron, gradeChevronDrawable(GradePalette.HSLUV))
    }

    @Test
    fun every_other_palette_takes_a_yellow_chevron() {
        GradePalette.entries
            .filter { it != GradePalette.HSLUV }
            .forEach { palette ->
                assertNotEquals(
                    "$palette should draw its own yellow, not white",
                    R.drawable.ic_climber_chevron,
                    gradeChevronDrawable(palette),
                )
            }
    }

    @Test
    fun barberfish_and_karoo_share_the_karoo_yellow() {
        assertEquals(
            gradeChevronDrawable(GradePalette.KAROO),
            gradeChevronDrawable(GradePalette.BARBERFISH),
        )
    }
}
