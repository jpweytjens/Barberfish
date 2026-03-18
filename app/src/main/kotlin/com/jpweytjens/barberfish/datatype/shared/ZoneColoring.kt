package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import io.hammerhead.karooext.models.UserProfile

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

fun powerZoneColor(zone: Int, palette: ZonePalette = ZonePalette.KAROO): Color {
    val colors =
        when (palette) {
            ZonePalette.KAROO -> karooPowerColors
            ZonePalette.WAHOO -> wahooPowerColors
            ZonePalette.INTERVALS -> intervalsPowerColors
            ZonePalette.ZWIFT -> zwiftPowerColors
        }
    return colors.getOrElse(zone - 1) { Color.White }
}

fun hrZoneColor(zone: Int, palette: ZonePalette = ZonePalette.KAROO): Color {
    val colors =
        when (palette) {
            ZonePalette.KAROO -> karooHrColors
            ZonePalette.WAHOO -> wahooHrColors
            ZonePalette.INTERVALS -> intervalsHrColors
            ZonePalette.ZWIFT -> zwiftHrColors
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
