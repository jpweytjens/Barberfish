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
//   20       ≥ 12     —        12.0 sp  HUD slot (1/3 of 60-wide cell)
//   15       ≥ 12     —        10.0 sp  4-col HUD slot (1/4 of 60-wide cell)
//
// Icon size equals labelSize in the native app (same px value, width = height).
// Native root cell padding is 0dp. Header wraps content; value fills remaining space, centered.
fun ViewConfig.toViewSizeConfig(
    colSpanOverride: Int? = null,
    textSizeOverride: Int? = null,
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
            colSpan == 15 && rowSpan >= 12 -> 10.0f // 4-col HUD slot (1/4)
            else -> 15.5f
        }
    val gapDp = maxOf(2, (labelSp * 0.2f).toInt())
    // Wide (1-col) cells have short labels that fit on one line; colSpan=20 HUD slots also use 1.
    val labelMaxLines = if (colSpan != 30) 1 else 2
    // Wide cells have more horizontal space; scale font only beyond 6 chars (vs 4 for narrow).
    val baseChars = if (colSpan == 60) 6 else 4
    // Move value up in FrameLayout
    val translationYPx: Float =
        when {
            colSpan == 60 && rowSpan >= 20 -> 16f
            colSpan == 60 && rowSpan >= 15 -> 14f
            colSpan == 60 && rowSpan >= 12 -> 10f
            colSpan == 30 && rowSpan >= 15 -> 27f
            colSpan == 30 && rowSpan >= 12 -> 24f
            colSpan == 20 && rowSpan >= 15 -> 27f
            colSpan == 20 && rowSpan >= 12 -> 24f
            colSpan == 15 && rowSpan >= 15 -> 22f
            colSpan == 15 && rowSpan >= 12 -> 20f
            else -> 0f
        }
    return ViewSizeConfig.STANDARD.copy(
        valueFontSizeBase = textSizeEff.coerceAtLeast(20),
        headerFontSize = labelSp.sp,
        headerIconSize = labelSp.dp,
        headerIconLabelGap = gapDp.dp,
        labelMaxLines = labelMaxLines,
        baseChars = baseChars,
        valueTranslationY = translationYPx,
    )
}

data class ViewSizeConfig(
    val paddingH: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val labelMaxLines: Int,
    val baseChars: Int,
    val valueFontSizeBase: Int,
    val valueTranslationY: Float,
) {
    companion object {
        val STANDARD =
            ViewSizeConfig(
                paddingH = 4.dp,
                headerIconSize = 17.dp,
                headerIconLabelGap = 6.dp,
                headerFontSize = 17.sp,
                labelMaxLines = 2,
                baseChars = 4,
                valueFontSizeBase = 49,
                valueTranslationY = 0f,
            )
    }
}
