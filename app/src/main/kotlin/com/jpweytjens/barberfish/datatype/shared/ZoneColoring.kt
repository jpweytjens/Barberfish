package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.abs
import kotlin.math.pow

// APCA-W3 (SAPC-0.98G-4g) Accessible Perceptual Contrast Algorithm.
// Returns Lc value: |Lc| ≥ 50 is the minimum for readable large text (~18 sp+).
// Positive = normal polarity (dark text on light bg); negative = reverse (light text on dark bg).
// Reference: https://github.com/Myndex/SAPC-APCA
internal fun apcaContrast(textColor: Color, bgColor: Color): Double {
    // APCA-W3 v0.1.7: luminance uses simple ^2.4 power (not piecewise sRGB linearization).
    fun luminance(c: Color): Double =
        c.red.toDouble().pow(2.4) * 0.2126729 +
            c.green.toDouble().pow(2.4) * 0.7151522 +
            c.blue.toDouble().pow(2.4) * 0.0721750
    // Soft clamp for near-black levels (no scaling factor in v0.1.7).
    fun softClamp(y: Double) = if (y < 0.022) y + (0.022 - y).pow(1.414) else y

    val yt = softClamp(luminance(textColor))
    val yb = softClamp(luminance(bgColor))
    if (abs(yb - yt) < 0.0005) return 0.0
    val sapc =
        when {
            yt < yb -> (yb.pow(0.56) - yt.pow(0.57)) * 1.14 // normal: dark text on light bg
            else -> (yb.pow(0.65) - yt.pow(0.62)) * 1.14    // reverse: light text on dark bg
        }
    return when {
        abs(sapc) < 0.1 -> 0.0
        sapc > 0 -> (sapc - 0.027) * 100
        else -> (sapc + 0.027) * 100
    }
}

// The Karoo dark ride screen background color.
internal val karooDark = Color(0xFF1B2D2D)

// True when this color meets minimum APCA contrast on the Karoo dark ride screen.
internal fun Color.isReadableOnKaroo(minLc: Double = 45.0) =
    abs(apcaContrast(this, karooDark)) >= minLc

// Raise HSL lightness via binary search until |Lc| ≥ minLc against bg.
// Returns the original color unchanged if it already passes.
internal fun Color.adjustedForReadability(bg: Color = karooDark, minLc: Double = 45.0): Color {
    if (abs(apcaContrast(this, bg)) >= minLc) return this
    val hsl = toHsl()
    var lo = hsl[2]
    var hi = 1f
    repeat(20) {
        val mid = (lo + hi) / 2f
        if (abs(apcaContrast(hslToColor(hsl[0], hsl[1], mid), bg)) >= minLc) hi = mid else lo = mid
    }
    return hslToColor(hsl[0], hsl[1], hi)
}

private fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    fun hue2rgb(p: Float, q: Float, t0: Float): Float {
        val t = if (t0 < 0f) t0 + 1f else if (t0 > 1f) t0 - 1f else t0
        return when {
            t < 1f / 6 -> p + (q - p) * 6f * t
            t < 1f / 2 -> q
            t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6f
            else -> p
        }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    return Color(hue2rgb(p, q, h + 1f / 3), hue2rgb(p, q, h), hue2rgb(p, q, h - 1f / 3))
}

// Karoo power zones (7 zones, low to high)
internal val karooPowerColors =
    listOf(
        Color(0xFF1A8C3A), // Zone 1 – Active Recovery   (dark green)
        Color(0xFF40D078), // Zone 2 – Endurance         (mint green)
        Color(0xFFF0D800), // Zone 3 – Tempo             (yellow)
        Color(0xFFF08868), // Zone 4 – Lactate Threshold (salmon)
        Color(0xFFF06020), // Zone 5 – VO2 Max           (orange)
        Color(0xFFD01020), // Zone 6 – Anaerobic         (red)
        Color(0xFF9020A0), // Zone 7 – Neuromuscular     (purple)
    )

// Karoo HR zones (5 zones) — same palette, subset of power zones
internal val karooHrColors =
    listOf(
        Color(0xFF1A8C3A), // Zone 1 – Active Recovery   (dark green)
        Color(0xFF40D078), // Zone 2 – Endurance         (mint green)
        Color(0xFFF0D800), // Zone 3 – Tempo             (yellow)
        Color(0xFFF08868), // Zone 4 – Lactate Threshold (salmon)
        Color(0xFFD01020), // Zone 5 – VO2 Max           (red)
    )

// Wahoo power zones (7 zones, low to high)
internal val wahooPowerColors =
    listOf(
        Color(0xFFC0C0C0), // Zone 1 – Recovery Miles    (grey)
        Color(0xFF253070), // Zone 2 – Foundation Miles  (navy)
        Color(0xFF4E90CC), // Zone 3 – Endurance Miles   (blue)
        Color(0xFF48B830), // Zone 4 – Tempo             (green)
        Color(0xFFF0D818), // Zone 5 – Steady State      (yellow)
        Color(0xFFE06818), // Zone 6 – Climbing Repeats  (orange)
        Color(0xFFE03020), // Zone 7 – Power Intervals   (red)
    )

// Wahoo HR zones (5 zones)
internal val wahooHrColors =
    listOf(
        Color(0xFFC0C0C0), // Zone 1 (grey)
        Color(0xFF253070), // Zone 2 (navy)
        Color(0xFF48B830), // Zone 3 (green)
        Color(0xFFE06818), // Zone 4 (orange)
        Color(0xFFE03020), // Zone 5 (red)
    )

// Intervals.icu power zones (7 zones)
internal val intervalsPowerColors =
    listOf(
        Color(0xFF3DB39F), // Zone 1 – Active Recovery   (teal)
        Color(0xFF3DB33F), // Zone 2 – Endurance         (green)
        Color(0xFFFCD549), // Zone 3 – Tempo             (yellow)
        Color(0xFFFC9C49), // Zone 4 – Lactate Threshold (orange)
        Color(0xFFE34074), // Zone 5 – VO2 Max           (pink)
        Color(0xFF8963D8), // Zone 6 – Anaerobic         (purple)
        Color(0xFF797388), // Zone 7 – Neuromuscular     (grey-purple)
    )

// Intervals.icu HR zones (5 zones — first 5 of power palette)
internal val intervalsHrColors = intervalsPowerColors.take(5)

// Zwift power zones (6 zones; zone 7 reuses zone 6 color)
internal val zwiftPowerColors =
    listOf(
        Color(0xFF7B7E80), // Zone 1 – Active Recovery   (grey)
        Color(0xFF368AF4), // Zone 2 – Endurance         (blue)
        Color(0xFF59B962), // Zone 3 – Tempo             (green)
        Color(0xFFF0C649), // Zone 4 – Lactate Threshold (yellow)
        Color(0xFFF06B45), // Zone 5 – VO2 Max           (orange)
        Color(0xFFF8431F), // Zone 6 – Anaerobic         (red)
        Color(0xFFF8431F), // Zone 7 – Neuromuscular     (red, same as zone 6)
    )

// Zwift HR zones (5 zones — first 5 of Zwift palette)
internal val zwiftHrColors = zwiftPowerColors.take(5)

// HSLuv power zones: L=65, S=100, hue starts at 260° with steps of -50° (Z1: S=10)
internal val hsluvPowerColors =
    listOf(
        Color(0xFF9C9DA8), // Z1 Recovery      (h=260° s=10  l=65)
        Color(0xFF00AEC2), // Z2 Endurance      (h=210° s=100 l=65)
        Color(0xFF00B38D), // Z3 Tempo          (h=160° s=100 l=65)
        Color(0xFF77AE00), // Z4 Threshold      (h=110° s=100 l=65)
        Color(0xFFC59700), // Z5 VO₂max         (h= 60° s=100 l=65)
        Color(0xFFFF6F77), // Z6 Anaerobic      (h= 10° s=100 l=65)
        Color(0xFFFF5AE1), // Z7 Neuromuscular  (h=320° s=100 l=65)
    )

// HSLuv HR zones: Z1, Z2, Z4, Z6, Z7 from the power palette (Wahoo-style selection)
internal val hsluvHrColors = listOf(0, 1, 3, 5, 6).map { hsluvPowerColors[it] }

// Readable palettes: raw colors lifted to |Lc| ≥ 45 against the Karoo dark background.
// Colors that already pass are returned unchanged; only lightness is raised, never hue.
internal val karooPowerColorsReadable = karooPowerColors.map { it.adjustedForReadability() }
internal val karooHrColorsReadable = karooHrColors.map { it.adjustedForReadability() }
internal val wahooPowerColorsReadable = wahooPowerColors.map { it.adjustedForReadability() }
internal val wahooHrColorsReadable = wahooHrColors.map { it.adjustedForReadability() }
internal val intervalsPowerColorsReadable = intervalsPowerColors.map { it.adjustedForReadability() }
internal val intervalsHrColorsReadable = intervalsHrColors.map { it.adjustedForReadability() }
internal val zwiftPowerColorsReadable = zwiftPowerColors.map { it.adjustedForReadability() }
internal val zwiftHrColorsReadable = zwiftHrColors.map { it.adjustedForReadability() }

fun powerZoneColor(zone: Int, palette: ZonePalette = ZonePalette.KAROO, readable: Boolean = true): Color {
    val colors =
        when (palette) {
            ZonePalette.KAROO -> if (readable) karooPowerColorsReadable else karooPowerColors
            ZonePalette.WAHOO -> if (readable) wahooPowerColorsReadable else wahooPowerColors
            ZonePalette.INTERVALS -> if (readable) intervalsPowerColorsReadable else intervalsPowerColors
            ZonePalette.ZWIFT -> if (readable) zwiftPowerColorsReadable else zwiftPowerColors
            ZonePalette.HSLUV -> hsluvPowerColors
        }
    return colors.getOrElse(zone - 1) { Color.White }
}

fun hrZoneColor(zone: Int, palette: ZonePalette = ZonePalette.KAROO, readable: Boolean = true): Color {
    val colors =
        when (palette) {
            ZonePalette.KAROO -> if (readable) karooHrColorsReadable else karooHrColors
            ZonePalette.WAHOO -> if (readable) wahooHrColorsReadable else wahooHrColors
            ZonePalette.INTERVALS -> if (readable) intervalsHrColorsReadable else intervalsHrColors
            ZonePalette.ZWIFT -> if (readable) zwiftHrColorsReadable else zwiftHrColors
            ZonePalette.HSLUV -> hsluvHrColors
        }
    return colors.getOrElse(zone - 1) { Color.White }
}

// Zone boundary math using the SDK-provided zone lists (respects the user's
// configured zones on their Karoo device, rather than hardcoded % thresholds).

fun powerZone(watts: Double, zones: List<UserProfile.Zone>): Int {
    if (zones.isEmpty()) return 1
    return zones.indexOfFirst { watts <= it.max }.let { if (it < 0) zones.size else it + 1 }
}

fun hrZone(bpm: Double, zones: List<UserProfile.Zone>): Int {
    if (zones.isEmpty()) return 1
    return zones.indexOfFirst { bpm <= it.max }.let { if (it < 0) zones.size else it + 1 }
}
