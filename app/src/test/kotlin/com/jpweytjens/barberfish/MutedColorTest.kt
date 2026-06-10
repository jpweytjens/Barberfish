package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.MutedFillGrey
import com.jpweytjens.barberfish.datatype.shared.MutedTextGrey
import com.jpweytjens.barberfish.datatype.shared.apcaContrast
import com.jpweytjens.barberfish.datatype.shared.toColorConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// FieldColor.Muted (grade Stale state) must honour colorMode: grey text in TEXT/NONE,
// grey fill in BACKGROUND. The greys are APCA-readable against both datafield backgrounds.
class MutedColorTest {

    private val black = Color(0xFF000000)
    private val white = Color(0xFFFFFFFF)

    @Test
    fun `muted TEXT mode is grey text, no fill`() {
        for (night in listOf(true, false)) {
            val c = FieldColor.Muted.toColorConfig(ZoneColorMode.TEXT, isNightMode = night)
            assertNull("no fill in TEXT (night=$night)", c.background)
            assertEquals("grey value text (night=$night)", MutedTextGrey, c.valueText)
        }
    }

    @Test
    fun `muted NONE mode is grey text, no fill`() {
        val c = FieldColor.Muted.toColorConfig(ZoneColorMode.NONE, isNightMode = true)
        assertNull(c.background)
        assertEquals(MutedTextGrey, c.valueText)
    }

    @Test
    fun `muted BACKGROUND mode fills grey with readable text`() {
        for (night in listOf(true, false)) {
            val c = FieldColor.Muted.toColorConfig(ZoneColorMode.BACKGROUND, isNightMode = night)
            assertEquals("grey fill (night=$night)", MutedFillGrey, c.background)
            assertTrue(
                "value text readable on fill (night=$night)",
                abs(apcaContrast(c.valueText, MutedFillGrey)) >= 45.0,
            )
            // header and icon track the fill's best text colour too
            assertEquals("header matches value on fill", c.valueText, c.headerText)
            assertEquals("icon matches value on fill", c.valueText, c.iconTint)
        }
    }

    @Test
    fun `muted text grey is readable on both datafield backgrounds`() {
        assertTrue("on black", abs(apcaContrast(MutedTextGrey, black)) >= 45.0)
        assertTrue("on white", abs(apcaContrast(MutedTextGrey, white)) >= 45.0)
    }
}
