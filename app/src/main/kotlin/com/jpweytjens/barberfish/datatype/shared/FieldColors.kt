package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import kotlin.math.sqrt

// UI grey palette (Design Guide scale: 100=lightest, 600=darkest)
internal val Grey100 = Color(0xFFF4F4F4)
internal val Grey200 = Color(0xFFDDDDDD)
internal val Grey400 = Color(0xFF979797)
internal val Grey500 = Color(0xFF7D7D7D)

// Named palette colors
private val ERROR_RED = Color(0xFFFF5252)
internal val ICON_TINT_TEAL = Color(0xFF31E09A)
internal val CLIMBER_BLUE             = Color(0xFF2086d8)
internal val KAROO_REJOIN_RED         = Color(0xFFfc292b)
internal val KAROO_DESTINATION_PURPLE = Color(0xFFddacfa)
internal val TextDark      = Color(0xFF1B2D2D)
internal val LemonYellow   = Color(0xFFFFE900)
internal val BackButtonTint = Color(0xFFA0B4BE)

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

// Zwift grade bands — official Zwift climb colors, designed for dark backgrounds
private val ZWIFT_GRADE_BANDS = listOf(
     9.0 to Color(0xFFEA5147), //  9%+    — red
     6.0 to Color(0xFFFE8253), //  6–9%   — orange
     3.0 to Color(0xFFF2C510), //  3–6%   — yellow
     0.0 to Color(0xFF39A7D6), //  0–3%   — blue
)

// Readable grade bands — HSLuv-corrected to |Lc| ≥ 45 against black. Pre-computed via scripts/apca_hsluv.py.
private val ZWIFT_GRADE_BANDS_READABLE = listOf(
     9.0 to Color(0xFFEB6D66), //  9%+    — red
     6.0 to Color(0xFFFE8253), //  6–9%   — orange
     3.0 to Color(0xFFF2C510), //  3–6%   — yellow
     0.0 to Color(0xFF39A7D6), //  0–3%   — blue
)
private val WAHOO_GRADE_BANDS_READABLE = listOf(
    20.0 to Color(0xFFFF5959), // 20%+
    12.0 to Color(0xFFFF5958), // 12–19.9%
     8.0 to Color(0xFFFF5C23), //  8–11.9%
     4.0 to Color(0xFFFEFF00), //  4–7.9%
     0.0 to Color(0xFF04FE00), //  0–3.9%
)
private val GARMIN_GRADE_BANDS_READABLE = listOf(
    12.0 to Color(0xFFFA5E60), // >12%  HC
     9.0 to Color(0xFFF36C72), //  9–12% Cat 1
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

// Turbo grade bands — readable by construction; the only palette that colors negative grades.
// Single variant: no _READABLE split.
private val TURBO_GRADE_BANDS = listOf(
    15.0  to Color(0xFF8E1201),                   // [15, ∞)   — deep crimson
    12.0  to Color(0xFFBC2900),                   // [12, 15)  — dark red
     9.0  to Color(0xFFDD4700),                   //  [9, 12)  — red-orange
     6.0  to Color(0xFFFE932C),                   //  [6, 9)   — orange
     3.0  to Color(0xFFF1D749),                   //  [3, 6)   — yellow
     0.0  to Color(0xFFB0F94D),                   //  [0, 3)   — lime green
    -3.0  to Color(0xFF30F0A9),                   // [-3, 0)   — mint
    -6.0  to Color(0xFF2BC7F0),                   // [-6, -3)  — light blue
    Double.NEGATIVE_INFINITY to Color(0xFF5783E9) // (-∞, -6)  — blue
)

internal fun gradeColor(percent: Double, palette: GradePalette, readable: Boolean = true): Color? {
    val bands = when (palette) {
        GradePalette.WAHOO -> if (readable) WAHOO_GRADE_BANDS_READABLE else WAHOO_GRADE_BANDS
        GradePalette.GARMIN -> if (readable) GARMIN_GRADE_BANDS_READABLE else GARMIN_GRADE_BANDS
        GradePalette.KAROO -> if (readable) KAROO_GRADE_BANDS_READABLE else KAROO_GRADE_BANDS
        GradePalette.HSLUV -> HSLUV_GRADE_BANDS
        GradePalette.ZWIFT -> if (readable) ZWIFT_GRADE_BANDS_READABLE else ZWIFT_GRADE_BANDS
        GradePalette.TURBO -> TURBO_GRADE_BANDS
    }
    return bands.firstOrNull { percent >= it.first }?.second
}

/**
 * Range of grades that receive a colour fill in the elevation sparkline. Symmetric
 * palettes (Turbo) colour both climbs and descents; one-sided palettes only climbs.
 *
 * - [posMin] (climbs): fill when grade >= posMin. null = never fill on the climb side.
 * - [negMax] (descents): fill when grade < negMax. null = never fill on the descent side.
 */
internal data class GradeFillRange(val posMin: Double?, val negMax: Double?)

/**
 * [skipBandsClimb]: how many of the flattest *positive* bands stay uncoloured. 0 colours
 * everything from grade=0 up; 1 (default) skips the lowest positive band; etc.
 *
 * [skipBandsDescent]: same idea on the descent side. 0 colours everything below 0;
 * 1 skips the flattest negative band; etc. No effect on palettes without negative bands.
 *
 * Both counts clamp to the available band count.
 */
internal fun gradeFillRange(
    palette: GradePalette,
    skipBandsClimb: Int = 1,
    skipBandsDescent: Int = 0,
): GradeFillRange {
    val bands = when (palette) {
        GradePalette.WAHOO  -> WAHOO_GRADE_BANDS
        GradePalette.GARMIN -> GARMIN_GRADE_BANDS
        GradePalette.HSLUV  -> HSLUV_GRADE_BANDS
        GradePalette.KAROO  -> KAROO_GRADE_BANDS
        GradePalette.ZWIFT  -> ZWIFT_GRADE_BANDS
        GradePalette.TURBO  -> TURBO_GRADE_BANDS
    }
    val thresholds = bands.map { it.first }
    val positives = thresholds.filter { it >= 0.0 }.sorted()
    val negatives = thresholds
        .filter { it < 0.0 && it != Double.NEGATIVE_INFINITY }
        .sortedDescending()

    val posMin = positives.getOrNull(skipBandsClimb.coerceAtMost(positives.lastIndex))
    val negMax = when {
        negatives.isEmpty() -> null
        skipBandsDescent <= 0 -> 0.0
        else -> negatives.getOrNull((skipBandsDescent - 1).coerceAtMost(negatives.lastIndex))
    }
    return GradeFillRange(posMin, negMax)
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
        is FieldColor.Muted,
        is FieldColor.StreamState -> null
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
        is FieldColor.Muted -> Grey500
        is FieldColor.StreamState -> null
        is FieldColor.Threshold -> thresholdColor(factor)
        is FieldColor.DangerZone -> dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette, readable) else powerZoneColor(zone, palette, readable)
        is FieldColor.Grade -> gradeColor(percent, palette, readable)
    }

internal fun FieldColor.toColorConfig(colorMode: ZoneColorMode, isNightMode: Boolean): ColorConfig {
    val defaultText = if (isNightMode) Color.White else Color.Black
    val bg = if (colorMode == ZoneColorMode.BACKGROUND) toBackgroundColor() else null
    val onBg = bg != null
    val valueColor: Color =
        when {
            // Error/Muted always use their own text color regardless of colorMode.
            // StreamState goes to stream_state_tv; valueText set to theme default.
            this is FieldColor.Error || this is FieldColor.Muted -> toColor() ?: defaultText
            this is FieldColor.StreamState -> defaultText
            colorMode == ZoneColorMode.TEXT -> toColor() ?: defaultText
            onBg -> defaultText
            else -> defaultText
        }
    return ColorConfig(
        valueText = valueColor,
        headerText = defaultText,
        iconTint = if (onBg || this is FieldColor.StreamState) defaultText else ICON_TINT_TEAL,
        background = bg,
    )
}
