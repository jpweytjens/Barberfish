package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.hudSlotWidthPx
import org.junit.Assert.assertEquals
import org.junit.Test

class HudSlotWidthTest {
    @Test
    fun k3_hud_slots_split_the_strip_inside_its_padding() {
        // 480 px screen, 4 dp = 7 px inset per side: 466 px across the slots.
        assertEquals(116.5f, hudSlotWidthPx(480, 4, 1.875f), 0.001f)
        assertEquals(155.333f, hudSlotWidthPx(480, 3, 1.875f), 0.001f)
    }
}
