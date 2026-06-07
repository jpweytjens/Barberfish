package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLngBounds
import com.jpweytjens.barberfish.datatype.shared.mercatorBoundsAspect
import com.jpweytjens.barberfish.datatype.shared.projectToUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapProjectionTest {

    private val bounds = LatLngBounds(minLat = 0.0, maxLat = 1.0, minLng = 0.0, maxLng = 2.0)

    @Test
    fun `north-west corner maps to top-left`() {
        val (u, v) = projectToUnit(bounds, lat = 1.0, lng = 0.0)
        assertEquals(0.0, u, 1e-9)
        assertEquals(0.0, v, 1e-9)
    }

    @Test
    fun `south-east corner maps to bottom-right`() {
        val (u, v) = projectToUnit(bounds, lat = 0.0, lng = 2.0)
        assertEquals(1.0, u, 1e-9)
        assertEquals(1.0, v, 1e-9)
    }

    @Test
    fun `longitude is linear`() {
        val (u, _) = projectToUnit(bounds, lat = 0.5, lng = 1.0)
        assertEquals(0.5, u, 1e-9)
    }

    @Test
    fun `latitude v decreases toward north (mercator monotonic)`() {
        val (_, vNorth) = projectToUnit(bounds, lat = 0.9, lng = 0.0)
        val (_, vSouth) = projectToUnit(bounds, lat = 0.1, lng = 0.0)
        assertTrue("north point sits higher (smaller v) than south point", vNorth < vSouth)
    }

    @Test
    fun `aspect is mercator width over height`() {
        // xSpan = 2 deg = 0.0349066 rad; ySpan = mercatorY(1)-mercatorY(0) = 0.0174542
        assertEquals(2.0000, mercatorBoundsAspect(bounds), 1e-3)
    }
}
