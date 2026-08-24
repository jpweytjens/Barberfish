package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.apcaContrast
import com.jpweytjens.barberfish.datatype.shared.bestTextOnBackground
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.hrZoneColor
import com.jpweytjens.barberfish.datatype.shared.intervalsHrColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.intervalsHrColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.intervalsPowerColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.intervalsPowerColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.karooHrColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.karooHrColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.karooPowerColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.karooPowerColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.datatype.shared.powerZoneColor
import com.jpweytjens.barberfish.datatype.shared.surgeonfishHrColors
import com.jpweytjens.barberfish.datatype.shared.surgeonfishHrColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.surgeonfishHrColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.surgeonfishPowerColors
import com.jpweytjens.barberfish.datatype.shared.surgeonfishPowerColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.surgeonfishPowerColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.wahooHrColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.wahooHrColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.zwiftHrColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.zwiftHrColorsReadableLight
import com.jpweytjens.barberfish.datatype.shared.zwiftPowerColorsReadableDark
import com.jpweytjens.barberfish.datatype.shared.zwiftPowerColorsReadableLight
import com.jpweytjens.barberfish.extension.GradePalette
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneColoringTest {

    // --- APCA contrast utility ---

    // Reference values from apcacontrast.com, background #1B2D2D (Karoo dark ride screen)
    private val karooDark = Color(0xFF1B2D2D)

    private fun assertApca(text: String, textColor: Color, expected: Double) {
        val lc = apcaContrast(textColor, karooDark)
        assertEquals("$text Lc", expected, lc, 1.0)
    }

    @Test
    fun `APCA ref 40D078 on 1B2D2D is -60 3`() = assertApca("#40D078", Color(0xFF40D078), -60.3)

    @Test
    fun `APCA ref F0D800 on 1B2D2D is -78 5`() = assertApca("#F0D800", Color(0xFFF0D800), -78.5)

    @Test
    fun `APCA ref 59B962 on 1B2D2D is -50 1`() = assertApca("#59B962", Color(0xFF59B962), -50.1)

    @Test
    fun `APCA ref F06020 on 1B2D2D is -38 7`() = assertApca("#F06020", Color(0xFFF06020), -38.7)

    @Test fun `APCA ref 253070 on 1B2D2D is 0`() = assertApca("#253070", Color(0xFF253070), 0.0)

    // --- Readability audit: APCA-adjusted palettes vs black (#000000) datafield background ---
    // Adjusted palettes must achieve |Lc| ≥ 45 (minimum for large bold text).
    // HSLuv palette uses |Lc| ≥ 43 (colors are designed for this target, not post-adjusted).

    private val black = Color(0xFF000000)

    private fun assertReadable(name: String, color: Color, threshold: Double = 45.0) {
        val lc = abs(apcaContrast(color, black))
        assertTrue(
            "$name hex=${color.value.toString(16).uppercase()} Lc=${"%.1f".format(lc)} < $threshold",
            lc >= threshold,
        )
    }

    @Test
    fun `Karoo Z1 readable on karoo dark`() =
        assertReadable("Karoo Z1", karooPowerColorsReadableDark[0])

    @Test
    fun `Karoo Z2 readable on karoo dark`() =
        assertReadable("Karoo Z2", karooPowerColorsReadableDark[1])

    @Test
    fun `Karoo Z3 readable on karoo dark`() =
        assertReadable("Karoo Z3", karooPowerColorsReadableDark[2])

    @Test
    fun `Karoo Z4 readable on karoo dark`() =
        assertReadable("Karoo Z4", karooPowerColorsReadableDark[3])

    @Test
    fun `Karoo Z5 readable on karoo dark`() =
        assertReadable("Karoo Z5", karooPowerColorsReadableDark[4])

    @Test
    fun `Karoo Z6 readable on karoo dark`() =
        assertReadable("Karoo Z6", karooPowerColorsReadableDark[5])

    @Test
    fun `Karoo Z7 readable on karoo dark`() =
        assertReadable("Karoo Z7", karooPowerColorsReadableDark[6])

    @Test
    fun `Karoo HR Z1 readable on karoo dark`() =
        assertReadable("Karoo HR Z1", karooHrColorsReadableDark[0])

    @Test
    fun `Karoo HR Z2 readable on karoo dark`() =
        assertReadable("Karoo HR Z2", karooHrColorsReadableDark[1])

    @Test
    fun `Karoo HR Z3 readable on karoo dark`() =
        assertReadable("Karoo HR Z3", karooHrColorsReadableDark[2])

    @Test
    fun `Karoo HR Z4 readable on karoo dark`() =
        assertReadable("Karoo HR Z4", karooHrColorsReadableDark[3])

    @Test
    fun `Karoo HR Z5 readable on karoo dark`() =
        assertReadable("Karoo HR Z5", karooHrColorsReadableDark[4])

    @Test
    fun `Wahoo Z1 readable on karoo dark`() =
        assertReadable("Wahoo Z1", wahooPowerColorsReadableDark[0])

    @Test
    fun `Wahoo Z2 readable on karoo dark`() =
        assertReadable("Wahoo Z2", wahooPowerColorsReadableDark[1])

    @Test
    fun `Wahoo Z3 readable on karoo dark`() =
        assertReadable("Wahoo Z3", wahooPowerColorsReadableDark[2])

    @Test
    fun `Wahoo Z4 readable on karoo dark`() =
        assertReadable("Wahoo Z4", wahooPowerColorsReadableDark[3])

    @Test
    fun `Wahoo Z5 readable on karoo dark`() =
        assertReadable("Wahoo Z5", wahooPowerColorsReadableDark[4])

    @Test
    fun `Wahoo Z6 readable on karoo dark`() =
        assertReadable("Wahoo Z6", wahooPowerColorsReadableDark[5])

    @Test
    fun `Wahoo Z7 readable on karoo dark`() =
        assertReadable("Wahoo Z7", wahooPowerColorsReadableDark[6])

    @Test
    fun `Wahoo HR Z1 readable on karoo dark`() =
        assertReadable("Wahoo HR Z1", wahooHrColorsReadableDark[0])

    @Test
    fun `Wahoo HR Z2 readable on karoo dark`() =
        assertReadable("Wahoo HR Z2", wahooHrColorsReadableDark[1])

    @Test
    fun `Wahoo HR Z3 readable on karoo dark`() =
        assertReadable("Wahoo HR Z3", wahooHrColorsReadableDark[2])

    @Test
    fun `Wahoo HR Z4 readable on karoo dark`() =
        assertReadable("Wahoo HR Z4", wahooHrColorsReadableDark[3])

    @Test
    fun `Wahoo HR Z5 readable on karoo dark`() =
        assertReadable("Wahoo HR Z5", wahooHrColorsReadableDark[4])

    @Test
    fun `Intervals Z1 readable on karoo dark`() =
        assertReadable("Intervals Z1", intervalsPowerColorsReadableDark[0])

    @Test
    fun `Intervals Z2 readable on karoo dark`() =
        assertReadable("Intervals Z2", intervalsPowerColorsReadableDark[1])

    @Test
    fun `Intervals Z3 readable on karoo dark`() =
        assertReadable("Intervals Z3", intervalsPowerColorsReadableDark[2])

    @Test
    fun `Intervals Z4 readable on karoo dark`() =
        assertReadable("Intervals Z4", intervalsPowerColorsReadableDark[3])

    @Test
    fun `Intervals Z5 readable on karoo dark`() =
        assertReadable("Intervals Z5", intervalsPowerColorsReadableDark[4])

    @Test
    fun `Intervals Z6 readable on karoo dark`() =
        assertReadable("Intervals Z6", intervalsPowerColorsReadableDark[5])

    @Test
    fun `Intervals Z7 readable on karoo dark`() =
        assertReadable("Intervals Z7", intervalsPowerColorsReadableDark[6])

    @Test
    fun `Intervals HR Z1 readable on karoo dark`() =
        assertReadable("Intervals HR Z1", intervalsHrColorsReadableDark[0])

    @Test
    fun `Intervals HR Z2 readable on karoo dark`() =
        assertReadable("Intervals HR Z2", intervalsHrColorsReadableDark[1])

    @Test
    fun `Intervals HR Z3 readable on karoo dark`() =
        assertReadable("Intervals HR Z3", intervalsHrColorsReadableDark[2])

    @Test
    fun `Intervals HR Z4 readable on karoo dark`() =
        assertReadable("Intervals HR Z4", intervalsHrColorsReadableDark[3])

    @Test
    fun `Intervals HR Z5 readable on karoo dark`() =
        assertReadable("Intervals HR Z5", intervalsHrColorsReadableDark[4])

    @Test
    fun `Zwift Z1 readable on karoo dark`() =
        assertReadable("Zwift Z1", zwiftPowerColorsReadableDark[0])

    @Test
    fun `Zwift Z2 readable on karoo dark`() =
        assertReadable("Zwift Z2", zwiftPowerColorsReadableDark[1])

    @Test
    fun `Zwift Z3 readable on karoo dark`() =
        assertReadable("Zwift Z3", zwiftPowerColorsReadableDark[2])

    @Test
    fun `Zwift Z4 readable on karoo dark`() =
        assertReadable("Zwift Z4", zwiftPowerColorsReadableDark[3])

    @Test
    fun `Zwift Z5 readable on karoo dark`() =
        assertReadable("Zwift Z5", zwiftPowerColorsReadableDark[4])

    @Test
    fun `Zwift Z6 readable on karoo dark`() =
        assertReadable("Zwift Z6", zwiftPowerColorsReadableDark[5])

    @Test
    fun `Zwift Z7 readable on karoo dark`() =
        assertReadable("Zwift Z7", zwiftPowerColorsReadableDark[6])

    @Test
    fun `Zwift HR Z1 readable on karoo dark`() =
        assertReadable("Zwift HR Z1", zwiftHrColorsReadableDark[0])

    @Test
    fun `Zwift HR Z2 readable on karoo dark`() =
        assertReadable("Zwift HR Z2", zwiftHrColorsReadableDark[1])

    @Test
    fun `Zwift HR Z3 readable on karoo dark`() =
        assertReadable("Zwift HR Z3", zwiftHrColorsReadableDark[2])

    @Test
    fun `Zwift HR Z4 readable on karoo dark`() =
        assertReadable("Zwift HR Z4", zwiftHrColorsReadableDark[3])

    @Test
    fun `Zwift HR Z5 readable on karoo dark`() =
        assertReadable("Zwift HR Z5", zwiftHrColorsReadableDark[4])

    // --- Readability audit: light-mode palettes vs white (#FFFFFF) datafield background ---

    private val white = Color(0xFFFFFFFF)

    private fun assertReadableOnWhite(name: String, color: Color, threshold: Double = 45.0) {
        val lc = abs(apcaContrast(color, white))
        assertTrue(
            "$name hex=${color.value.toString(16).uppercase()} Lc=${"%.1f".format(lc)} < $threshold",
            lc >= threshold,
        )
    }

    @Test
    fun `every light-readable power palette zone meets contrast on white`() {
        val palettes =
            mapOf(
                "Karoo" to karooPowerColorsReadableLight,
                "Surgeonfish" to surgeonfishPowerColorsReadableLight,
                "Wahoo" to wahooPowerColorsReadableLight,
                "Intervals" to intervalsPowerColorsReadableLight,
                "Zwift" to zwiftPowerColorsReadableLight,
            )
        for ((name, colors) in palettes) {
            colors.forEachIndexed { i, c -> assertReadableOnWhite("$name Z${i + 1}", c) }
        }
    }

    @Test
    fun `every light-readable hr palette zone meets contrast on white`() {
        val palettes =
            mapOf(
                "Karoo HR" to karooHrColorsReadableLight,
                "Surgeonfish HR" to surgeonfishHrColorsReadableLight,
                "Wahoo HR" to wahooHrColorsReadableLight,
                "Intervals HR" to intervalsHrColorsReadableLight,
                "Zwift HR" to zwiftHrColorsReadableLight,
            )
        for ((name, colors) in palettes) {
            colors.forEachIndexed { i, c -> assertReadableOnWhite("$name Z${i + 1}", c) }
        }
    }

    // --- Surgeonfish palette (improved-progression sibling of Karoo) ---

    @Test
    fun `every Surgeonfish readable-dark zone meets contrast on black`() {
        surgeonfishPowerColorsReadableDark.forEachIndexed { i, c ->
            assertReadable("Surgeonfish Z${i + 1}", c)
        }
        surgeonfishHrColorsReadableDark.forEachIndexed { i, c ->
            assertReadable("Surgeonfish HR Z${i + 1}", c)
        }
    }

    // HR is the same [0,1,2,3,5] slice of the power palette Karoo uses (drops Z5 and Z7).
    @Test
    fun `Surgeonfish HR palette is the power subset 0 1 2 3 5`() {
        val subset = listOf(0, 1, 2, 3, 5)
        assertEquals(subset.map { surgeonfishPowerColors[it] }, surgeonfishHrColors)
        assertEquals(
            subset.map { surgeonfishPowerColorsReadableDark[it] },
            surgeonfishHrColorsReadableDark,
        )
        assertEquals(
            subset.map { surgeonfishPowerColorsReadableLight[it] },
            surgeonfishHrColorsReadableLight,
        )
    }

    // --- bestTextOnBackground picker ---
    // The runtime rule in FieldColors.toColorConfig: in BACKGROUND mode, pick the
    // text color (white vs black) with max APCA |Lc| against the fill color.

    private fun assertPickReadable(name: String, bg: Color, threshold: Double = 45.0) {
        val pick = bestTextOnBackground(bg)
        val lc = abs(apcaContrast(pick, bg))
        val bgHex = bg.value.toString(16).uppercase().takeLast(8)
        assertTrue("$name (bg=#$bgHex) Lc=${"%.1f".format(lc)} < $threshold", lc >= threshold)
    }

    @Test
    fun `picker chooses white on pure black`() =
        assertEquals(Color.White, bestTextOnBackground(Color.Black))

    @Test
    fun `picker chooses black on pure white`() =
        assertEquals(Color.Black, bestTextOnBackground(Color.White))

    @Test
    fun `picker yields readable contrast on every power palette zone`() {
        for (palette in ZonePalette.entries) {
            for (readable in listOf(true, false)) {
                for (zone in 1..7) {
                    assertPickReadable(
                        "$palette power Z$zone readable=$readable",
                        powerZoneColor(zone, palette, readable),
                    )
                }
            }
        }
    }

    @Test
    fun `picker yields readable contrast on every hr palette zone`() {
        for (palette in ZonePalette.entries) {
            for (readable in listOf(true, false)) {
                for (zone in 1..5) {
                    assertPickReadable(
                        "$palette HR Z$zone readable=$readable",
                        hrZoneColor(zone, palette, readable),
                    )
                }
            }
        }
    }

    @Test
    fun `picker yields readable contrast on every grade palette band`() {
        // Sweep grades wide enough to hit every band on every palette (incl. Turbo's negatives).
        val grades = generateSequence(-15.0) { if (it >= 30.0) null else it + 0.5 }.toList()
        for (palette in GradePalette.entries) {
            for (readable in listOf(true, false)) {
                val seen = mutableSetOf<ULong>()
                for (g in grades) {
                    val bg = gradeColor(g, palette, readable) ?: continue
                    if (!seen.add(bg.value)) continue
                    assertPickReadable("$palette grade=$g readable=$readable", bg)
                }
            }
        }
    }

    @Test
    fun `picker yields readable contrast across the threshold gradient`() {
        // Reconstructs thresholdBackgroundColor's lerp (Black/White → RDYLGN_RED/GREEN with sqrt).
        val red = Color(0xFFD73027)
        val green = Color(0xFF1A9850)
        for (isNight in listOf(true, false)) {
            val neutral = if (isNight) Color.Black else Color.White
            for (i in -10..10) {
                val f = i / 10f
                val end = if (f >= 0f) green else red
                val bg = lerp(neutral, end, sqrt(abs(f)))
                assertPickReadable("threshold isNight=$isNight factor=$f", bg)
            }
        }
    }

    // Turbo extremes — sanity check that the picker actually flips on bright shades.
    @Test
    fun `Turbo crimson keeps white text`() =
        assertEquals(Color.White, bestTextOnBackground(Color(0xFF8E1201)))

    @Test
    fun `Turbo dark purple keeps white text`() =
        assertEquals(Color.White, bestTextOnBackground(Color(0xFF401C4C)))

    @Test
    fun `Turbo yellow flips to black text`() =
        assertEquals(Color.Black, bestTextOnBackground(Color(0xFFF1D749)))

    @Test
    fun `Turbo lime flips to black text`() =
        assertEquals(Color.Black, bestTextOnBackground(Color(0xFFB0F94D)))

    @Test
    fun `Turbo mint flips to black text`() =
        assertEquals(Color.Black, bestTextOnBackground(Color(0xFF30F0A9)))

    // --- Zone boundary math ---

    private fun zones(vararg maxes: Int) = maxes.map { UserProfile.Zone(min = 0, max = it) }

    @Test
    fun `powerZone returns 1 below first threshold`() {
        val zones = zones(150, 200, 250, 300, 350, 400)
        assertEquals(1, powerZone(100.0, zones))
    }

    @Test
    fun `powerZone returns correct zone at exact boundary`() {
        val zones = zones(150, 200, 250)
        assertEquals(1, powerZone(150.0, zones))
        assertEquals(2, powerZone(151.0, zones))
        assertEquals(2, powerZone(200.0, zones))
    }

    @Test
    fun `powerZone returns last zone above all thresholds`() {
        val zones = zones(150, 200, 250)
        assertEquals(3, powerZone(300.0, zones))
    }

    @Test
    fun `powerZone with empty zones returns 1`() {
        assertEquals(1, powerZone(999.0, emptyList()))
    }

    @Test
    fun `hrZone returns 1 below first threshold`() {
        val zones = zones(120, 140, 160, 180)
        assertEquals(1, hrZone(100.0, zones))
    }

    @Test
    fun `hrZone returns last zone above all thresholds`() {
        val zones = zones(120, 140, 160, 180)
        assertEquals(4, hrZone(200.0, zones))
    }
}
