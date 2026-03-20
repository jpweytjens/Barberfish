package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.extension.ZoneColorMode
import kotlin.math.sqrt

// Named palette colors
private val ERROR_RED = Color(0xFFFF5252)
private val MUTED_GREY = Color(0xFF7D7D7D)
private val ICON_TINT_TEAL = Color(0xFF31E09A)
private val ICON_TINT_BLACK = Color(0xFF000000)

// RdYlGn color map (single threshold) — neutral center is white
internal val RDYLGN_RED = Color(0xFFD73027)
internal val RDYLGN_GREEN = Color(0xFF1A9850)

// Danger zone color map (min/max mode) — light amber so the whitish gradient reads clearly
internal val DANGER_ORANGE = Color(0xFFFFA726)

internal val whiteText = ColorProvider(Color.White)

// sqrt curve pushes color out quickly: at 10% of range, ~31% saturation; at 1%, ~10%
// factor: -1.0 = fully red, 0.0 = white (at threshold), +1.0 = fully green
private fun thresholdColor(factor: Float): Color =
    if (factor >= 0f) lerp(Color.White, RDYLGN_GREEN, sqrt(factor))
    else lerp(Color.White, RDYLGN_RED, sqrt(-factor))

// outsideFactor > 0: ORANGE → RED (outside boundary)
// outsideFactor == 0, hasSafeZone: GREEN → ORANGE (inside, approaching boundary)
// outsideFactor == 0, !hasSafeZone: WHITE → ORANGE (one-sided safe zone)
// sqrt applied throughout for perceptual uniformity
private fun dangerZoneColor(
    outsideFactor: Float,
    borderProximity: Float,
    hasSafeZone: Boolean,
): Color =
    when {
        outsideFactor > 0f -> lerp(DANGER_ORANGE, RDYLGN_RED, sqrt(outsideFactor))
        hasSafeZone -> lerp(RDYLGN_GREEN, DANGER_ORANGE, sqrt(borderProximity))
        else -> lerp(Color.White, DANGER_ORANGE, sqrt(borderProximity))
    }

data class ColorConfig(
    val valueText: Color,
    val headerText: ColorProvider,
    val iconTint: Color,
    val background: ColorProvider?, // null = transparent cell
)

internal fun FieldColor.toColorProvider(): ColorProvider =
    when (this) {
        is FieldColor.Default -> ColorProvider(Color.White)
        is FieldColor.Error -> ColorProvider(ERROR_RED)
        is FieldColor.Muted -> ColorProvider(MUTED_GREY)
        is FieldColor.Threshold -> ColorProvider(thresholdColor(factor))
        is FieldColor.DangerZone ->
            ColorProvider(dangerZoneColor(outsideFactor, borderProximity, hasSafeZone))
        is FieldColor.Zone ->
            ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
    }

// Error and Muted never fill the cell background — colored text is enough.
internal fun FieldColor.toBackgroundColorProvider(): ColorProvider? =
    when (this) {
        is FieldColor.Default,
        is FieldColor.Error,
        is FieldColor.Muted -> null
        is FieldColor.Threshold -> ColorProvider(thresholdColor(factor))
        is FieldColor.DangerZone ->
            ColorProvider(dangerZoneColor(outsideFactor, borderProximity, hasSafeZone))
        is FieldColor.Zone ->
            ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
    }

internal fun FieldColor.toColor(): Color? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Error -> ERROR_RED
        is FieldColor.Muted -> MUTED_GREY
        is FieldColor.Threshold -> thresholdColor(factor)
        is FieldColor.DangerZone -> dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette)
    }

internal fun FieldColor.toColorConfig(colorMode: ZoneColorMode): ColorConfig {
    val bg = if (colorMode == ZoneColorMode.BACKGROUND) toBackgroundColorProvider() else null
    val onBg = bg != null
    val valueColor: Color =
        when {
            onBg -> Color.Black
            // Error/Muted always use their own text color regardless of colorMode
            this is FieldColor.Error || this is FieldColor.Muted -> toColor() ?: Color.White
            colorMode == ZoneColorMode.TEXT -> toColor() ?: Color.White
            else -> Color.White
        }
    return ColorConfig(
        valueText = valueColor,
        headerText = ColorProvider(if (onBg) Color.Black else Color.White),
        iconTint = if (onBg) ICON_TINT_BLACK else ICON_TINT_TEAL,
        background = bg,
    )
}
