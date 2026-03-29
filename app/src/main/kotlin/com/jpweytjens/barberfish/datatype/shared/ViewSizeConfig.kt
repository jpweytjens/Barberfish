package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hammerhead.karooext.models.ViewConfig

// Native rideapp (hhv5.d.hha) uses a hardcoded px lookup keyed on (colSpan, rowSpan).
// Converted to sp at Karoo 3 density (1.875 = 300 dpi / 160):
//
//   colSpan  rowSpan  labelPx  labelSp  example          textSize (sp)
//   60       ≥ 15     36 px    19.2 sp  1-col 3/4-row    69 – 96
//   60       ≥ 12     33 px    17.6 sp  1-col 5-row      55
//   30       ≥ 15     33 px    17.6 sp  2-col 4-row      50
//   30       ≥ 12     29 px    15.5 sp  2-col 5-row      47
//   20       ≥ 12     —        12.0 sp  HUD slot (1/3 of 60-wide cell)  [dynamic base]
//   15       ≥ 12     —        11.0 sp  4-col HUD slot (1/4 of 60-wide cell)  [dynamic base]
//
// Icon size equals labelSize in the native app (same px value, width = height).
// Native root cell padding is 0dp. Header wraps content; value fills remaining space, centered.
fun ViewConfig.toViewSizeConfig(
    colSpanOverride: Int? = null,
    textSizeOverride: Int? = null,
    sparklineActive: Boolean = false,
): ViewSizeConfig {
    val colSpan = colSpanOverride ?: gridSize.first
    val rowSpan = gridSize.second
    val textSizeEff = textSizeOverride ?: textSize
    val labelSp: Float =
        when {
            colSpan == 60 && rowSpan >= 15 -> 19.2f // 36 px
            colSpan == 60 && rowSpan >= 12 -> 17.6f // 33 px
            colSpan == 30 && rowSpan >= 15 -> 17.6f // 33 px
            colSpan == 30 && rowSpan >= 12 -> 15.5f // 29 px
            colSpan == 20 && rowSpan >= 12 -> 12.0f // HUD slot (1/3)
            colSpan == 15 && rowSpan >= 12 -> 11.0f // 4-col HUD slot (1/4)
            else -> 15.5f
        }
    val gapDp = maxOf(2, (labelSp * 0.2f).toInt())
    // Wide (1-col) cells have short labels that fit on one line; colSpan=20 HUD slots also use 1.
    val labelMaxLines = if (colSpan != 30) 1 else 2
    // Threshold below which single-line shrinks enough to warrant 2-line wrapping.
    // Lower for narrow HUD slots (small font is acceptable there).
    val wrapThresholdSp = when {
        colSpan == 60 -> 22
        colSpan == 30 -> 18
        colSpan == 20 -> 14
        else          -> 12  // 4-col HUD
    }
    // Move value up in FrameLayout
    val translationYPx: Float =
        when {
            colSpan == 60 && rowSpan >= 20 -> 16f
            colSpan == 60 && rowSpan >= 15 -> 14f
            colSpan == 60 && rowSpan >= 12 -> 10f
            colSpan == 30 && rowSpan >= 15 -> 27f
            colSpan == 30 && rowSpan >= 12 -> 24f
            colSpan == 20 && rowSpan >= 18 && sparklineActive -> 55f
            colSpan == 20 && rowSpan >= 15 -> 27f
            colSpan == 20 && rowSpan >= 12 -> 24f
            colSpan == 15 && rowSpan >= 18 && sparklineActive -> 60f
            colSpan == 15 && rowSpan >= 15 -> 22f
            colSpan == 15 && rowSpan >= 12 -> 20f
            else -> 0f
        }
    return ViewSizeConfig.STANDARD.copy(
        colSpan = colSpan,
        valueFontSizeBase = textSizeEff.coerceAtLeast(20),
        headerFontSize = labelSp.sp,
        headerIconSize = labelSp.dp,
        headerIconLabelGap = gapDp.dp,
        labelMaxLines = labelMaxLines,
        wrapThresholdSp = wrapThresholdSp,
        valueTranslationY = translationYPx,
    )
}

data class ViewSizeConfig(
    val colSpan: Int,
    val paddingH: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val labelMaxLines: Int,
    val wrapThresholdSp: Int,
    val valueFontSizeBase: Int,
    val valueTranslationY: Float,
    val cellWidthPxOverride: Float? = null,
) {
    companion object {
        val STANDARD =
            ViewSizeConfig(
                colSpan = 30,
                paddingH = 4.dp,
                headerIconSize = 17.dp,
                headerIconLabelGap = 6.dp,
                headerFontSize = 17.sp,
                labelMaxLines = 2,
                wrapThresholdSp = 18,
                valueFontSizeBase = 49,
                valueTranslationY = 0f,
            )

        // Config-screen preview: HUD 3-column slots
        val PREVIEW_HUD_THREE =
            ViewSizeConfig(
                colSpan = 20,
                paddingH = 4.dp,
                headerIconSize = 12.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 12.sp,
                labelMaxLines = 2,
                wrapThresholdSp = 14,
                valueFontSizeBase = 28,
                valueTranslationY = 7.5f, // 4dp × 1.875 density
            )

        // Config-screen preview: HUD 4-column slots
        val PREVIEW_HUD_FOUR =
            ViewSizeConfig(
                colSpan = 15,
                paddingH = 4.dp,
                headerIconSize = 11.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 9.sp,
                labelMaxLines = 2,
                wrapThresholdSp = 12,
                valueFontSizeBase = 20,
                valueTranslationY = 7.5f, // 4dp × 1.875 density
            )
    }
}
