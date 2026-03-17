package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.extension.ZoneColorMode

private val ICON_TINT_TEAL = Color(0xFF31E09A)
private val ICON_TINT_WHITE = Color(0xFFFFFFFF)
private val ICON_TINT_BLACK = Color(0xFF000000)

data class ColorConfig(
    val valueText: ColorProvider,
    val labelText: ColorProvider,
    val iconTint: Color,
    val background: ColorProvider?, // null = transparent cell
)

internal fun FieldColor.toColorConfig(colorMode: ZoneColorMode): ColorConfig {
    val bg = if (colorMode == ZoneColorMode.BACKGROUND) toBackgroundColorProvider() else null
    val onBg = bg != null
    val value =
        when {
            onBg -> ColorProvider(Color.Black)
            colorMode == ZoneColorMode.TEXT -> toColorProvider()
            else -> whiteText // BACKGROUND mode, no zone
        }
    return ColorConfig(
        valueText = value,
        labelText = ColorProvider(if (onBg) Color.Black else Color.White),
        iconTint = if (onBg) ICON_TINT_BLACK else ICON_TINT_TEAL,
        background = bg,
    )
}
