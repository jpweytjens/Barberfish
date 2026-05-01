package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.valueBitmapHeightPx
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapValueGeometryTest {
    @Test
    fun `bitmap height equals 0 point 8 times valueFontBase scaled by density (5x2)`() {
        // 5x2: valueFontBase=47, K3 density=1.875 -> 0.8 x 47 x 1.875 = 70.5 -> 70 (truncated).
        assertEquals(70, valueBitmapHeightPx(valueFontBaseSp = 47, density = 1.875f))
    }

    @Test
    fun `bitmap height for 1x1 native font`() {
        // 1x1: valueFontBase=96 -> 0.8 x 96 x 1.875 = 144.
        assertEquals(144, valueBitmapHeightPx(valueFontBaseSp = 96, density = 1.875f))
    }

    @Test
    fun `bitmap height for HUD 3-col slot`() {
        // HUD_THREE: valueFontBase=42 -> 0.8 x 42 x 1.875 = 63.
        assertEquals(63, valueBitmapHeightPx(valueFontBaseSp = 42, density = 1.875f))
    }

    @Test
    fun `bitmap height has minimum to keep tiny fonts legible`() {
        // Pathological tiny font shouldn't yield a 0-px bitmap.
        assertEquals(30, valueBitmapHeightPx(valueFontBaseSp = 5, density = 1.875f))
    }
}
