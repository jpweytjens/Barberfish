package com.jpweytjens.barberfish

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpweytjens.barberfish.datatype.hudHeaderFontSp
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.headerPaint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A one-word HUD label, drawn at the size the HUD picks for it, fits the width it was sized for.
 */
@RunWith(AndroidJUnit4::class)
class HudHeaderFitTest {
    @Test
    fun one_word_label_never_overflows_its_width() {
        val density =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .resources
                .displayMetrics
                .density
        // From well below to above GRADE's width at 11 sp, in half-pixel steps.
        for (step in 80..200) {
            val availablePx = step / 2f
            val sp = hudHeaderFontSp("Grade", ViewSizeConfig.HUD_FOUR, availablePx, density)
            val drawnPx = headerPaint(sp * density).measureText("GRADE")
            assertTrue(
                "GRADE at $sp sp is $drawnPx px in $availablePx px",
                drawnPx <= availablePx,
            )
        }
    }
}
