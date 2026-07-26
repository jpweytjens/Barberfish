package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.MARKER_PAD_PX
import com.jpweytjens.barberfish.datatype.shared.buildWarpedXMapper
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.sparklineWindow
import com.jpweytjens.barberfish.datatype.shared.visvalingamWhyatt
import com.jpweytjens.barberfish.extension.SparklineWarp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationStripTest {

    // Precision-1 Google Encoded Polyline for (0m, 100m), (500m, 110m), (1000m, 95m).
    // Confirmed format from Task 5 spike: lat = cumulative distance m, lng = elevation m, divisor =
    // 10.
    // Computed manually: encode [(0,1000),(5000,1100),(10000,950)] as standard polyline deltas.
    private val FIXTURE_POLYLINE = "?o}@owHgEowHjH"

    @Test
    fun decoder_returns_expected_points() {
        val points = decodeElevationPolyline(FIXTURE_POLYLINE)
        assertEquals(3, points.size)
        assertEquals(0f, points[0].first, 1f)
        assertEquals(100f, points[0].second, 1f)
        assertEquals(500f, points[1].first, 1f)
        assertEquals(110f, points[1].second, 1f)
        assertEquals(1000f, points[2].first, 1f)
        assertEquals(95f, points[2].second, 1f)
    }

    @Test
    fun decoder_returns_empty_list_for_blank_input() {
        assertTrue(decodeElevationPolyline("").isEmpty())
    }

    // --- visvalingamWhyatt ---

    @Test
    fun vw_returns_input_unchanged_for_fewer_than_three_points() {
        assertTrue(visvalingamWhyatt(emptyList(), 100f).isEmpty())
        val one = listOf(0f to 0f)
        assertEquals(one, visvalingamWhyatt(one, 100f))
        val two = listOf(0f to 0f, 50f to 10f)
        assertEquals(two, visvalingamWhyatt(two, 100f))
    }

    @Test
    fun vw_returns_input_unchanged_when_threshold_is_zero() {
        val input = listOf(0f to 0f, 20f to 1f, 40f to 0f)
        assertEquals(input, visvalingamWhyatt(input, 0f))
        assertEquals(input, visvalingamWhyatt(input, -5f))
    }

    @Test
    fun vw_collapses_a_straight_climb_to_just_the_endpoints() {
        val climb = listOf(0f to 0f, 50f to 5f, 100f to 10f, 150f to 15f)
        // minAreaM2 must be strictly > 0 — interior triangles are geometrically zero but
        // finite-float noise can still produce tiny non-zero values; 0.01 m² cuts them all.
        val simplified = visvalingamWhyatt(climb, 0.01f)
        assertEquals(2, simplified.size)
        assertEquals(0f to 0f, simplified.first())
        assertEquals(150f to 15f, simplified.last())
    }

    @Test
    fun vw_removes_one_metre_wiggle_at_twenty_metre_spacing() {
        // Triangle area = 0.5 × |20·0 − 40·1| = 20 m² — the rainbow-noise threshold.
        val wiggle = listOf(0f to 0f, 20f to 1f, 40f to 0f)
        val keptAtMild = visvalingamWhyatt(wiggle, 15f)
        assertEquals(3, keptAtMild.size)
        val removedAtMedium = visvalingamWhyatt(wiggle, 25f)
        assertEquals(2, removedAtMedium.size)
        assertEquals(0f to 0f, removedAtMedium.first())
        assertEquals(40f to 0f, removedAtMedium.last())
    }

    @Test
    fun vw_preserves_a_real_two_metre_bump() {
        // Triangle area = 0.5 × |50·0 − 100·2| = 100 m² — well above MILD (25) / MEDIUM (60).
        val bump = listOf(0f to 0f, 50f to 2f, 100f to 0f)
        val simplified = visvalingamWhyatt(bump, 25f)
        assertEquals(3, simplified.size)
        assertEquals(50f to 2f, simplified[1])
    }

    @Test
    fun vw_always_keeps_first_and_last_points() {
        // Ramp with one noisy dip; endpoints must survive regardless of threshold.
        val input = listOf(0f to 0f, 50f to 5f, 100f to 4.9f, 150f to 15f, 200f to 20f)
        val simplified = visvalingamWhyatt(input, 10_000f) // absurdly aggressive
        assertEquals(2, simplified.size)
        assertEquals(0f to 0f, simplified.first())
        assertEquals(200f to 20f, simplified.last())
    }

    // --- sparklineWindow ---

    // Every user-selectable lookahead (km) and warp setting.
    private val lookaheadKmOptions = listOf(5, 10, 20)

    // Narrowest plausible sparkline cell through a full-width one.
    private val cellWidthsPx = listOf(160, 240, 480)

    private val ROUTE_LENGTH_M = 60_000f

    // Route-end dot x for every lookahead × warp × cell width, via the same window and mapper the
    // renderer uses.
    private fun routeEndDotX(
        lookaheadM: Float,
        warp: SparklineWarp,
        widthPx: Int,
        positionM: Float,
    ): Float {
        val (start, end) =
            sparklineWindow(
                firstDist = 0f,
                lastDist = ROUTE_LENGTH_M,
                positionM = positionM,
                lookaheadM = lookaheadM,
                positionFraction = warp.positionFraction,
                widthPx = widthPx,
                logWarpK = warp.k,
            )
        return buildWarpedXMapper(start, end, positionM, lookaheadM, widthPx, warp.k)(positionM)
    }

    @Test
    fun position_dot_clears_the_marker_pad_at_both_route_ends() {
        for (lookaheadKm in lookaheadKmOptions) {
            val lookaheadM = lookaheadKm * 1000f
            for (warp in SparklineWarp.entries) {
                for (positionM in listOf(0f, ROUTE_LENGTH_M)) {
                    for (widthPx in cellWidthsPx) {
                        val dotX = routeEndDotX(lookaheadM, warp, widthPx, positionM)
                        val case =
                            "${lookaheadKm}km ${warp.label} ${widthPx}px at ${positionM}m: $dotX"
                        assertTrue(case, dotX >= MARKER_PAD_PX)
                        assertTrue(case, dotX <= widthPx - MARKER_PAD_PX)
                    }
                }
            }
        }
    }

    @Test
    fun route_start_reserves_no_more_than_the_marker_pad() {
        // The reserve exists only to keep the dot whole, so the empty strip in front of the route
        // must stay within a pixel of the dot's own radius rather than growing to the mid-ride
        // past region (which is an eighth of the strip).
        for (lookaheadKm in lookaheadKmOptions) {
            val lookaheadM = lookaheadKm * 1000f
            for (warp in SparklineWarp.entries) {
                for (widthPx in cellWidthsPx) {
                    val dotX = routeEndDotX(lookaheadM, warp, widthPx, 0f)
                    val case = "${lookaheadKm}km ${warp.label} ${widthPx}px: $dotX"
                    assertTrue(case, dotX <= MARKER_PAD_PX + 4f)
                    val endDotX = routeEndDotX(lookaheadM, warp, widthPx, ROUTE_LENGTH_M)
                    assertTrue(case, endDotX >= widthPx - MARKER_PAD_PX - 4f)
                }
            }
        }
    }

    @Test
    fun window_is_unchanged_mid_route() {
        // Well away from both ends the margin is already satisfied, so the window must stay exactly
        // where it was: `positionFraction` of it behind the rider, the rest ahead.
        val lookaheadM = 10_000f
        val fraction = SparklineWarp.MEDIUM.positionFraction
        val positionM = 30_000f
        val (start, end) =
            sparklineWindow(
                firstDist = 0f,
                lastDist = ROUTE_LENGTH_M,
                positionM = positionM,
                lookaheadM = lookaheadM,
                positionFraction = fraction,
                widthPx = 240,
                logWarpK = SparklineWarp.MEDIUM.k,
            )
        assertEquals(positionM - lookaheadM * fraction, start, 0.01f)
        assertEquals(positionM + lookaheadM * (1f - fraction), end, 0.01f)
        assertEquals(lookaheadM, end - start, 0.01f)
    }

    @Test
    fun window_never_exceeds_the_lookahead_width() {
        // stepM in buildWarpedXMapper divides the window by a step count derived from lookaheadM;
        // a window wider than lookaheadM would silently coarsen the warp lookup.
        for (lookaheadKm in lookaheadKmOptions) {
            val lookaheadM = lookaheadKm * 1000f
            for (warp in SparklineWarp.entries) {
                for (routeLengthM in listOf(400f, 2_000f, ROUTE_LENGTH_M)) {
                    var positionM = 0f
                    while (positionM <= routeLengthM) {
                        val (start, end) =
                            sparklineWindow(
                                firstDist = 0f,
                                lastDist = routeLengthM,
                                positionM = positionM,
                                lookaheadM = lookaheadM,
                                positionFraction = warp.positionFraction,
                                widthPx = 240,
                                logWarpK = warp.k,
                            )
                        assertTrue(
                            "${lookaheadKm}km ${warp.label} on ${routeLengthM}m at ${positionM}m: " +
                                "${end - start}",
                            end - start <= lookaheadM + 0.01f,
                        )
                        assertTrue(end > start)
                        positionM += routeLengthM / 20f
                    }
                }
            }
        }
    }
}
