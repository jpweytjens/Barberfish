package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FieldSizeConfig(
    val iconSize: Dp,
    val iconLabelGap: Dp,
    val cellPaddingH: Dp,
    val cellPaddingTop: Dp,
    val labelFontSize: TextUnit,
    val valueFontSizeBase: Int,
    val labelLineSpacing: Dp,
    val multilineIconTopPadding: Dp,
    val valueBottomPadding: Dp,
) {
    companion object {
        val STANDARD =
            FieldSizeConfig(
                iconSize = 17.dp,
                iconLabelGap = 6.dp,
                cellPaddingH = 4.dp,
                cellPaddingTop = 8.dp,
                labelFontSize = 17.sp,
                valueFontSizeBase = 49,
                labelLineSpacing = 4.dp,
                multilineIconTopPadding = 12.dp,
                valueBottomPadding = 5.dp,
            )
        val HUD =
            FieldSizeConfig(
                iconSize = 14.dp,
                iconLabelGap = 4.dp,
                cellPaddingH = 4.dp,
                cellPaddingTop = 6.dp,
                labelFontSize = 14.sp,
                valueFontSizeBase = 36,
                labelLineSpacing = 2.dp,
                multilineIconTopPadding = 8.dp,
                valueBottomPadding = 5.dp,
            )
    }
}
