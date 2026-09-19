package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.EdgeOccurrence
import com.jpweytjens.barberfish.datatype.shared.Traversal
import com.jpweytjens.barberfish.datatype.shared.matchRepeatedEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatedEdgesTest {

    @Test
    fun a_straight_route_has_no_repeats() {
        assertTrue(matchRepeatedEdges(RouteFixtures.straight(500.0, 100.0)).isEmpty())
    }

    @Test
    fun out_and_back_matches_every_edge_in_the_opposite_direction() {
        // Edges 0,1,2 out; 3,4,5 back over the same ground: 3 with 2, 4 with 1, 5 with 0.
        val groups = matchRepeatedEdges(RouteFixtures.outAndBack(300.0, 100.0))
        assertEquals(3, groups.size)
        assertEquals(
            listOf(
                listOf(EdgeOccurrence(0, Traversal.SAME), EdgeOccurrence(5, Traversal.OPPOSITE)),
                listOf(EdgeOccurrence(1, Traversal.SAME), EdgeOccurrence(4, Traversal.OPPOSITE)),
                listOf(EdgeOccurrence(2, Traversal.SAME), EdgeOccurrence(3, Traversal.OPPOSITE)),
            ),
            groups.map { it.occurrences },
        )
    }

    @Test
    fun laps_match_every_edge_in_the_same_direction() {
        val groups = matchRepeatedEdges(RouteFixtures.laps(400.0, 3, 25.0))
        // 100 m sides in 25 m steps: 16 edges per lap, each ridden three times.
        assertEquals(16, groups.size)
        assertTrue(groups.all { g -> g.occurrences.size == 3 })
        assertTrue(groups.all { g -> g.occurrences.all { it.traversal == Traversal.SAME } })
    }

    @Test
    fun adjacent_turnaround_matches_without_a_separation_hole() {
        // A-B then B-A: the two edges share both endpoints, swapped.
        val groups = matchRepeatedEdges(listOf(point(0.0, 0.0), point(100.0, 0.0), point(0.0, 0.0)))
        assertEquals(
            listOf(
                listOf(EdgeOccurrence(0, Traversal.SAME), EdgeOccurrence(1, Traversal.OPPOSITE))
            ),
            groups.map { it.occurrences },
        )
    }

    @Test
    fun one_shared_endpoint_is_not_a_match() {
        // A-B, B-C, C-A: every edge shares one endpoint with another and no ground.
        val route = listOf(point(0.0, 0.0), point(100.0, 0.0), point(100.0, 100.0), point(0.0, 0.0))
        assertTrue(matchRepeatedEdges(route).isEmpty())
    }

    @Test
    fun endpoints_in_neighbouring_hash_cells_still_match() {
        // With 1.5 m cells, y = 1.4 and y = 1.6 fall in different cells 0.2 m apart. The 0.2 m
        // link between the legs is below the minimum edge length and is skipped.
        val route = listOf(point(0.0, 1.4), point(100.0, 1.4), point(100.0, 1.6), point(0.0, 1.6))
        val groups = matchRepeatedEdges(route)
        assertEquals(
            listOf(
                listOf(EdgeOccurrence(0, Traversal.SAME), EdgeOccurrence(2, Traversal.OPPOSITE))
            ),
            groups.map { it.occurrences },
        )
    }

    @Test
    fun legs_beyond_tolerance_stay_separate() {
        assertTrue(matchRepeatedEdges(RouteFixtures.switchback(300.0, 12.0, 100.0)).isEmpty())
        // A 2 m offset is outside 1.5 m and inside 2.5 m.
        val offset = RouteFixtures.switchback(300.0, 2.0, 100.0)
        assertTrue(matchRepeatedEdges(offset, matchM = 1.5).isEmpty())
        assertEquals(3, matchRepeatedEdges(offset, matchM = 2.5).size)
    }

    @Test
    fun zero_length_and_short_edges_never_establish_a_match() {
        // A duplicate vertex at the turn is a zero-length edge; it neither matches nor blocks.
        val dup = listOf(point(0.0, 0.0), point(100.0, 0.0), point(100.0, 0.0), point(0.0, 0.0))
        assertEquals(
            listOf(
                listOf(EdgeOccurrence(0, Traversal.SAME), EdgeOccurrence(2, Traversal.OPPOSITE))
            ),
            matchRepeatedEdges(dup).map { it.occurrences },
        )
        // A 2 m edge ridden twice is below 2 x 1.5 m and stays ordinary ground.
        val tiny = listOf(point(0.0, 0.0), point(2.0, 0.0), point(0.0, 0.0))
        assertTrue(matchRepeatedEdges(tiny).isEmpty())
    }

    @Test
    fun proximity_is_not_transitive() {
        // Three parallel 100 m edges 1.2 m apart: B joins A's group, C is 2.4 m from A and
        // must not join through B. The 1.2 m links between legs are skipped as short edges.
        val route =
            listOf(
                point(0.0, 0.0),
                point(100.0, 0.0),
                point(100.0, 1.2),
                point(0.0, 1.2),
                point(0.0, 2.4),
                point(100.0, 2.4),
            )
        val groups = matchRepeatedEdges(route)
        assertEquals(
            listOf(
                listOf(EdgeOccurrence(0, Traversal.SAME), EdgeOccurrence(2, Traversal.OPPOSITE))
            ),
            groups.map { it.occurrences },
        )
    }

    @Test
    fun a_pass_sampled_as_one_edge_misses_a_pass_sampled_as_two() {
        // Out over A-B-C, back over C-A in one edge: documented miss, no match.
        val route = listOf(point(0.0, 0.0), point(100.0, 0.0), point(200.0, 0.0), point(0.0, 0.0))
        assertTrue(matchRepeatedEdges(route).isEmpty())
    }

    @Test
    fun reversal_matches_the_same_ground() {
        val forward = matchRepeatedEdges(RouteFixtures.outAndBack(300.0, 100.0))
        val reversed = matchRepeatedEdges(RouteFixtures.outAndBack(300.0, 100.0).asReversed())
        assertEquals(forward.size, reversed.size)
        assertEquals(
            forward.map { it.occurrences.map { o -> o.traversal } },
            reversed.map { it.occurrences.map { o -> o.traversal } },
        )
    }
}
