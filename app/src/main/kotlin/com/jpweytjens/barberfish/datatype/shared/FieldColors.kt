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

// FieldColor.Muted greys, APCA-tuned against the datafield background (#000000 night,
// #FFFFFF day). MutedTextGrey is one value for both modes: |Lc| ~51 on each. MutedFillGrey
// is the BACKGROUND-mode fill; bestTextOnBackground picks white on it at ~81 Lc.
internal val MutedTextGrey = Color(0xFFA0A0A0)
internal val MutedFillGrey = Color(0xFF6E6E6E)

// Named palette colors
private val ERROR_RED = Color(0xFFFF5252)
internal val ICON_TINT_TEAL = Color(0xFF31E09A) // native connected icon green, dark mode
internal val ICON_TINT_TEAL_DAY = Color(0xFF129A5E) // native connected icon green, light mode
internal val CLIMBER_BLUE = Color(0xFF2086d8)
internal val KAROO_REJOIN_RED = Color(0xFFfc292b)
internal val KAROO_DESTINATION_PURPLE = Color(0xFFddacfa)
internal val TextDark = Color(0xFF1B2D2D)
internal val BarberfishYellow = Color(0xFFFBE401)
internal val LemonYellow = Color(0xFFFFE900) // native Karoo route-line yellow; climb-overlay filler
internal val OceanBlue = Color(0xFF2A679A)
internal val BackButtonTint = Color(0xFFA0B4BE)

// RdYlGn color map (single threshold) — neutral center is mode-aware (see thresholdColorConfig)
internal val RDYLGN_RED = Color(0xFFD73027)
internal val RDYLGN_GREEN = Color(0xFF1A9850)

// Danger zone color map (min/max mode) — light amber so the whitish gradient reads clearly
internal val DANGER_ORANGE = Color(0xFFFFA726)

// Sparkline palette — used by ElevationSparkline rendering. Call sites pass to Paint.color
// via .toArgb() since android.graphics.Paint expects an Int, not a Compose Color.
internal val SPARKLINE_PAST_OUTLINE = Color(0xFF646464) // grey for past stroke + past POI fill
internal val SPARKLINE_PAST_CLIMB = Color(0xFF42759E) // CLIMBER_BLUE pre-blended with PAST_OUTLINE
internal val SPARKLINE_SILHOUETTE_NIGHT =
    Color(0x0FFFFFFF) // ~6% white — subtle ahead-fill in night mode
internal val SPARKLINE_SILHOUETTE_DAY =
    Color(0x0F000000) // ~6% black — subtle ahead-fill in day mode
internal val SPARKLINE_PAST_OVERLAY_NIGHT =
    Color(0x8C000000) // dims grade fills under past region (night)
internal val SPARKLINE_PAST_OVERLAY_DAY =
    Color(0xC8B4B4B4) // dims grade fills under past region (day)
internal val SPARKLINE_POI_FILL_NIGHT = Color(0xE6FFFFFF) // ahead-of-position POI fill (night)
internal val SPARKLINE_POI_FILL_DAY = Color(0xE6000000) // ahead-of-position POI fill (day)

// sqrt curve pushes color out quickly: at 10% of range, ~31% saturation; at 1%, ~10%
// Text neutral matches the default text color so "at threshold" looks like a default cell.
private fun thresholdTextColor(factor: Float, isNightMode: Boolean): Color {
    val neutral = if (isNightMode) Color.White else Color.Black
    return if (factor >= 0f) lerp(neutral, RDYLGN_GREEN, sqrt(factor))
    else lerp(neutral, RDYLGN_RED, sqrt(-factor))
}

// Background neutral matches the Karoo cell color so "at threshold" blends into neighbors.
private fun thresholdBackgroundColor(factor: Float, isNightMode: Boolean): Color {
    val neutral = if (isNightMode) Color.Black else Color.White
    return if (factor >= 0f) lerp(neutral, RDYLGN_GREEN, sqrt(factor))
    else lerp(neutral, RDYLGN_RED, sqrt(-factor))
}

private fun thresholdColorConfig(
    factor: Float,
    colorMode: ZoneColorMode,
    isNightMode: Boolean,
): ColorConfig {
    val defaultText = if (isNightMode) Color.White else Color.Black
    return when (colorMode) {
        ZoneColorMode.BACKGROUND -> {
            val bg = thresholdBackgroundColor(factor, isNightMode)
            val onBgText = bestTextOnBackground(bg)
            ColorConfig(
                valueText = onBgText,
                headerText = onBgText,
                iconTint = onBgText,
                background = bg,
            )
        }
        ZoneColorMode.TEXT ->
            ColorConfig(
                valueText = thresholdTextColor(factor, isNightMode),
                headerText = defaultText,
                iconTint = if (isNightMode) ICON_TINT_TEAL else ICON_TINT_TEAL_DAY,
                background = null,
            )
        ZoneColorMode.NONE ->
            ColorConfig(
                valueText = defaultText,
                headerText = defaultText,
                iconTint = if (isNightMode) ICON_TINT_TEAL else ICON_TINT_TEAL_DAY,
                background = null,
            )
    }
}

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
private val WAHOO_GRADE_BANDS =
    listOf(
        20.0 to Color(0xFF540000), // 20%+
        12.0 to Color(0xFFAA0200), // 12–19.9%
        8.0 to Color(0xFFFF5501), //  8–11.9%
        4.0 to Color(0xFFFEFF00), //  4–7.9%
        0.0 to Color(0xFF04FE00), //  0–3.9%
    )

private val GARMIN_GRADE_BANDS =
    listOf(
        12.0 to Color(0xFFED1B24), // >12%  HC
        9.0 to Color(0xFFF36C72), //  9–12% Cat 1
        6.0 to Color(0xFFFBAD41), //  6–9%  Cat 2
        3.0 to Color(0xFFF9EE44), //  3–6%  Cat 3
        0.0 to Color(0xFF6EBE43), //  0–3%  Cat 4
    )

// HSLuv grade bands — aliased to HSLuv power palette, Garmin-style grade spacing
private val HSLUV_GRADE_BANDS =
    listOf(
        18.0 to hsluvPowerColors[6], // >18%       purple/neuromuscular
        15.0 to hsluvPowerColors[5], // 15–18%     red/anaerobic
        12.0 to hsluvPowerColors[4], // 12–15%     yellow/VO₂max
        9.0 to hsluvPowerColors[3], //  9–12%     yellow-green/threshold
        6.0 to hsluvPowerColors[2], //  6–9%      green/tempo
        3.0 to hsluvPowerColors[1], //  3–6%      teal/endurance
        0.0 to hsluvPowerColors[0], //  <3%       blue-gray/recovery
    )

// Reuses Karoo power zone palette (green→yellow→orange→red→purple)
private val KAROO_GRADE_BANDS =
    listOf(
        20.0 to karooPowerColors[6], // >20%      — purple
        14.0 to karooPowerColors[5], // 14–19.9%  — red
        11.0 to karooPowerColors[4], // 11–13.9%  — orange
        8.0 to karooPowerColors[3], //  8–10.9%  — salmon
        5.0 to karooPowerColors[2], //  5–7.9%   — yellow
        2.0 to karooPowerColors[1], //  2–4.9%   — mint green
        0.0 to karooPowerColors[0], //  <2%      — dark green
    )

// Zwift grade bands — official Zwift climb colors, designed for dark backgrounds
private val ZWIFT_GRADE_BANDS =
    listOf(
        9.0 to Color(0xFFEA5147), //  9%+    — red
        6.0 to Color(0xFFFE8253), //  6–9%   — orange
        3.0 to Color(0xFFF2C510), //  3–6%   — yellow
        0.0 to Color(0xFF39A7D6), //  0–3%   — blue
    )

// Readable grade bands — HSLuv-corrected to |Lc| ≥ 45 against the datafield
// background. Dark variants target #000000 (night mode); Light variants
// target #FFFFFF (day mode). Pre-computed via scripts/apca_hsluv.py.
private val ZWIFT_GRADE_BANDS_READABLE_DARK =
    listOf(
        9.0 to Color(0xFFEB6D66), //  9%+    — red
        6.0 to Color(0xFFFE8253), //  6–9%   — orange
        3.0 to Color(0xFFF2C510), //  3–6%   — yellow
        0.0 to Color(0xFF39A7D6), //  0–3%   — blue
    )
private val WAHOO_GRADE_BANDS_READABLE_DARK =
    listOf(
        20.0 to Color(0xFFFF5959), // 20%+
        12.0 to Color(0xFFFF5958), // 12–19.9%
        8.0 to Color(0xFFFF5C23), //  8–11.9%
        4.0 to Color(0xFFFEFF00), //  4–7.9%
        0.0 to Color(0xFF04FE00), //  0–3.9%
    )
private val GARMIN_GRADE_BANDS_READABLE_DARK =
    listOf(
        12.0 to Color(0xFFFA5E60), // >12%  HC
        9.0 to Color(0xFFF36C72), //  9–12% Cat 1
        6.0 to Color(0xFFFBAD41), //  6–9%  Cat 2
        3.0 to Color(0xFFF9EE44), //  3–6%  Cat 3
        0.0 to Color(0xFF6EBE43), //  0–3%  Cat 4
    )
private val KAROO_GRADE_BANDS_READABLE_DARK =
    listOf(
        20.0 to karooPowerColorsReadableDark[6], // >20%      — purple
        14.0 to karooPowerColorsReadableDark[5], // 14–19.9%  — red
        11.0 to karooPowerColorsReadableDark[4], // 11–13.9%  — orange
        8.0 to karooPowerColorsReadableDark[3], //  8–10.9%  — salmon
        5.0 to karooPowerColorsReadableDark[2], //  5–7.9%   — yellow
        2.0 to karooPowerColorsReadableDark[1], //  2–4.9%   — mint green
        0.0 to karooPowerColorsReadableDark[0], //  <2%      — dark green
    )

// Turbo grade bands — the only palette that colors negative grades. Fill
// values are designed for visual distinction; the readable variants
// brighten the darkest blue/purple descent bands for legibility in text
// mode (night) and tone down the lighter bands for legibility on white
// (day).
private val TURBO_GRADE_BANDS =
    listOf(
        15.0 to Color(0xFF8E1201), // [15, ∞)  — deep crimson
        12.0 to Color(0xFFBC2900), // [12, 15) — dark red
        9.0 to Color(0xFFDD4700), //  [9, 12) — red-orange
        6.0 to Color(0xFFFE932C), //  [6, 9)  — orange
        3.0 to Color(0xFFF1D749), //  [3, 6)  — yellow
        0.0 to Color(0xFFB0F94D), //  [0, 3)  — lime green
        -3.0 to Color(0xFF30F0A9), // [-3, 0)  — mint
        -6.0 to Color(0xFF2BC7F0), // [-6, -3) — light blue
        -9.0 to Color(0xFF5783E9), // [-9, -6) — blue
        Double.NEGATIVE_INFINITY to Color(0xFF401C4C) // (-∞, -9) — dark purple
    )
private val TURBO_GRADE_BANDS_READABLE_DARK =
    listOf(
        15.0 to Color(0xFFFF5950),
        12.0 to Color(0xFFFF5A45),
        9.0 to Color(0xFFFF5C27),
        6.0 to Color(0xFFFE932C),
        3.0 to Color(0xFFF1D749),
        0.0 to Color(0xFFB0F94D),
        -3.0 to Color(0xFF30F0A9),
        -6.0 to Color(0xFF2BC7F0),
        -9.0 to Color(0xFF7092EC),
        Double.NEGATIVE_INFINITY to Color(0xFFBF79D9),
    )

private val ZWIFT_GRADE_BANDS_READABLE_LIGHT =
    listOf(
        9.0 to Color(0xFFEA5147),
        6.0 to Color(0xFFFE8253),
        3.0 to Color(0xFFCDA70C),
        0.0 to Color(0xFF39A7D6),
    )
private val WAHOO_GRADE_BANDS_READABLE_LIGHT =
    listOf(
        20.0 to Color(0xFF540000),
        12.0 to Color(0xFFAA0200),
        8.0 to Color(0xFFFF5501),
        4.0 to Color(0xFFB1B100),
        0.0 to Color(0xFF02C500),
    )
private val GARMIN_GRADE_BANDS_READABLE_LIGHT =
    listOf(
        12.0 to Color(0xFFED1B24),
        9.0 to Color(0xFFF36C72),
        6.0 to Color(0xFFE59C30),
        3.0 to Color(0xFFB7AE2F),
        0.0 to Color(0xFF6EBE43),
    )
private val KAROO_GRADE_BANDS_READABLE_LIGHT =
    listOf(
        20.0 to karooPowerColorsReadableLight[6],
        14.0 to karooPowerColorsReadableLight[5],
        11.0 to karooPowerColorsReadableLight[4],
        8.0 to karooPowerColorsReadableLight[3],
        5.0 to karooPowerColorsReadableLight[2],
        2.0 to karooPowerColorsReadableLight[1],
        0.0 to karooPowerColorsReadableLight[0],
    )
private val TURBO_GRADE_BANDS_READABLE_LIGHT =
    listOf(
        15.0 to Color(0xFF8E1201),
        12.0 to Color(0xFFBC2900),
        9.0 to Color(0xFFDD4700),
        6.0 to Color(0xFFFC8F12),
        3.0 to Color(0xFFC1AB38),
        0.0 to Color(0xFF84BB38),
        -3.0 to Color(0xFF25C187),
        -6.0 to Color(0xFF27B9E0),
        -9.0 to Color(0xFF5783E9),
        Double.NEGATIVE_INFINITY to Color(0xFF401C4C),
    )

internal fun gradeColor(
    percent: Double,
    palette: GradePalette,
    readable: Boolean = true,
    isNightMode: Boolean = true,
): Color? {
    val bands =
        when (palette) {
            GradePalette.WAHOO ->
                when {
                    !readable -> WAHOO_GRADE_BANDS
                    isNightMode -> WAHOO_GRADE_BANDS_READABLE_DARK
                    else -> WAHOO_GRADE_BANDS_READABLE_LIGHT
                }
            GradePalette.GARMIN ->
                when {
                    !readable -> GARMIN_GRADE_BANDS
                    isNightMode -> GARMIN_GRADE_BANDS_READABLE_DARK
                    else -> GARMIN_GRADE_BANDS_READABLE_LIGHT
                }
            GradePalette.KAROO ->
                when {
                    !readable -> KAROO_GRADE_BANDS
                    isNightMode -> KAROO_GRADE_BANDS_READABLE_DARK
                    else -> KAROO_GRADE_BANDS_READABLE_LIGHT
                }
            GradePalette.HSLUV -> HSLUV_GRADE_BANDS
            GradePalette.ZWIFT ->
                when {
                    !readable -> ZWIFT_GRADE_BANDS
                    isNightMode -> ZWIFT_GRADE_BANDS_READABLE_DARK
                    else -> ZWIFT_GRADE_BANDS_READABLE_LIGHT
                }
            GradePalette.TURBO ->
                when {
                    !readable -> TURBO_GRADE_BANDS
                    isNightMode -> TURBO_GRADE_BANDS_READABLE_DARK
                    else -> TURBO_GRADE_BANDS_READABLE_LIGHT
                }
        }
    return bands.firstOrNull { percent >= it.first }?.second
}

/**
 * Range of grades that receive a colour fill in the elevation sparkline. Symmetric palettes (Turbo)
 * colour both climbs and descents; one-sided palettes only climbs.
 * - [posMin] (climbs): fill when grade >= posMin. null = never fill on the climb side.
 * - [negMax] (descents): fill when grade < negMax. null = never fill on the descent side.
 */
internal data class GradeFillRange(val posMin: Double?, val negMax: Double?)

/**
 * [skipBandsClimb]: how many of the flattest *positive* bands stay uncoloured. 0 colours everything
 * from grade=0 up; 1 (default) skips the lowest positive band; etc.
 *
 * [skipBandsDescent]: same idea on the descent side. 0 colours everything below 0; 1 skips the
 * flattest negative band; etc. No effect on palettes without negative bands.
 *
 * Both counts clamp to the available band count.
 */
internal fun gradeFillRange(
    palette: GradePalette,
    skipBandsClimb: Int = 1,
    skipBandsDescent: Int = 0,
): GradeFillRange {
    val bands =
        when (palette) {
            GradePalette.WAHOO -> WAHOO_GRADE_BANDS
            GradePalette.GARMIN -> GARMIN_GRADE_BANDS
            GradePalette.HSLUV -> HSLUV_GRADE_BANDS
            GradePalette.KAROO -> KAROO_GRADE_BANDS
            GradePalette.ZWIFT -> ZWIFT_GRADE_BANDS
            GradePalette.TURBO -> TURBO_GRADE_BANDS
        }
    val thresholds = bands.map { it.first }
    val positives = thresholds.filter { it >= 0.0 }.sorted()
    val negatives =
        thresholds.filter { it < 0.0 && it != Double.NEGATIVE_INFINITY }.sortedDescending()

    val posMin = positives.getOrNull(skipBandsClimb.coerceAtMost(positives.lastIndex))
    val negMax =
        when {
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

// Error never fills the cell — colored text is enough. Muted fills MutedFillGrey in
// BACKGROUND mode (only reached when colorMode == BACKGROUND, see toColorConfig).
// FieldColor.Threshold is handled separately in toColorConfig (mode-aware neutral).
// Fills use the brand palette; the APCA picker in toColorConfig handles text contrast.
internal fun FieldColor.toBackgroundColor(): Color? =
    when (this) {
        is FieldColor.Default,
        is FieldColor.Error,
        is FieldColor.StreamState,
        is FieldColor.Threshold -> null
        is FieldColor.Muted -> MutedFillGrey
        is FieldColor.DangerZone -> dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette, readable = false)
            else powerZoneColor(zone, palette, readable = false)
        is FieldColor.Grade -> gradeColor(percent, palette, readable = false)
    }

// Text mode draws the palette color directly on the datafield bg, so use
// the HSLuv-corrected variant — dark-readable on #000000 in night mode,
// light-readable on #FFFFFF in day mode.
internal fun FieldColor.toColor(isNightMode: Boolean = true): Color? =
    when (this) {
        is FieldColor.Default -> null
        is FieldColor.Error -> ERROR_RED
        is FieldColor.Muted -> MutedTextGrey
        is FieldColor.StreamState -> null
        is FieldColor.Threshold -> null
        is FieldColor.DangerZone -> dangerZoneColor(outsideFactor, borderProximity, hasSafeZone)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette, readable = true, isNightMode = isNightMode)
            else powerZoneColor(zone, palette, readable = true, isNightMode = isNightMode)
        is FieldColor.Grade ->
            gradeColor(percent, palette, readable = true, isNightMode = isNightMode)
    }

internal fun FieldColor.toColorConfig(colorMode: ZoneColorMode, isNightMode: Boolean): ColorConfig {
    if (this is FieldColor.Threshold) return thresholdColorConfig(factor, colorMode, isNightMode)
    val defaultText = if (isNightMode) Color.White else Color.Black
    val bg = if (colorMode == ZoneColorMode.BACKGROUND) toBackgroundColor() else null
    // On a colored fill, pick whichever of white/black gives higher APCA contrast.
    val onBgText: Color? = bg?.let { bestTextOnBackground(it) }
    val valueColor: Color =
        when {
            // Error always uses its own text color regardless of colorMode.
            // Muted uses grey text in TEXT/NONE, but in BACKGROUND it fills the cell
            // (bg != null) so it falls through to the APCA-picked onBgText below.
            // StreamState goes to stream_state_tv; valueText set to theme default.
            this is FieldColor.Error -> toColor(isNightMode) ?: defaultText
            this is FieldColor.Muted && bg == null -> toColor(isNightMode) ?: defaultText
            this is FieldColor.StreamState -> defaultText
            colorMode == ZoneColorMode.TEXT -> toColor(isNightMode) ?: defaultText
            onBgText != null -> onBgText
            else -> defaultText
        }
    return ColorConfig(
        valueText = valueColor,
        headerText = onBgText ?: defaultText,
        iconTint =
            when {
                this is FieldColor.StreamState -> defaultText
                onBgText != null -> onBgText
                else -> if (isNightMode) ICON_TINT_TEAL else ICON_TINT_TEAL_DAY
            },
        background = bg,
    )
}
