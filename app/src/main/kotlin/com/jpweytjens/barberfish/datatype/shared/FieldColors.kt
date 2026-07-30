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

// Text-mode readable variants: HSLuv lightness moved until APCA |Lc| >= 45 against the
// theme background (same pipeline as the zone palettes; scripts/palettes.py
// adjust_for_readability). Colors that already pass keep the brand value: orange passes
// on dark, red and green pass on light. Fill mode keeps the brand colors everywhere —
// its overlay text is APCA-picked per cell instead.
internal val RDYLGN_RED_READABLE_DARK = Color(0xFFF5645F)
internal val RDYLGN_GREEN_READABLE_DARK = Color(0xFF1EAA5A)
internal val DANGER_ORANGE_READABLE_LIGHT = Color(0xFFED9800)

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
internal val SPARKLINE_POI_FILL_NIGHT = Color(0xFFFFFFFF) // ahead-of-position POI fill (night)
internal val SPARKLINE_POI_FILL_DAY = Color(0xFF000000) // ahead-of-position POI fill (day)

// sqrt curve pushes color out quickly: at 10% of range, ~31% saturation; at 1%, ~10%
// Text neutral matches the default text color so "at threshold" looks like a default cell.
private fun thresholdTextColor(factor: Float, isNightMode: Boolean): Color {
    val neutral = if (isNightMode) Color.White else Color.Black
    val green = if (isNightMode) RDYLGN_GREEN_READABLE_DARK else RDYLGN_GREEN
    val red = if (isNightMode) RDYLGN_RED_READABLE_DARK else RDYLGN_RED
    return if (factor >= 0f) lerp(neutral, green, sqrt(factor))
    else lerp(neutral, red, sqrt(-factor))
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
    liveIcon: Boolean = true,
): ColorConfig {
    val defaultText = if (isNightMode) Color.White else Color.Black
    val liveTint =
        if (!liveIcon) defaultText
        else if (isNightMode) ICON_TINT_TEAL else ICON_TINT_TEAL_DAY
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
                iconTint = liveTint,
                background = null,
            )
        ZoneColorMode.NONE ->
            ColorConfig(
                valueText = defaultText,
                headerText = defaultText,
                iconTint = liveTint,
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

// Text-mode variant of dangerZoneColor with readable endpoints per theme. The one-sided
// neutral follows the theme text color (white on dark, black on light) — a fixed White
// start would be invisible as text on the day background.
private fun dangerZoneTextColor(
    outsideFactor: Float,
    borderProximity: Float,
    hasSafeZone: Boolean,
    isNightMode: Boolean,
): Color {
    val red = if (isNightMode) RDYLGN_RED_READABLE_DARK else RDYLGN_RED
    val green = if (isNightMode) RDYLGN_GREEN_READABLE_DARK else RDYLGN_GREEN
    val orange = if (isNightMode) DANGER_ORANGE else DANGER_ORANGE_READABLE_LIGHT
    val neutral = if (isNightMode) Color.White else Color.Black
    return when {
        outsideFactor > 0f -> lerp(orange, red, sqrt(outsideFactor))
        hasSafeZone -> lerp(green, orange, sqrt(borderProximity))
        else -> lerp(neutral, orange, sqrt(borderProximity))
    }
}

internal fun gradeColor(
    percent: Double,
    palette: GradePalette,
    readable: Boolean = true,
    isNightMode: Boolean = true,
): Color? {
    if (percent < gradeFloor(palette, readable, isNightMode)) return null
    return gradeBands(palette, readable, isNightMode).firstOrNull {
        (it.lo == null || percent >= it.lo) && (it.hi == null || percent < it.hi)
    }?.color
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
        is FieldColor.DangerZone ->
            dangerZoneTextColor(outsideFactor, borderProximity, hasSafeZone, isNightMode)
        is FieldColor.Zone ->
            if (isHr) hrZoneColor(zone, palette, readable = true, isNightMode = isNightMode)
            else powerZoneColor(zone, palette, readable = true, isNightMode = isNightMode)
        is FieldColor.Grade ->
            gradeColor(percent, palette, readable = true, isNightMode = isNightMode)
    }

internal fun FieldColor.toColorConfig(
    colorMode: ZoneColorMode,
    isNightMode: Boolean,
    liveIcon: Boolean = true,
): ColorConfig {
    if (this is FieldColor.Threshold)
        return thresholdColorConfig(factor, colorMode, isNightMode, liveIcon)
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
                !liveIcon -> defaultText
                else -> if (isNightMode) ICON_TINT_TEAL else ICON_TINT_TEAL_DAY
            },
        background = bg,
    )
}
