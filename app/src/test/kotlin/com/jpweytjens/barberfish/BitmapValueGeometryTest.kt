package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.valueBitmapHeightPx
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapValueGeometryTest {
    @Test
    fun `bitmap height equals 0 point 74 times valueFontBase scaled by density (5x2)`() {
        // 5x2: valueFontBase=47, K3 density=1.875 -> 0.74 x 47 x 1.875 = 65.2 -> 65 (truncated).
        assertEquals(65, valueBitmapHeightPx(valueFontBaseSp = 47, density = 1.875f))
    }

    @Test
    fun `bitmap height for 1x1 native font`() {
        // 1x1: valueFontBase=96 -> 0.74 x 96 x 1.875 = 133.2 -> 133 (truncated).
        assertEquals(133, valueBitmapHeightPx(valueFontBaseSp = 96, density = 1.875f))
    }

    @Test
    fun `bitmap height for 5x1 fits inside tight baseline_box`() {
        // 5x1 is the tightest layout: valueFontBase=55 -> 0.74 x 55 x 1.875 = 76.3
        // -> 76. baseline_box is ~79 px so this fits without ImageView fitCenter
        // downscaling; at the prior 0.8 ratio it was 82 px and got scaled down.
        assertEquals(76, valueBitmapHeightPx(valueFontBaseSp = 55, density = 1.875f))
    }

    @Test
    fun `bitmap height for HUD 3-col slot`() {
        // HUD_THREE: valueFontBase=42 -> 0.74 x 42 x 1.875 = 58.27 -> 58.
        assertEquals(58, valueBitmapHeightPx(valueFontBaseSp = 42, density = 1.875f))
    }

    @Test
    fun `bitmap height has minimum to keep tiny fonts legible`() {
        // Pathological tiny font shouldn't yield a 0-px bitmap.
        assertEquals(30, valueBitmapHeightPx(valueFontBaseSp = 5, density = 1.875f))
    }
}
