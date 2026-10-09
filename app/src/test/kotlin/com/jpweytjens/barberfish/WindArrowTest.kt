package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.WindArrowGeometry
import com.jpweytjens.barberfish.datatype.shared.windArrowBoxPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindArrowTest {

    private val box = 100f

    @Test
    fun proportions_match_the_mockup() {
        assertEquals(78f, WindArrowGeometry.shaftPx(box), 0.001f)
        assertEquals(11f, WindArrowGeometry.strokePx(box), 0.001f)
        assertEquals(24f, WindArrowGeometry.headOffsetPx(box), 0.001f)
    }

    @Test
    fun the_rotated_arrow_stays_inside_its_box() {
        // Half the shaft plus the round cap is the farthest any ink gets from the centre.
        assertTrue(WindArrowGeometry.sweepRadiusPx(box) <= box / 2f)
    }

    @Test
    fun the_head_offset_stays_short_of_the_half_shaft() {
        assertTrue(WindArrowGeometry.headOffsetPx(box) < WindArrowGeometry.shaftPx(box) / 2f)
    }

    @Test
    fun a_wide_cell_keeps_the_full_height_arrow() {
        // 149 px cell, 7 px gap, 75 px reference: 67 px free, capped at the 60 px value height.
        assertEquals(60, windArrowBoxPx(60, 149f, 75f, 7))
    }

    @Test
    fun a_narrow_cell_gives_the_arrow_what_the_reference_leaves() {
        // 110 px cell, 7 px gap, 60 px reference: 43 px, between half and full height.
        assertEquals(43, windArrowBoxPx(60, 110f, 60f, 7))
    }

    @Test
    fun the_arrow_never_drops_below_half_the_value_height() {
        // 110 px cell, 7 px gap, 90 px reference: 13 px free, floored at 30 px.
        assertEquals(30, windArrowBoxPx(60, 110f, 90f, 7))
    }
}
