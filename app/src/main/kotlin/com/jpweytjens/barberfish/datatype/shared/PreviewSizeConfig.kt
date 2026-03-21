package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PreviewSizeConfig(
    val paddingStart: Dp,
    val paddingEnd: Dp,
    val paddingTop: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val valueFontSize: TextUnit,
    val valueBottomPadding: Dp,
) {
    companion object {
        val SINGLE =
            PreviewSizeConfig(
                paddingStart = 2.dp,
                paddingEnd = 4.dp,
                paddingTop = 6.dp,
                headerIconSize = 16.dp,
                headerIconLabelGap = 4.dp,
                headerFontSize = 16.sp,
                valueFontSize = 36.sp,
                valueBottomPadding = 4.dp,
            )
        val HUD =
            PreviewSizeConfig(
                paddingStart = 4.dp,
                paddingEnd = 4.dp,
                paddingTop = 4.dp,
                headerIconSize = 12.dp,
                headerIconLabelGap = 4.dp,
                headerFontSize = 10.sp,
                valueFontSize = 32.sp,
                valueBottomPadding = 4.dp,
            )
    }
}
