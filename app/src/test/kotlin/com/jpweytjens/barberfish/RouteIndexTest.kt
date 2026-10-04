package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.LatLngBounds
import com.jpweytjens.barberfish.datatype.shared.RouteIndex
import com.jpweytjens.barberfish.datatype.shared.buildRouteIndex
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.distanceToBoundsM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteIndexTest {

    private fun index(points: List<LatLng>): RouteIndex =
        buildRouteIndex(points, cumulativeDistancesM(points))

    private fun assertTiles(index: RouteIndex) {
        assertEquals(0.0, index.visits.first().startM, 1e-6)
        assertEquals(index.lengthM, index.visits.last().endM, 1e-6)
        index.visits.zipWithNext().forEach { (a, b) -> assertEquals(a.endM, b.startM, 1e-6) }
        assertTrue(index.visits.all { it.endM > it.startM })
        assertEquals(index.visits.indices.toList(), index.visits.map { it.key })
    }

    @Test
    fun a_straight_route_is_one_visit() {
        val index = index(RouteFixtures.straight(500.0, 100.0))
        assertEquals(1, index.visits.size)
        val only = index.visits.single()
        assertEquals(0, only.key)
        assertEquals(1, only.depth)
        assertNull(only.nextVisitM)
        assertEquals(500.0, only.endM, 1e-6)
        assertTiles(index)
    }

    @Test
    fun out_and_back_stacks_two_visits_per_edge() {
        val index = index(RouteFixtures.outAndBack(300.0, 100.0))
        assertEquals(6, index.visits.size)
        assertTiles(index)
        assertEquals(listOf(2, 2, 2, 1, 1, 1), index.visits.map { it.depth })
        // Visit 0 (0-100 m) is ridden again as visit 5 (500-600 m); visit 2 turns straight
        // into visit 3 at the apex.
        assertEquals(500.0, index.visit(0).nextVisitM ?: -1.0, 1e-6)
        assertEquals(300.0, index.visit(2).nextVisitM ?: -1.0, 1e-6)
        assertNull(index.visit(3).nextVisitM)
        assertEquals(index.visit(0).unit, index.visit(5).unit)
        assertEquals(listOf(0, 5), index.visitsOfUnit(index.visit(0).unit).map { it.key })
    }

    @Test
    fun unmatched_ground_between_repeats_is_one_visit() {
        // 300 m out, 100 m back: the first 200 m is ordinary ground, the last edge is repeated.
        val route = RouteFixtures.straight(300.0, 100.0) + point(200.0, 0.0)
        val index = index(route)
        assertTiles(index)
        assertEquals(3, index.visits.size)
        assertEquals(200.0, index.visit(0).endM, 1e-6)
        assertEquals(listOf(1, 2, 1), index.visits.map { it.depth })
        assertEquals(300.0, index.visit(1).nextVisitM ?: -1.0, 1e-6)
    }

    @Test
    fun laps_hold_three_visits_per_unit_with_descending_depth() {
        val index = index(RouteFixtures.laps(400.0, 3, 25.0))
        assertTiles(index)
        assertEquals(48, index.visits.size)
        index.visits
            .groupBy { it.unit }
            .values
            .forEach { visits ->
                assertEquals(listOf(3, 2, 1), visits.map { it.depth })
            }
    }

    @Test
    fun a_duplicate_vertex_inside_a_repeat_adds_no_visit() {
        val dup = listOf(point(0.0, 0.0), point(100.0, 0.0), point(100.0, 0.0), point(0.0, 0.0))
        val index = index(dup)
        assertTiles(index)
        assertEquals(2, index.visits.size)
    }

    @Test
    fun visitAt_is_start_inclusive_end_exclusive_and_closed_at_the_route_end() {
        val index = index(RouteFixtures.outAndBack(300.0, 100.0))
        assertEquals(0, index.visitAt(0.0)?.key)
        assertEquals(0, index.visitAt(99.9)?.key)
        assertEquals(1, index.visitAt(100.0)?.key)
        assertEquals(5, index.visitAt(600.0)?.key)
        assertNull(index.visitAt(600.1))
        assertNull(index.visitAt(-1.0))
    }

    @Test
    fun building_twice_gives_the_same_index() {
        val points = RouteFixtures.laps(400.0, 2, 100.0)
        assertEquals(index(points).visits, index(points).visits)
    }

    @Test
    fun the_polyline_overload_applies_reversal() {
        // Compare on the decoded polyline: encoding quantises the fixture's coordinates.
        val encoded =
            RouteFixtures.encoded(RouteFixtures.straight(300.0, 100.0) + point(200.0, 0.0))
        val fromPoints = index(decodeGpsPolyline(encoded).asReversed())
        val fromPolyline = buildRouteIndex(encoded, reversed = true)
        assertEquals(fromPoints.visits, fromPolyline.visits)
    }

    @Test
    fun distance_to_bounds_is_zero_inside_and_edge_distance_outside() {
        val bounds =
            LatLngBounds(
                minLat = 0.0,
                maxLat = 0.0,
                minLng = 0.0,
                maxLng = 100.0 / RouteFixtures.M_PER_DEG,
            )
        assertEquals(0.0, distanceToBoundsM(point(50.0, 0.0), bounds), 1e-6)
        assertEquals(40.0, distanceToBoundsM(point(140.0, 0.0), bounds), 0.01)
        assertEquals(30.0, distanceToBoundsM(point(50.0, 30.0), bounds), 0.01)
    }
}
