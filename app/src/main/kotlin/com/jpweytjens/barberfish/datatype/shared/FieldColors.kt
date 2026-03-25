package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import kotlin.math.sqrt

// Named palette colors
private val ERROR_RED = Color(0xFFFF5252)
private val MUTED_GREY = Color(0xFF7D7D7D)
internal val ICON_TINT_TEAL = Color(0xFF31E09A)

// RdYlGn color map (single threshold) — neutral center is white
internal val RDYLGN_RED = Color(0xFFD73027)
internal val RDYLGN_GREEN = Color(0xFF1A9850)

// Danger zone color map (min/max mode) — light amber so the whitish gradient reads clearly
internal val DANGER_ORANGE = Color(0xFFFFA726)

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

// HSLuv grade bands — aliased to HSLuv power palette, Garmin-style grade spacing
private val HSLUV_GRADE_BANDS = listOf(
    18.0 to hsluvPowerColors[6], // >18%       purple/neuromuscular
    15.0 to hsluvPowerColors[5], // 15–18%     red/anaerobic
    12.0 to hsluvPowerColors[4], // 12–15%     yellow/VO₂max
     9.0 to hsluvPowerColors[3], //  9–12%     yellow-green/threshold
     6.0 to hsluvPowerColors[2], //  6–9%      green/tempo
     3.0 to hsluvPowerColors[1], //  3–6%      teal/endurance
     0.0 to hsluvPowerColors[0], //  <3%       blue-gray/recovery
)

// Reuses Karoo power zone palette (green→yellow→orange→red→purple)
private val KAROO_GRADE_BANDS = listOf(
    23.6 to karooPowerColors[6], // >23.5%     — purple
    19.6 to karooPowerColors[5], // 19.6–23.5% — red
    15.6 to karooPowerColors[4], // 15.6–19.5% — orange
    12.6 to karooPowerColors[3], // 12.6–15.5% — salmon
     7.6 to karooPowerColors[2], //  7.6–12.5% — yellow
     4.6 to karooPowerColors[1], //  4.6–7.5%  — mint green
     0.0 to karooPowerColors[0], //  <4.6%     — dark green
)

// Readable grade bands — HSLuv-corrected to |Lc| ≥ 45. Pre-computed via scripts/apca_hsluv.py.
private val WAHOO_GRADE_BANDS_READABLE = listOf(
    20.0 to Color(0xFFFF6A6A), // 20%+
    12.0 to Color(0xFFFF6A6A), // 12–19.9%
     8.0 to Color(0xFFFF6D49), //  8–11.9%
     4.0 to Color(0xFFFEFF00), //  4–7.9%
     0.0 to Color(0xFF04FE00), //  0–3.9%
)
private val GARMIN_GRADE_BANDS_READABLE = listOf(
    12.0 to Color(0xFFFA6F71), // >12%  HC
     9.0 to Color(0xFFF37479), //  9–12% Cat 1
     6.0 to Color(0xFFFBAD41), //  6–9%  Cat 2
     3.0 to Color(0xFFF9EE44), //  3–6%  Cat 3
     0.0 to Color(0xFF6EBE43), //  0–3%  Cat 4
)
private val KAROO_GRADE_BANDS_READABLE = listOf(
    23.6 to karooPowerColorsReadable[6], // >23.5%     — purple
    19.6 to karooPowerColorsReadable[5], // 19.6–23.5% — red
    15.6 to karooPowerColorsReadable[4], // 15.6–19.5% — orange
    12.6 to karooPowerColorsReadable[3], // 12.6–15.5% — salmon
     7.6 to karooPowerColorsReadable[2], //  7.6–12.5% — yellow
     4.6 to karooPowerColorsReadable[1], //  4.6–7.5%  — mint green
     0.0 to karooPowerColorsReadable[0], //  <4.6%     — dark green
)

internal fun gradeColor(percent: Double, palette: GradePalette, readable: Boolean = true): Color? {
    if (percent < 0) return null
    val bands = when (palette) {
        GradePalette.WAHOO -> if (readable) WAHOO_GRADE_BANDS_READABLE else WAHOO_GRADE_BANDS
        GradePalette.GARMIN -> if (readable) GARMIN_GRADE_BANDS_READABLE else GARMIN_GRADE_BANDS
        GradePalette.KAROO -> if (readable) KAROO_GRADE_BANDS_READABLE else KAROO_GRADE_BANDS
        GradePalette.HSLUV -> HSLUV_GRADE_BANDS
    }
    return bands.firstOrNull { percent >= it.first }?.second
}

/**
 * Returns the minimum grade (%) that receives a colour fill in the elevation sparkline.
 *
 * [skipBands] controls how many of the lowest-grade bands are suppressed (0 = colour
 * everything including flat terrain, 1 = skip the lowest band (default), 2 = skip the
 * two lowest, etc.). Clamped so it never exceeds the band count.
 */
internal fun gradeThreshold(palette: GradePalette, skipBands: Int = 1): Double {
    val bands = when (palette) {
        GradePalette.WAHOO  -> WAHOO_GRADE_BANDS
        GradePalette.GARMIN -> GARMIN_GRADE_BANDS
        GradePalette.HSLUV  -> HSLUV_GRADE_BANDS
        GradePalette.KAROO  -> KAROO_GRADE_BANDS
    }
    val idx = (bands.lastIndex - skipBands).coerceAtLeast(0)
    return bands[idx].first
}

data class ColorConfig(
    val valueText: Color,
    val headerText: Color,
    val iconTint: Color,
    val background: Color?, // null = transparent cell
)

// Error and Muted never fill the cell background — colored text is enough.
internal fun FieldColor.toBackgroundColor(): Color? =
    when (this) {
        is FieldColor.Default,
        is FieldColor.Error,
        is FieldColor.Muted -> null
        is FieldColor.Threshold -> thresholdColor(factor)
        is FieldColor.DangerZone ->
            dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette, readable) else powerZoneColor(zone, palette, readable)
        is FieldColor.Grade -> gradeColor(percent, palette, readable)
    }

internal fun FieldColor.toColor(): Color? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Error -> ERROR_RED
        is FieldColor.Muted -> MUTED_GREY
        is FieldColor.Threshold -> thresholdColor(factor)
        is FieldColor.DangerZone -> dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette, readable) else powerZoneColor(zone, palette, readable)
        is FieldColor.Grade -> gradeColor(percent, palette, readable)
    }

internal fun FieldColor.toColorConfig(colorMode: ZoneColorMode): ColorConfig {
    val bg = if (colorMode == ZoneColorMode.BACKGROUND) toBackgroundColor() else null
    val onBg = bg != null
    val valueColor: Color =
        when {
            // Error/Muted always use their own text color regardless of colorMode
            this is FieldColor.Error || this is FieldColor.Muted -> toColor() ?: Color.White
            colorMode == ZoneColorMode.TEXT -> toColor() ?: Color.White
            else -> Color.White
        }
    return ColorConfig(
        valueText = valueColor,
        headerText = Color.White,
        iconTint = if (onBg) Color.White else ICON_TINT_TEAL,
        background = bg,
    )
}
