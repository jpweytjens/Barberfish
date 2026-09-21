package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.WindArrowGeometry
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
}
