package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.extension.GradePalette
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

// Grade color bands — sorted descending, first match wins (percent >= threshold)
private val WAHOO_GRADE_BANDS = listOf(
    20.0 to Color(0xFF540000), // 20%+
    12.0 to Color(0xFFAA0200), // 12–19.9%
     8.0 to Color(0xFFFF5501), //  8–11.9%
     4.0 to Color(0xFFFEFF00), //  4–7.9%
     0.0 to Color(0xFF04FE00), //  0–3.9%
)

private val GARMIN_GRADE_BANDS = listOf(
    12.0 to Color(0xFFED1B24), // >12%  HC
     9.0 to Color(0xFFF36C72), //  9–12% Cat 1
     6.0 to Color(0xFFFBAD41), //  6–9%  Cat 2
     3.0 to Color(0xFFF9EE44), //  3–6%  Cat 3
     0.0 to Color(0xFF6EBE43), //  0–3%  Cat 4
)

// Reuses Karoo power zone palette (green→yellow→orange→red→purple)
private val HAMMERHEAD_GRADE_BANDS = listOf(
    23.6 to karooPowerColors[6], // >23.5%     — purple
    19.6 to karooPowerColors[5], // 19.6–23.5% — red
    15.6 to karooPowerColors[4], // 15.6–19.5% — orange
    12.6 to karooPowerColors[3], // 12.6–15.5% — salmon
     7.6 to karooPowerColors[2], //  7.6–12.5% — yellow
     4.6 to karooPowerColors[1], //  4.6–7.5%  — mint green
     0.0 to karooPowerColors[0], //  <4.6%     — dark green
)

internal fun gradeColor(percent: Double, palette: GradePalette): Color {
    val bands = when (palette) {
        GradePalette.WAHOO -> WAHOO_GRADE_BANDS
        GradePalette.GARMIN -> GARMIN_GRADE_BANDS
        GradePalette.HAMMERHEAD -> HAMMERHEAD_GRADE_BANDS
    }
    return bands.firstOrNull { percent >= it.first }?.second ?: bands.last().second
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
        is FieldColor.Grade -> ColorProvider(gradeColor(percent, palette))
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
        is FieldColor.Grade -> ColorProvider(gradeColor(percent, palette))
    }

internal fun FieldColor.toBackgroundColor(): Color? =
    when (this) {
        is FieldColor.Default,
        is FieldColor.Error,
        is FieldColor.Muted -> null
        is FieldColor.Threshold -> thresholdColor(factor)
        is FieldColor.DangerZone ->
            dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette)
        is FieldColor.Grade -> gradeColor(percent, palette)
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
        is FieldColor.Grade -> gradeColor(percent, palette)
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
