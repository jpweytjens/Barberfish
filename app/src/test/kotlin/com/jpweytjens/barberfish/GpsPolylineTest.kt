package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.extractSubPolyline
import com.jpweytjens.barberfish.datatype.shared.projectPoiAlongRoute
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsPolylineTest {

    // Canonical Google example decodes to (38.5, -120.2), (40.7, -120.95), (43.252, -126.453).
    // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
    private val canonical = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun decodeGpsPolyline_canonical_example() {
        val points = decodeGpsPolyline(canonical)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-5)
        assertEquals(-120.2, points[0].lng, 1e-5)
        assertEquals(40.7, points[1].lat, 1e-5)
        assertEquals(-120.95, points[1].lng, 1e-5)
        assertEquals(43.252, points[2].lat, 1e-5)
        assertEquals(-126.453, points[2].lng, 1e-5)
    }

    @Test
    fun encodeGpsPolyline_roundtrip_canonical_example() {
        val points = decodeGpsPolyline(canonical)
        val reEncoded = encodeGpsPolyline(points)
        assertEquals(canonical, reEncoded)
    }

    @Test
    fun decodeGpsPolyline_blank_returns_empty() {
        assertTrue(decodeGpsPolyline("").isEmpty())
    }

    @Test
    fun encodeGpsPolyline_empty_returns_empty_string() {
        assertEquals("", encodeGpsPolyline(emptyList()))
    }

    @Test
    fun cumulativeDistancesM_one_degree_latitude_is_about_111km() {
        val points = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        val cum = cumulativeDistancesM(points)
        assertEquals(2, cum.size)
        assertEquals(0.0, cum[0], 0.0)
        // 1° of latitude ≈ 111_195 m on a 6_371_000 m sphere. Allow ±1%.
        val expected = 111_195.0
        assertTrue("got=${cum[1]}", abs(cum[1] - expected) / expected < 0.01)
    }

    @Test
    fun cumulativeDistancesM_single_point_returns_zero() {
        val cum = cumulativeDistancesM(listOf(LatLng(10.0, 20.0)))
        assertEquals(1, cum.size)
        assertEquals(0.0, cum[0], 0.0)
    }

    // A 4-point straight line along a meridian. Cumulative distances:
    //   0 → 0 m, 1 → ~111195 m, 2 → ~222390 m, 3 → ~333585 m
    // We rescale by constructing a synthetic cumDist to get exact 0/100/200/300 m values.
    private val straightPoints =
        listOf(
            LatLng(0.0, 0.0),
            LatLng(0.001, 0.0),
            LatLng(0.002, 0.0),
            LatLng(0.003, 0.0),
        )
    private val straightCum = doubleArrayOf(0.0, 100.0, 200.0, 300.0)

    @Test
    fun extractSubPolyline_midway_interpolates_endpoints() {
        val sub = extractSubPolyline(straightPoints, straightCum, 50.0, 250.0)
        assertEquals(4, sub.size)
        // First point interpolated midway between points[0] and points[1] (t=0.5 along lat).
        assertEquals(0.0005, sub[0].lat, 1e-9)
        assertEquals(0.001, sub[1].lat, 1e-12) // raw
        assertEquals(0.002, sub[2].lat, 1e-12) // raw
        assertEquals(0.0025, sub[3].lat, 1e-9) // interpolated
    }

    @Test
    fun extractSubPolyline_full_range_returns_all_points() {
        val sub = extractSubPolyline(straightPoints, straightCum, 0.0, 300.0)
        assertEquals(4, sub.size)
        assertEquals(0.0, sub.first().lat, 1e-12)
        assertEquals(0.003, sub.last().lat, 1e-12)
    }

    @Test
    fun extractSubPolyline_empty_range_returns_empty() {
        assertTrue(extractSubPolyline(straightPoints, straightCum, 50.0, 50.0).isEmpty())
        assertTrue(extractSubPolyline(straightPoints, straightCum, 100.0, 50.0).isEmpty())
    }

    @Test
    fun extractSubPolyline_endM_beyond_total_clamps() {
        val sub = extractSubPolyline(straightPoints, straightCum, 100.0, 500.0)
        // Clamped to [100, 300]; first point is exact vertex at 100, last point is exact vertex at
        // 300.
        assertEquals(0.001, sub.first().lat, 1e-12)
        assertEquals(0.003, sub.last().lat, 1e-12)
    }

    // A due-east segment on the equator from (0,0) to (0,0.01). A POI 0.001° north of the
    // segment midpoint projects to (0,0.005): ~111 m off-route, ~556 m along-route.
    @Test
    fun projectPoiAlongRoute_returns_along_distance_within_corridor() {
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01))
        val cum = cumulativeDistancesM(route)
        val along = projectPoiAlongRoute(LatLng(0.001, 0.005), route, cum, 150.0)
        assertNotNull(along)
        assertEquals(556.0, along ?: Double.NaN, 5.0)
    }

    @Test
    fun projectPoiAlongRoute_returns_null_beyond_corridor() {
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01))
        val cum = cumulativeDistancesM(route)
        // ~111 m off-route; a 100 m corridor excludes it.
        val along = projectPoiAlongRoute(LatLng(0.001, 0.005), route, cum, 100.0)
        assertNull(along)
    }

    @Test
    fun projectPoiAlongRoute_clamps_past_segment_end_to_endpoint() {
        // POI beyond the segment end projects to the endpoint (t clamped to 1).
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01))
        val cum = cumulativeDistancesM(route)
        val along = projectPoiAlongRoute(LatLng(0.0, 0.02), route, cum, 2000.0)
        assertNotNull(along)
        assertEquals(cum.last(), along ?: Double.NaN, 1.0)
    }

    @Test
    fun projectPoiAlongRoute_degenerate_route_returns_null() {
        val along =
            projectPoiAlongRoute(LatLng(0.0, 0.0), listOf(LatLng(0.0, 0.0)), doubleArrayOf(0.0), 500.0)
        assertNull(along)
    }

    @Test
    fun projecting_onto_a_reversed_route_mirrors_the_distance() {
        val pts =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.0, 0.01),
                LatLng(0.0, 0.02),
                LatLng(0.0, 0.03),
            )
        val cum = cumulativeDistancesM(pts)
        val reversedPts = pts.asReversed()
        val reversedCum = cumulativeDistancesM(reversedPts)
        val poi = LatLng(0.0, 0.005)

        val forward =
            projectPoiAlongRoute(poi, pts, cum, 500.0) ?: error("expected a forward projection")
        val reversed =
            projectPoiAlongRoute(poi, reversedPts, reversedCum, 500.0)
                ?: error("expected a reversed projection")

        // Same physical point, measured from opposite ends, so the two must sum to
        // the route length.
        assertEquals(cum.last(), forward + reversed, 1.0)
    }
}
