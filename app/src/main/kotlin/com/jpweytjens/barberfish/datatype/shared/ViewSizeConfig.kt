package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ViewSizeConfig(
    val paddingH: Dp,
    val paddingTop: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val headerLineSpacing: Dp,
    val headerIconTopPadding: Dp,
    val valueFontSizeBase: Int,
    val valueBottomPadding: Dp,
) {
    companion object {
        val STANDARD =
            ViewSizeConfig(
                paddingH = 4.dp,
                paddingTop = 8.dp,
                headerIconSize = 17.dp,
                headerIconLabelGap = 6.dp,
                headerFontSize = 17.sp,
                headerLineSpacing = 4.dp,
                headerIconTopPadding = 12.dp,
                valueFontSizeBase = 49,
                valueBottomPadding = 4.dp,
            )
        val HUD =
            ViewSizeConfig(
                paddingH = 4.dp,
                paddingTop = 6.dp,
                headerIconSize = 14.dp,
                headerIconLabelGap = 4.dp,
                headerFontSize = 14.sp,
                headerLineSpacing = 2.dp,
                headerIconTopPadding = 8.dp,
                valueFontSizeBase = 36,
                valueBottomPadding = 4.dp,
            )
    }
}
