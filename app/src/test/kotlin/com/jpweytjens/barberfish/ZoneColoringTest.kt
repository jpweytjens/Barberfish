package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.datatype.shared.apcaContrast
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.hsluvPowerColors
import com.jpweytjens.barberfish.datatype.shared.intervalsHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.intervalsPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.karooHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.karooPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.datatype.shared.wahooHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.zwiftHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.zwiftPowerColorsReadable
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.abs
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

    @Test fun `APCA ref 40D078 on 1B2D2D is -60 3`() = assertApca("#40D078", Color(0xFF40D078), -60.3)
    @Test fun `APCA ref F0D800 on 1B2D2D is -78 5`() = assertApca("#F0D800", Color(0xFFF0D800), -78.5)
    @Test fun `APCA ref 59B962 on 1B2D2D is -50 1`() = assertApca("#59B962", Color(0xFF59B962), -50.1)
    @Test fun `APCA ref F06020 on 1B2D2D is -38 7`() = assertApca("#F06020", Color(0xFFF06020), -38.7)
    @Test fun `APCA ref 253070 on 1B2D2D is 0`()    = assertApca("#253070", Color(0xFF253070),   0.0)

    // --- Readability audit: APCA-adjusted palettes + HSLuv vs Karoo dark background ---
    // Adjusted palettes must achieve |Lc| ≥ 45 (minimum for large bold text).
    // HSLuv palette uses |Lc| ≥ 43 (colors are designed for this target, not post-adjusted).

    private fun assertReadable(name: String, color: Color, threshold: Double = 45.0) {
        val lc = abs(apcaContrast(color, karooDark))
        assertTrue("$name hex=${color.value.toString(16).uppercase()} Lc=${"%.1f".format(lc)} < $threshold", lc >= threshold)
    }

    @Test fun `Karoo Z1 readable on karoo dark`() = assertReadable("Karoo Z1", karooPowerColorsReadable[0])
    @Test fun `Karoo Z2 readable on karoo dark`() = assertReadable("Karoo Z2", karooPowerColorsReadable[1])
    @Test fun `Karoo Z3 readable on karoo dark`() = assertReadable("Karoo Z3", karooPowerColorsReadable[2])
    @Test fun `Karoo Z4 readable on karoo dark`() = assertReadable("Karoo Z4", karooPowerColorsReadable[3])
    @Test fun `Karoo Z5 readable on karoo dark`() = assertReadable("Karoo Z5", karooPowerColorsReadable[4])
    @Test fun `Karoo Z6 readable on karoo dark`() = assertReadable("Karoo Z6", karooPowerColorsReadable[5])
    @Test fun `Karoo Z7 readable on karoo dark`() = assertReadable("Karoo Z7", karooPowerColorsReadable[6])
    @Test fun `Karoo HR Z1 readable on karoo dark`() = assertReadable("Karoo HR Z1", karooHrColorsReadable[0])
    @Test fun `Karoo HR Z2 readable on karoo dark`() = assertReadable("Karoo HR Z2", karooHrColorsReadable[1])
    @Test fun `Karoo HR Z3 readable on karoo dark`() = assertReadable("Karoo HR Z3", karooHrColorsReadable[2])
    @Test fun `Karoo HR Z4 readable on karoo dark`() = assertReadable("Karoo HR Z4", karooHrColorsReadable[3])
    @Test fun `Karoo HR Z5 readable on karoo dark`() = assertReadable("Karoo HR Z5", karooHrColorsReadable[4])

    @Test fun `Wahoo Z1 readable on karoo dark`() = assertReadable("Wahoo Z1", wahooPowerColorsReadable[0])
    @Test fun `Wahoo Z2 readable on karoo dark`() = assertReadable("Wahoo Z2", wahooPowerColorsReadable[1])
    @Test fun `Wahoo Z3 readable on karoo dark`() = assertReadable("Wahoo Z3", wahooPowerColorsReadable[2])
    @Test fun `Wahoo Z4 readable on karoo dark`() = assertReadable("Wahoo Z4", wahooPowerColorsReadable[3])
    @Test fun `Wahoo Z5 readable on karoo dark`() = assertReadable("Wahoo Z5", wahooPowerColorsReadable[4])
    @Test fun `Wahoo Z6 readable on karoo dark`() = assertReadable("Wahoo Z6", wahooPowerColorsReadable[5])
    @Test fun `Wahoo Z7 readable on karoo dark`() = assertReadable("Wahoo Z7", wahooPowerColorsReadable[6])
    @Test fun `Wahoo HR Z1 readable on karoo dark`() = assertReadable("Wahoo HR Z1", wahooHrColorsReadable[0])
    @Test fun `Wahoo HR Z2 readable on karoo dark`() = assertReadable("Wahoo HR Z2", wahooHrColorsReadable[1])
    @Test fun `Wahoo HR Z3 readable on karoo dark`() = assertReadable("Wahoo HR Z3", wahooHrColorsReadable[2])
    @Test fun `Wahoo HR Z4 readable on karoo dark`() = assertReadable("Wahoo HR Z4", wahooHrColorsReadable[3])
    @Test fun `Wahoo HR Z5 readable on karoo dark`() = assertReadable("Wahoo HR Z5", wahooHrColorsReadable[4])

    @Test fun `Intervals Z1 readable on karoo dark`() = assertReadable("Intervals Z1", intervalsPowerColorsReadable[0])
    @Test fun `Intervals Z2 readable on karoo dark`() = assertReadable("Intervals Z2", intervalsPowerColorsReadable[1])
    @Test fun `Intervals Z3 readable on karoo dark`() = assertReadable("Intervals Z3", intervalsPowerColorsReadable[2])
    @Test fun `Intervals Z4 readable on karoo dark`() = assertReadable("Intervals Z4", intervalsPowerColorsReadable[3])
    @Test fun `Intervals Z5 readable on karoo dark`() = assertReadable("Intervals Z5", intervalsPowerColorsReadable[4])
    @Test fun `Intervals Z6 readable on karoo dark`() = assertReadable("Intervals Z6", intervalsPowerColorsReadable[5])
    @Test fun `Intervals Z7 readable on karoo dark`() = assertReadable("Intervals Z7", intervalsPowerColorsReadable[6])
    @Test fun `Intervals HR Z1 readable on karoo dark`() = assertReadable("Intervals HR Z1", intervalsHrColorsReadable[0])
    @Test fun `Intervals HR Z2 readable on karoo dark`() = assertReadable("Intervals HR Z2", intervalsHrColorsReadable[1])
    @Test fun `Intervals HR Z3 readable on karoo dark`() = assertReadable("Intervals HR Z3", intervalsHrColorsReadable[2])
    @Test fun `Intervals HR Z4 readable on karoo dark`() = assertReadable("Intervals HR Z4", intervalsHrColorsReadable[3])
    @Test fun `Intervals HR Z5 readable on karoo dark`() = assertReadable("Intervals HR Z5", intervalsHrColorsReadable[4])

    @Test fun `Zwift Z1 readable on karoo dark`() = assertReadable("Zwift Z1", zwiftPowerColorsReadable[0])
    @Test fun `Zwift Z2 readable on karoo dark`() = assertReadable("Zwift Z2", zwiftPowerColorsReadable[1])
    @Test fun `Zwift Z3 readable on karoo dark`() = assertReadable("Zwift Z3", zwiftPowerColorsReadable[2])
    @Test fun `Zwift Z4 readable on karoo dark`() = assertReadable("Zwift Z4", zwiftPowerColorsReadable[3])
    @Test fun `Zwift Z5 readable on karoo dark`() = assertReadable("Zwift Z5", zwiftPowerColorsReadable[4])
    @Test fun `Zwift Z6 readable on karoo dark`() = assertReadable("Zwift Z6", zwiftPowerColorsReadable[5])
    @Test fun `Zwift Z7 readable on karoo dark`() = assertReadable("Zwift Z7", zwiftPowerColorsReadable[6])
    @Test fun `Zwift HR Z1 readable on karoo dark`() = assertReadable("Zwift HR Z1", zwiftHrColorsReadable[0])
    @Test fun `Zwift HR Z2 readable on karoo dark`() = assertReadable("Zwift HR Z2", zwiftHrColorsReadable[1])
    @Test fun `Zwift HR Z3 readable on karoo dark`() = assertReadable("Zwift HR Z3", zwiftHrColorsReadable[2])
    @Test fun `Zwift HR Z4 readable on karoo dark`() = assertReadable("Zwift HR Z4", zwiftHrColorsReadable[3])
    @Test fun `Zwift HR Z5 readable on karoo dark`() = assertReadable("Zwift HR Z5", zwiftHrColorsReadable[4])

    // --- Zone boundary math ---

    private fun zones(vararg maxes: Int) =
        maxes.map { UserProfile.Zone(min = 0, max = it) }

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
