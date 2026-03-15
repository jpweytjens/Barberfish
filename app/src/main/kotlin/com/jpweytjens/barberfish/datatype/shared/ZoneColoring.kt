package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import io.hammerhead.karooext.models.UserProfile

// Karoo power zones (7 zones, low to high)
private val karooPowerColors = listOf(
    Color(0xFF1A8C3A),  // Zone 1 – Active Recovery   (dark green)
    Color(0xFF40D078),  // Zone 2 – Endurance         (mint green)
    Color(0xFFF0D800),  // Zone 3 – Tempo             (yellow)
    Color(0xFFF08868),  // Zone 4 – Lactate Threshold (salmon)
    Color(0xFFF06020),  // Zone 5 – VO2 Max           (orange)
    Color(0xFFD01020),  // Zone 6 – Anaerobic         (red)
    Color(0xFF9020A0),  // Zone 7 – Neuromuscular     (purple)
)

// Karoo HR zones (5 zones) — same palette, subset of power zones
private val karooHrColors = listOf(
    Color(0xFF1A8C3A),  // Zone 1 – Active Recovery   (dark green)
    Color(0xFF40D078),  // Zone 2 – Endurance         (mint green)
    Color(0xFFF0D800),  // Zone 3 – Tempo             (yellow)
    Color(0xFFF08868),  // Zone 4 – Lactate Threshold (salmon)
    Color(0xFFD01020),  // Zone 5 – VO2 Max           (red)
)

// Wahoo power zones (7 zones, low to high)
private val wahooPowerColors = listOf(
    Color(0xFFC0C0C0),  // Zone 1 – Recovery Miles    (grey)
    Color(0xFF253070),  // Zone 2 – Foundation Miles  (navy)
    Color(0xFF4E90CC),  // Zone 3 – Endurance Miles   (blue)
    Color(0xFF48B830),  // Zone 4 – Tempo             (green)
    Color(0xFFF0D818),  // Zone 5 – Steady State      (yellow)
    Color(0xFFE06818),  // Zone 6 – Climbing Repeats  (orange)
    Color(0xFFE03020),  // Zone 7 – Power Intervals   (red)
)

// Wahoo HR zones (5 zones)
private val wahooHrColors = listOf(
    Color(0xFFC0C0C0),  // Zone 1 (grey)
    Color(0xFF253070),  // Zone 2 (navy)
    Color(0xFF48B830),  // Zone 3 (green)
    Color(0xFFE06818),  // Zone 4 (orange)
    Color(0xFFE03020),  // Zone 5 (red)
)

fun powerZoneColor(zone: Int, palette: ZonePalette = ZonePalette.KAROO): Color {
    val colors = when (palette) {
        ZonePalette.KAROO -> karooPowerColors
        ZonePalette.WAHOO -> wahooPowerColors
    }
    return colors.getOrElse(zone - 1) { Color.White }
}

fun hrZoneColor(zone: Int, palette: ZonePalette = ZonePalette.KAROO): Color {
    val colors = when (palette) {
        ZonePalette.KAROO -> karooHrColors
        ZonePalette.WAHOO -> wahooHrColors
    }
    return colors.getOrElse(zone - 1) { Color.White }
}

// Zone boundary math using the SDK-provided zone lists (respects the user's
// configured zones on their Karoo device, rather than hardcoded % thresholds).

fun powerZone(watts: Double, zones: List<UserProfile.Zone>): Int {
    if (zones.isEmpty()) return 1
    return zones.indexOfFirst { watts <= it.max }
        .let { if (it < 0) zones.size else it + 1 }
}

fun hrZone(bpm: Double, zones: List<UserProfile.Zone>): Int {
    if (zones.isEmpty()) return 1
    return zones.indexOfFirst { bpm <= it.max }
        .let { if (it < 0) zones.size else it + 1 }
}
