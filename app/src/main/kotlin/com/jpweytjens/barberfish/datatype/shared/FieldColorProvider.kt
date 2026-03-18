package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.glance.unit.ColorProvider

internal val whiteText = ColorProvider(Color.White)

private val COOLWARM_RED = Color(0xFFB40426)
private val COOLWARM_BLUE = Color(0xFF3B4CC0)

// factor: -1.0 = fully red, 0.0 = white (at threshold), +1.0 = fully blue
private fun thresholdColor(factor: Float): Color =
    if (factor >= 0f) lerp(Color.White, COOLWARM_BLUE, factor)
    else lerp(COOLWARM_RED, Color.White, factor + 1f)

internal fun FieldColor.toColorProvider(): ColorProvider =
    when (this) {
        is FieldColor.Default -> ColorProvider(Color.White)
        is FieldColor.Threshold -> ColorProvider(thresholdColor(factor))
        is FieldColor.Zone ->
            ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
    }

internal fun FieldColor.toBackgroundColorProvider(): ColorProvider? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Threshold -> ColorProvider(thresholdColor(factor))
        is FieldColor.Zone ->
            ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
    }

internal fun FieldColor.toColor(): Color? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Threshold -> thresholdColor(factor)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette)
    }
