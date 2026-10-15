package com.jpweytjens.barberfish

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.fontSizeForCell
import com.jpweytjens.barberfish.datatype.shared.headerPaint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A HUD label sized by [fontSizeForCell] must draw without breaking a word: every word, in capitals
 * and in [headerPaint] at the chosen size, fits the label width. Measuring in another font or case
 * split "CADENCE" mid-word in a 4-column slot.
 */
@RunWith(AndroidJUnit4::class)
class HeaderLabelFitTest {

    private val labels =
        listOf("Speed", "HR", "3s Power", "Cadence", "Avg Speed", "Lap Avg Power", "LL Avg Power")

    @Test
    fun everyWordFitsTheLabelWidth() {
        val density =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .resources
                .displayMetrics
                .density
        // Every base size a HUD preset could be tuned to, not only today's, across slot widths.
        for (preset in listOf(ViewSizeConfig.HUD_THREE, ViewSizeConfig.HUD_FOUR)) {
            for ((baseSp, widthDp) in
                (10..16).flatMap { sp -> (40..100 step 5).map { sp to it } }) {
                val widthPx = widthDp * density
                for (label in labels) {
                    val upper = label.uppercase()
                    val (sp, _) =
                        fontSizeForCell(
                            upper,
                            baseSp,
                            widthPx,
                            density,
                            wrapThresholdSp = preset.wrapThresholdSp,
                            paint = headerPaint(0f),
                        )
                    val paint = headerPaint(sp * density)
                    for (word in upper.split(' ')) {
                        val drawn = paint.measureText(word)
                        assertTrue(
                            "'$word' at $sp sp is ${drawn}px, wider than ${widthPx}px",
                            drawn <= widthPx,
                        )
                    }
                }
            }
        }
    }
}
