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
                valueFontSize = 28.sp,
                valueBottomPadding = 4.dp,
            )
        val HUD_FOUR =
            PreviewSizeConfig(
                paddingStart = 2.dp,
                paddingEnd = 2.dp,
                paddingTop = 4.dp,
                headerIconSize = 10.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 9.sp,
                valueFontSize = 24.sp,
                valueBottomPadding = 2.dp,
            )
    }
}
