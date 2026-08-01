package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ChevronTuning
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.placeChevrons
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChevronPlacementTest {

    // 1 degree of longitude at the equator is 111_320 m, so 0.001 deg is 111.32 m.
    private fun straightEast(pointCount: Int, stepDeg: Double = 0.001): List<LatLng> =
        (0 until pointCount).map { LatLng(0.0, it * stepDeg) }

    // Turns turnDegPerEdge degrees at every edgeLengthM-metre edge, starting due east from
    // the origin. Used for the post-loop-acceptance fixture: a gentle, continuous curve
    // whose local bearing spread sits between the early-accept and heading thresholds.
    private fun curvingRoute(
        pointCount: Int,
        edgeLengthM: Double = 20.0,
        turnDegPerEdge: Double = 4.0,
    ): List<LatLng> {
        var lat = 0.0
        var lng = 0.0
        var headingDeg = 90.0
        val points = mutableListOf(LatLng(lat, lng))
        repeat(pointCount - 1) {
            val rad = headingDeg * PI / 180.0
            lat += edgeLengthM * cos(rad) / 111_320.0
            lng += edgeLengthM * sin(rad) / 111_320.0
            points += LatLng(lat, lng)
            headingDeg += turnDegPerEdge
        }
        return points
    }

    // 1 metre of latitude or longitude at the equator is 1/111_320 degrees.
    private fun metersToDeg(m: Double): Double = m / 111_320.0

    private fun tuning(
        spacingM: Double = 100.0,
        windowHalfM: Double = 0.0,
        collisionRadiusM: Double = 0.0,
        headingThresholdDeg: Double = 0.0,
    ) = ChevronTuning(spacingM, windowHalfM, collisionRadiusM, headingThresholdDeg)

    @Test
    fun straight_route_places_on_the_half_phase_cadence() {
        val gps = straightEast(6) // 5 segments, ~556 m
        val cum = cumulativeDistancesM(gps)
        val placed = placeChevrons(gps, cum, tuning(spacingM = 100.0))
        // Cadence is 50, 150, 250, 350, 450, and 550 is inside the 556 m route.
        assertEquals(listOf(50.0, 150.0, 250.0, 350.0, 450.0, 550.0), placed.map { it.distanceM })
    }

    @Test
    fun empty_or_degenerate_input_places_nothing() {
        assertTrue(placeChevrons(emptyList(), doubleArrayOf(), tuning()).isEmpty())
        val gps = straightEast(2)
        assertTrue(placeChevrons(gps, cumulativeDistancesM(gps), tuning(spacingM = 0.0)).isEmpty())
    }

    @Test
    fun bearing_points_along_travel_on_a_straight_route() {
        val gps = straightEast(6)
        val placed = placeChevrons(gps, cumulativeDistancesM(gps), tuning(spacingM = 100.0))
        // Due east is 90 degrees clockwise from north.
        placed.forEach { assertEquals(90.0f, it.bearingDeg, 0.5f) }
    }

    @Test
    fun collision_radius_skips_candidates_and_stretches_the_cadence() {
        // Collision radius 1.5 spacings: at the 150 cursor step the furthest candidate is
        // 175, still only 125 m from the chevron at 50, so every one of the seven collides
        // and nothing is placed. 250 is 200 m clear, so it places. The cadence doubles.
        // The radius is deliberately not exactly one spacing, which would put a candidate
        // exactly on the boundary and make the test turn on a rounding digit.
        val gps = straightEast(10)
        val placed =
            placeChevrons(
                gps,
                cumulativeDistancesM(gps),
                tuning(spacingM = 100.0, collisionRadiusM = 150.0),
            )
        assertEquals(listOf(50.0, 250.0, 450.0, 650.0, 850.0), placed.map { it.distanceM })
    }

    @Test
    fun sharp_bend_nudges_the_chevron_instead_of_dropping_it() {
        // Right angle at 200 m, then straight north. Spacing 100 m puts a cadence point at
        // 150, whose 60 m window spans the corner. Offset -0.25 lands at 125 m, whose window
        // clears the corner, so it is early-accepted; the full trace for this fixture is
        // 50, 125, 262.5, 362.5.
        val east = 0.001797 // ~200 m east at the equator
        val gps =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.0, east),
                LatLng(0.001797, east),
            )
        val cum = cumulativeDistancesM(gps)
        val placed =
            placeChevrons(
                gps,
                cum,
                tuning(spacingM = 100.0, windowHalfM = 60.0, headingThresholdDeg = 30.0),
            )
        val near150 = placed.map { it.distanceM }.filter { it in 100.0..200.0 }
        assertEquals("expected exactly one chevron nudged near the bend", 1, near150.size)
        assertTrue(
            "expected the chevron to move off the nominal 150 m position, got ${near150[0]}",
            abs(near150[0] - 150.0) > 1.0,
        )
    }

    @Test
    fun bend_too_sharp_for_every_offset_drops_the_position() {
        // Same corner, but a window wide enough that all seven offsets span it. Nothing is
        // placed between 100 and 300 m, and the cadence resumes on the far leg.
        val east = 0.001797
        val gps =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.0, east),
                LatLng(0.003594, east),
            )
        val cum = cumulativeDistancesM(gps)
        val placed =
            placeChevrons(
                gps,
                cum,
                tuning(spacingM = 100.0, windowHalfM = 250.0, headingThresholdDeg = 30.0),
            )
        assertTrue(
            "expected no chevron spanning the corner, got ${placed.map { it.distanceM }}",
            placed.none { it.distanceM in 100.0..300.0 },
        )
        assertTrue("expected chevrons on the straight legs", placed.isNotEmpty())
    }

    @Test
    fun cadence_after_a_nudge_is_measured_from_the_accepted_position() {
        // Reuses the nudge case: the next accepted position is measured from the nudged
        // 125 m position (125 + 100 = 225 nominal), not from the original 150 m grid point
        // (150 + 100 = 250). The next chevron actually lands at 262.5 m, a 137.5 m gap, not
        // an exact spacing on either grid, because the cadence after re-phasing still walks
        // in 100 m steps but the acceptance criteria at each step can nudge the candidate
        // again. Either way it is nowhere near a fixed-grid implementation's 225 m.
        val east = 0.001797
        val gps =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.0, east),
                LatLng(0.001797, east),
            )
        val cum = cumulativeDistancesM(gps)
        val placed =
            placeChevrons(
                gps,
                cum,
                tuning(spacingM = 100.0, windowHalfM = 60.0, headingThresholdDeg = 30.0),
            )
        val nudged = placed.first { it.distanceM > 100.0 }
        val next = placed.firstOrNull { it.distanceM > nudged.distanceM }
        assertTrue("expected a chevron after the nudged one", next != null)
        val gap = (next?.distanceM ?: 0.0) - nudged.distanceM
        assertTrue("expected the next chevron one spacing on, got $gap", gap in 60.0..140.0)
    }

    @Test
    fun gentle_continuous_curve_relies_on_the_post_loop_acceptance() {
        // A route turning 4 degrees every 20 m edge is never straight enough for early
        // acceptance (every candidate's local bearing spread is at least ~24 degrees, the
        // spread of a ~120 m window over a 4 deg/20 m curve) but is straight enough for the
        // 30 degree heading threshold. Every placement below can only come from the
        // post-loop "take the straightest candidate" branch; deleting that branch drops
        // every one of them.
        val gps = curvingRoute(pointCount = 30)
        val cum = cumulativeDistancesM(gps)
        val placed =
            placeChevrons(
                gps,
                cum,
                tuning(spacingM = 100.0, windowHalfM = 60.0, headingThresholdDeg = 30.0),
            )
        assertEquals(
            listOf(12.5, 112.5, 212.5, 312.5, 412.5, 550.0),
            placed.map { it.distanceM },
        )
    }

    @Test
    fun collision_at_one_offset_slides_to_a_clearing_offset_instead_of_dropping_the_step() {
        // A 250 m out-and-back hairpin, 5 m wide at the turn: north 250 m, a 5 m jog east,
        // then south 250 m back down almost on top of the outbound leg. The 100 m cadence
        // places chevrons at 50, 150, 250 on the outbound leg with no collisions. At the
        // 350 m cursor step the offset-0 candidate sits on the inbound leg at 155 m north,
        // only ~7 m from the chevron already placed at 150 m north — inside the 12 m
        // collision radius — but the -0.25 offset candidate at 325 m sits at 180 m north,
        // ~30 m from that same chevron and clear of every other one, so it is placed there
        // instead. The walk then re-phases off 325, landing next at 425.
        val d = 250.0
        val eps = 5.0
        val gps =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(metersToDeg(d), 0.0),
                LatLng(metersToDeg(d), metersToDeg(eps)),
                LatLng(0.0, metersToDeg(eps)),
            )
        val cum = cumulativeDistancesM(gps)
        val placed =
            placeChevrons(
                gps,
                cum,
                tuning(spacingM = 100.0, collisionRadiusM = 12.0),
            )
        assertEquals(listOf(50.0, 150.0, 250.0, 325.0, 425.0), placed.map { it.distanceM })
    }
}
