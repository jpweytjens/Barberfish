package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.RideVisibility
import com.jpweytjens.barberfish.datatype.shared.RouteIndex
import com.jpweytjens.barberfish.datatype.shared.buildRouteIndex
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.interpolateAt
import com.jpweytjens.barberfish.datatype.shared.placeChevronsByCadence
import com.jpweytjens.barberfish.datatype.shared.selectChevrons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideVisibilityTest {

    private fun index(points: List<LatLng>): RouteIndex =
        buildRouteIndex(points, cumulativeDistancesM(points))

    // 300 m out and back: visits 0,1,2 out (depth 2), 3,4,5 back (depth 1).
    private val short = index(RouteFixtures.outAndBack(300.0, 100.0))

    // 2 km out and back, for the off-screen clause.
    private val long = index(RouteFixtures.outAndBack(2000.0, 100.0))

    private fun chevron(index: RouteIndex, distanceM: Double): ClimbChevronSpec {
        val at = interpolateAt(index.gps, index.cumDist, distanceM)
        return ClimbChevronSpec("c${distanceM.toInt()}", at.lat, at.lng, 90f, 0, distanceM)
    }

    @Test
    fun at_progress_zero_the_first_visits_are_exposed_and_return_chevrons_are_ineligible() {
        val visibility = RideVisibility(short)
        short.visits.forEach { v ->
            assertTrue(visibility.pieceDrawable(v.key))
        }
        assertEquals(0, visibility.exposedVisit(short.visit(0).unit)?.key)
        assertTrue(visibility.chevronDrawable(50.0))
        assertFalse(visibility.chevronDrawable(550.0))
    }

    @Test
    fun a_repeated_visit_cannot_hide_before_its_end() {
        val visibility = RideVisibility(short)
        // Rider far away and the next visit within radius: both clauses fire for the ridden
        // visits 0 and 1, and neither may touch visit 2, which ends at 300 m.
        visibility.update(250.0, point(5000.0, 0.0), viewRadiusM = 100.0)
        assertEquals(setOf(0, 1), visibility.hiddenVisits)
    }

    @Test
    fun at_the_apex_the_outbound_visit_hands_over_to_the_return_visit() {
        val visibility = RideVisibility(short)
        assertTrue(visibility.update(300.0, null, viewRadiusM = 100.0))
        // Visit 2 ends at the apex and its return visit starts there; visit 1 returns at 400 m,
        // inside the 100 m radius; visit 0 returns at 500 m, outside it.
        assertEquals(setOf(1, 2), visibility.hiddenVisits)
        assertEquals(3, visibility.exposedVisit(short.visit(2).unit)?.key)
        assertFalse(visibility.pieceDrawable(2))
        assertTrue(visibility.pieceDrawable(0))
        visibility.update(400.0, null, viewRadiusM = 100.0)
        assertEquals(setOf(0, 1, 2), visibility.hiddenVisits)
    }

    @Test
    fun a_ridden_repeated_visit_hides_when_out_of_view_and_stays_when_position_is_missing() {
        val inView = RideVisibility(long)
        inView.update(100.0, point(150.0, 0.0), viewRadiusM = 500.0)
        assertTrue(inView.hiddenVisits.isEmpty())

        val outOfView = RideVisibility(long)
        outOfView.update(100.0, point(700.0, 0.0), viewRadiusM = 500.0)
        assertEquals(setOf(0), outOfView.hiddenVisits)

        val noFix = RideVisibility(long)
        noFix.update(100.0, null, viewRadiusM = 500.0)
        assertTrue(noFix.hiddenVisits.isEmpty())
    }

    @Test
    fun a_final_visit_stays_drawn_behind_the_rider_and_loses_only_its_passed_chevrons() {
        val visibility = RideVisibility(short)
        visibility.update(350.0, null, viewRadiusM = 100.0)
        // Visit 3 (300-400 m) has no successor, so nothing beneath it needs uncovering: its
        // pieces stay although the rider is halfway along it.
        assertTrue(visibility.pieceDrawable(3))
        assertTrue(visibility.pieceDrawable(4))
        assertTrue(visibility.hiddenVisits.none { it == 3 })
        assertFalse(visibility.chevronDrawable(320.0))
        assertTrue(visibility.chevronDrawable(380.0))
    }

    @Test
    fun progress_never_moves_backwards_and_a_repeat_stays_hidden_on_rebuild() {
        val visibility = RideVisibility(short)
        visibility.update(400.0, null, viewRadiusM = 100.0)
        assertFalse(visibility.update(100.0, null, viewRadiusM = 100.0))
        assertEquals(400.0, visibility.progressM, 0.0)
        assertEquals(setOf(0, 1, 2), visibility.hiddenVisits)
    }

    @Test
    fun chevrons_on_a_retained_visit_stay_until_it_hides() {
        val visibility = RideVisibility(short)
        visibility.update(150.0, null, viewRadiusM = 100.0)
        // Visit 0 is ridden and retained: its mark keeps the direction cue.
        assertTrue(visibility.chevronDrawable(50.0))
        assertFalse(visibility.chevronDrawable(550.0))
        visibility.update(400.0, null, viewRadiusM = 100.0)
        assertFalse(visibility.chevronDrawable(50.0))
        assertTrue(visibility.chevronDrawable(550.0))
        // A passed mark on a final visit hides without waiting for the whole visit.
        assertFalse(visibility.chevronDrawable(350.0))
        assertTrue(visibility.chevronDrawable(450.0))
    }

    @Test
    fun selection_drops_colliding_marks_but_keeps_them_for_later() {
        val visibility = RideVisibility(short)
        val outbound = chevron(short, 50.0)
        // The return mark at 550 m sits on the same ground as the outbound mark at 50 m.
        val returning = chevron(short, 550.0)
        assertEquals(
            listOf(outbound),
            selectChevrons(listOf(outbound, returning), visibility, 20.0, short.gps.first(), 1e6),
        )
        visibility.update(500.0, null, viewRadiusM = 100.0)
        assertEquals(
            listOf(returning),
            selectChevrons(listOf(outbound, returning), visibility, 20.0, short.gps.first(), 1e6),
        )
    }

    @Test
    fun offset_cadence_return_marks_survive_placement_and_become_drawable() {
        // 1 km out and back, marks every 100 m from 50 m: the return mark at 550 m lands on the
        // outbound mark at 450 m. Collision filtering at placement used to delete it for good.
        val route = RouteFixtures.outAndBack(500.0, 100.0)
        val index = index(route)
        val placements =
            placeChevronsByCadence(
                gps = index.gps,
                cumDist = index.cumDist,
                gradeAtM = { 0.0 },
                changeAtM = { 0.0 },
                alpha = 0.0,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.2,
                spacingMaxM = 100.0,
                spacingMinM = 100.0,
                collisionRadiusM = 0.0,
                windowHalfM = 10.0,
            )
        val candidates = placements.mapIndexed { i, p ->
            ClimbChevronSpec("m$i", p.lat, p.lng, p.bearingDeg, 0, p.distanceM)
        }
        assertEquals(10, candidates.size)
        val visibility = RideVisibility(index)
        val atStart = selectChevrons(candidates, visibility, 20.0, index.gps.first(), 1e6)
        assertEquals(listOf(50.0, 150.0, 250.0, 350.0, 450.0), atStart.map { it.distanceM })
        visibility.update(500.0, null, viewRadiusM = 1000.0)
        val afterApex = selectChevrons(candidates, visibility, 20.0, index.gps.first(), 1e6)
        assertEquals(listOf(550.0, 650.0, 750.0, 850.0, 950.0), afterApex.map { it.distanceM })
    }

    @Test
    fun selection_keeps_only_marks_within_the_view_radius() {
        // Same out-and-back; the rider stands at the start with a 200 m window, so only the
        // marks on the first 200 m of ground qualify, on whichever visit is exposed.
        val route = RouteFixtures.outAndBack(500.0, 100.0)
        val index = index(route)
        val candidates =
            (50..950 step 100).map { m ->
                val p = interpolateAt(index.gps, index.cumDist, m.toDouble())
                ClimbChevronSpec("m$m", p.lat, p.lng, 0f, 0, m.toDouble())
            }
        val visibility = RideVisibility(index)
        val start = index.gps.first()
        assertEquals(
            listOf(50.0, 150.0),
            selectChevrons(candidates, visibility, 20.0, start, 200.0).map { it.distanceM },
        )
        visibility.update(500.0, null, viewRadiusM = 1000.0)
        assertEquals(
            listOf(850.0, 950.0),
            selectChevrons(candidates, visibility, 20.0, start, 200.0).map { it.distanceM },
        )
    }
}
