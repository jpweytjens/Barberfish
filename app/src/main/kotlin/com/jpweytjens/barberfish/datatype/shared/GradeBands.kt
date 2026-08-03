package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.extension.GradePalette

// Grade color bands — sorted descending, highest threshold first

// Barberfish grade bands — two-sided. The climb side above 2% carries the same
// hexes as the Karoo power palette's zones 2–7; -2% to 2% is a quiet green-grey,
// kin to the descent limb, which deepens from teal to slate.
private val BARBERFISH_GRADE_BANDS =
    listOf(
        20.0 to Color(0xFF9020A0), // [20, ∞)   — purple
        14.0 to Color(0xFFD01020), // [14, 20)  — red
        11.0 to Color(0xFFF06020), // [11, 14)  — orange
        8.0 to Color(0xFFF08868), //  [8, 11)  — salmon
        5.0 to Color(0xFFF0D800), //  [5, 8)   — yellow
        2.0 to Color(0xFF40D078), //  [2, 5)   — mint green
        -2.0 to Color(0xFF92B4A5), // [-2, 2)   — flat green-grey
        -6.0 to Color(0xFF50A39C), // [-6, -2)  — teal
        -10.0 to Color(0xFF1C6E86), // [-10, -6) — deep teal
        Double.NEGATIVE_INFINITY to Color(0xFF384778), // (-∞, -10) — slate
    )

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
private val BARBERFISH_GRADE_BANDS_READABLE_DARK =
    listOf(
        20.0 to Color(0xFFDE5AF3), // was #9020A0
        14.0 to Color(0xFFFC5C61), // was #D01020
        11.0 to Color(0xFFF86421), // was #F06020
        8.0 to Color(0xFFF08868),
        5.0 to Color(0xFFF0D800),
        2.0 to Color(0xFF40D078),
        -2.0 to Color(0xFF92B4A5),
        -6.0 to Color(0xFF50A39C),
        -10.0 to Color(0xFF2DA0C2), // was #1C6E86
        Double.NEGATIVE_INFINITY to Color(0xFF8392CF), // was #384778
    )
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

// Turbo grade bands — like Barberfish, colors negative grades as well. Fill
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
        Double.NEGATIVE_INFINITY to Color(0xFF401C4C), // (-∞, -9) — dark purple
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

private val BARBERFISH_GRADE_BANDS_READABLE_LIGHT =
    listOf(
        20.0 to Color(0xFF9020A0),
        14.0 to Color(0xFFD01020),
        11.0 to Color(0xFFF06020),
        8.0 to Color(0xFFF08868),
        5.0 to Color(0xFFC0AC00), // was #F0D800
        2.0 to Color(0xFF3BC16F), // was #40D078
        -2.0 to Color(0xFF91B3A4), // was #92B4A5
        -6.0 to Color(0xFF50A39C),
        -10.0 to Color(0xFF1C6E86),
        Double.NEGATIVE_INFINITY to Color(0xFF384778),
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

// Raw threshold/color pairs backing a palette's bands, descending high to low, exactly the
// tables gradeColor used to switch on directly.
private fun gradeThresholdColors(
    palette: GradePalette,
    readable: Boolean,
    isNightMode: Boolean,
): List<Pair<Double, Color>> =
    when (palette) {
        GradePalette.BARBERFISH ->
            when {
                !readable -> BARBERFISH_GRADE_BANDS
                isNightMode -> BARBERFISH_GRADE_BANDS_READABLE_DARK
                else -> BARBERFISH_GRADE_BANDS_READABLE_LIGHT
            }
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

/**
 * One grade band. [lo] is inclusive, [hi] exclusive. A null end is open. Bands are always returned
 * ordered low to high and tile the axis without gaps.
 */
internal data class GradeBand(val lo: Double?, val hi: Double?, val color: Color)

/** Boundary values a selector may snap an edge to, per side. */
internal data class GradeBandStops(
    val climb: List<Double>,
    val descent: List<Double>,
)

/**
 * The band table for [palette]. Built from the same `*_GRADE_BANDS` lists the previous `gradeColor`
 * used, reversed into low-to-high order and widened into explicit ranges.
 *
 * The lowest band's open [GradeBand.lo] is not a real floor for one-sided palettes; callers
 * matching grades against it must guard with [gradeFloor] first (see [gradeColor]).
 */
internal fun gradeBands(
    palette: GradePalette,
    readable: Boolean = true,
    isNightMode: Boolean = true,
): List<GradeBand> {
    val descending = gradeThresholdColors(palette, readable, isNightMode)
    val ascending = descending.reversed()
    return ascending.mapIndexed { i, (threshold, color) ->
        GradeBand(
            lo = if (i == 0) null else threshold,
            hi = ascending.getOrNull(i + 1)?.first,
            color = color,
        )
    }
}

internal fun gradeBandStops(palette: GradePalette): GradeBandStops {
    val bands = gradeBands(palette, readable = false)
    val stops = bands.mapNotNull { it.lo }
    return GradeBandStops(
        climb = stops.filter { it > 0.0 }.sorted(),
        descent = stops.filter { it < 0.0 }.sortedDescending(),
    )
}

/**
 * The map overlay's neutral for [palette]: what a run inside the emphasis edges paints. A palette
 * with a band strictly containing zero (only Barberfish) uses that band's colour, so its flat band
 * and the map neutral stay one colour; every other palette keeps the shared [FlatGrey].
 */
internal fun mapNeutral(
    palette: GradePalette,
    readable: Boolean = true,
    isNightMode: Boolean = true,
): Color =
    gradeBands(palette, readable, isNightMode)
        // lo must be a real threshold: a one-sided palette's lowest band has an open low
        // end that is not a floor (see gradeBands' KDoc), and it must not match here.
        .firstOrNull { it.lo != null && it.lo < 0.0 && (it.hi ?: Double.POSITIVE_INFINITY) > 0.0 }
        ?.color ?: FlatGrey

/**
 * The palette's lowest explicit threshold (e.g. 0.0 for one-sided palettes,
 * [Double.NEGATIVE_INFINITY] for Turbo, which has no true floor). [gradeBands] always reports an
 * open low end on the lowest band, so `gradeColor` uses this to keep returning null below a
 * one-sided palette's floor instead of matching the flattest band.
 */
internal fun gradeFloor(
    palette: GradePalette,
    readable: Boolean = true,
    isNightMode: Boolean = true,
): Double = gradeThresholdColors(palette, readable, isNightMode).last().first

/**
 * The band's own colour for [grade], or [neutral] when the grade falls inside the edges.
 * [climbEdge] null means no climb band is coloured; [descentEdge] null means no descent band is.
 * Implemented in terms of [gradeBands] so the two can never disagree.
 *
 * Grades below the palette's [gradeFloor] stay [neutral], the same guard [gradeColor] applies: the
 * lowest band's open low end is not a real floor on a one-sided palette, so a descent edge stored
 * against one would otherwise hand descents the flattest climb band's colour.
 */
internal fun gradeBandColor(
    grade: Double,
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
    neutral: Color,
    readable: Boolean = true,
    isNightMode: Boolean = true,
): Color {
    val coloured =
        when {
            grade > 0.0 -> climbEdge != null && grade >= climbEdge
            grade < 0.0 -> descentEdge != null && grade <= descentEdge
            // Exactly 0.0 is neither side, so the comparisons above never see it. An edge of 0.0
            // only ever means "Off" (no palette has a 0.0 stop), and the edges are inclusive, so
            // a fully-on side colours 0.0 too: it takes its containing band below.
            else ->
                (climbEdge != null && climbEdge <= 0.0) ||
                    (descentEdge != null && descentEdge >= 0.0)
        }
    if (!coloured) return neutral
    if (grade < gradeFloor(palette, readable, isNightMode)) return neutral
    val band =
        gradeBands(palette, readable, isNightMode).firstOrNull {
            (it.lo == null || grade >= it.lo) && (it.hi == null || grade < it.hi)
        }
    return band?.color ?: neutral
}
