package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationStripTest {

    // Precision-1 Google Encoded Polyline for (0m, 100m), (500m, 110m), (1000m, 95m).
    // Confirmed format from Task 5 spike: lat = cumulative distance m, lng = elevation m, divisor = 10.
    // Computed manually: encode [(0,1000),(5000,1100),(10000,950)] as standard polyline deltas.
    private val FIXTURE_POLYLINE = "?o}@owHgEowHjH"

    @Test fun decoder_returns_expected_points() {
        val points = decodeElevationPolyline(FIXTURE_POLYLINE)
        assertEquals(3, points.size)
        assertEquals(0f,    points[0].first,  1f)
        assertEquals(100f,  points[0].second, 1f)
        assertEquals(500f,  points[1].first,  1f)
        assertEquals(110f,  points[1].second, 1f)
        assertEquals(1000f, points[2].first,  1f)
        assertEquals(95f,   points[2].second, 1f)
    }

    @Test fun decoder_returns_empty_list_for_blank_input() {
        assertTrue(decodeElevationPolyline("").isEmpty())
    }
}
