package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.visvalingamWhyatt
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

    // --- visvalingamWhyatt ---

    @Test fun vw_returns_input_unchanged_for_fewer_than_three_points() {
        assertTrue(visvalingamWhyatt(emptyList(), 100f).isEmpty())
        val one = listOf(0f to 0f)
        assertEquals(one, visvalingamWhyatt(one, 100f))
        val two = listOf(0f to 0f, 50f to 10f)
        assertEquals(two, visvalingamWhyatt(two, 100f))
    }

    @Test fun vw_returns_input_unchanged_when_threshold_is_zero() {
        val input = listOf(0f to 0f, 20f to 1f, 40f to 0f)
        assertEquals(input, visvalingamWhyatt(input, 0f))
        assertEquals(input, visvalingamWhyatt(input, -5f))
    }

    @Test fun vw_collapses_a_straight_climb_to_just_the_endpoints() {
        val climb = listOf(0f to 0f, 50f to 5f, 100f to 10f, 150f to 15f)
        // minAreaM2 must be strictly > 0 — interior triangles are geometrically zero but
        // finite-float noise can still produce tiny non-zero values; 0.01 m² cuts them all.
        val simplified = visvalingamWhyatt(climb, 0.01f)
        assertEquals(2, simplified.size)
        assertEquals(0f to 0f, simplified.first())
        assertEquals(150f to 15f, simplified.last())
    }

    @Test fun vw_removes_one_metre_wiggle_at_twenty_metre_spacing() {
        // Triangle area = 0.5 × |20·0 − 40·1| = 20 m² — the rainbow-noise threshold.
        val wiggle = listOf(0f to 0f, 20f to 1f, 40f to 0f)
        val keptAtMild = visvalingamWhyatt(wiggle, 15f)
        assertEquals(3, keptAtMild.size)
        val removedAtMedium = visvalingamWhyatt(wiggle, 25f)
        assertEquals(2, removedAtMedium.size)
        assertEquals(0f to 0f, removedAtMedium.first())
        assertEquals(40f to 0f, removedAtMedium.last())
    }

    @Test fun vw_preserves_a_real_two_metre_bump() {
        // Triangle area = 0.5 × |50·0 − 100·2| = 100 m² — well above MILD (25) / MEDIUM (60).
        val bump = listOf(0f to 0f, 50f to 2f, 100f to 0f)
        val simplified = visvalingamWhyatt(bump, 25f)
        assertEquals(3, simplified.size)
        assertEquals(50f to 2f, simplified[1])
    }

    @Test fun vw_always_keeps_first_and_last_points() {
        // Ramp with one noisy dip; endpoints must survive regardless of threshold.
        val input = listOf(0f to 0f, 50f to 5f, 100f to 4.9f, 150f to 15f, 200f to 20f)
        val simplified = visvalingamWhyatt(input, 10_000f) // absurdly aggressive
        assertEquals(2, simplified.size)
        assertEquals(0f to 0f, simplified.first())
        assertEquals(200f to 20f, simplified.last())
    }
}
