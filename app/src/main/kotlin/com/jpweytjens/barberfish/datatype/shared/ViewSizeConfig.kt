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
//
// Icon size equals labelSize in the native app (same px value, width = height).
// Native root cell padding is 0dp. Header wraps content; value fills remaining space, centered.
fun ViewConfig.toViewSizeConfig(): ViewSizeConfig {
    val colSpan = gridSize.first
    val rowSpan = gridSize.second
    val labelSp: Float =
        when {
            colSpan == 60 && rowSpan >= 15 -> 19.2f // 36 px
            colSpan == 60 && rowSpan >= 12 -> 17.6f // 33 px
            colSpan == 30 && rowSpan >= 15 -> 17.6f // 33 px
            colSpan == 30 && rowSpan >= 12 -> 15.5f // 29 px
            else -> 15.5f
        }
    val gapDp = maxOf(2, (labelSp * 0.2f).toInt())
    // Native dataTranslationY (px) from hhv5.d.hha() — negative = move value up.
    val translationYPx: Float =
        when {
            colSpan == 60 && rowSpan >= 18 -> -12f
            colSpan == 60 && rowSpan >= 12 -> -10f
            colSpan == 30 && rowSpan >= 15 -> -26f
            colSpan == 30 && rowSpan >= 12 -> -9f
            else -> 0f
        }
    return ViewSizeConfig.STANDARD.copy(
        valueFontSizeBase = textSize.coerceAtLeast(20),
        headerFontSize = labelSp.sp,
        headerIconSize = labelSp.dp,
        headerIconLabelGap = gapDp.dp,
        valueTranslationY = translationYPx,
    )
}

data class ViewSizeConfig(
    val paddingH: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val headerLineSpacing: Dp,
    val headerIconTopPadding: Dp,
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
                headerLineSpacing = 2.dp,
                headerIconTopPadding = 12.dp,
                valueFontSizeBase = 49,
                valueTranslationY = 0f,
            )
        val HUD =
            ViewSizeConfig(
                paddingH = 4.dp,
                headerIconSize = 12.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 12.sp,
                headerLineSpacing = 2.dp,
                headerIconTopPadding = 10.dp,
                valueFontSizeBase = 36,
                valueTranslationY = 0f,
            )
    }
}
