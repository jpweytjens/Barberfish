package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

internal val whiteText = ColorProvider(Color.White)

internal fun FieldColor.toColorProvider(): ColorProvider =
    when (this) {
        is FieldColor.Default -> ColorProvider(Color.White)
        is FieldColor.Threshold ->
            ColorProvider(if (above) Color(0xFF81C784) else Color(0xFFE57373))
        is FieldColor.Zone ->
            ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
    }

internal fun FieldColor.toBackgroundColorProvider(): ColorProvider? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Threshold ->
            ColorProvider(if (above) Color(0xFF81C784) else Color(0xFFE57373))
        is FieldColor.Zone ->
            ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
    }

internal fun FieldColor.toColor(): Color? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Threshold -> if (above) Color(0xFF81C784) else Color(0xFFE57373)
        is FieldColor.Zone -> if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette)
    }
