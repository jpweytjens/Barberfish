package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.buildClimbOverlaySpecs
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.extension.ClimberMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbPolylinesTest {

    // 4 points along the equator, spaced 0.01° of longitude apart.
    // At the equator, 0.01° of longitude ≈ 1112 m, so total route length ≈ 3336 m —
    // plenty of room for a 300 m elevation polyline covering the first third.
    private val routePolyline = encodeGpsManually(
        listOf(
            0.0 to 0.0,
            0.0 to 0.01,
            0.0 to 0.02,
            0.0 to 0.03,
        ),
    )

    // Elevation polyline: 4 points at 0, 100, 200, 300 m with elevations 100, 108, 120, 120.
    // Yields three segments with grades 8%, 12%, 0%.
    private val elevationPolyline = encodeElevationManually(
        listOf(
            0f to 100f,
            100f to 108f,
            200f to 120f,
            300f to 120f,
        ),
    )

    private val noneCfg = ClimberMapConfig(enabled = true, simplification = ElevationSimplification.NONE, skipBands = 0)

    @Test fun blank_route_polyline_returns_empty() {
        val overlay = buildClimbOverlaySpecs(
            routePolyline = "",
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test fun missing_elevation_polyline_returns_empty() {
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = null,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test fun blank_elevation_polyline_returns_empty() {
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = "",
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test fun merges_adjacent_same_colour_segments() {
        // Segments in the KAROO palette: 8% and 12% both fall in the "yellow" band (7.6–12.5%),
        // so they merge into one run; the 0% segment is in the dark-green band (the lowest),
        // a different colour, so it becomes its own run.
        val specs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        ).polylines
        assertEquals(2, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals("barberfish-seg-1", specs[1].id)
        val yellow = gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb()
        val flat = gradeColor(0.0, GradePalette.KAROO, true)!!.toArgb()
        assertEquals(yellow, specs[0].colorArgb)
        assertEquals(flat, specs[1].colorArgb)
    }

    @Test fun different_colour_adjacent_segments_do_not_merge() {
        // Segments at 8% (yellow band) and 16% (orange band) are adjacent but different colours,
        // so they stay as two runs.
        val twoBandsPolyline = encodeElevationManually(
            listOf(
                0f to 100f,
                100f to 108f,   // 8% → yellow
                200f to 124f,   // 16% → orange
            ),
        )
        val specs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = twoBandsPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        ).polylines
        assertEquals(2, specs.size)
        assertEquals(
            gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb(),
            specs[0].colorArgb,
        )
        assertEquals(
            gradeColor(16.0, GradePalette.KAROO, true)!!.toArgb(),
            specs[1].colorArgb,
        )
    }

    @Test fun skipBands_one_suppresses_flat_segment() {
        val specs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = ClimberMapConfig(enabled = true, simplification = ElevationSimplification.NONE, skipBands = 1),
        ).polylines
        // Flat 0% segment drops to below threshold; the two climbing segments (8% and 12%)
        // are both in the yellow band and merge into a single run.
        assertEquals(1, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals(gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb(), specs[0].colorArgb)
    }

    @Test fun dip_segment_is_skipped_sparkline_parity() {
        // Elevation goes up, down, up → middle segment has a negative local grade.
        val dipPolyline = encodeElevationManually(
            listOf(
                0f to 100f,
                100f to 110f,    // +10 m in 100 m = 10% grade
                200f to 105f,    // -5 m in 100 m = -5% grade (skipped)
                300f to 120f,    // +15 m in 100 m = 15% grade
            ),
        )
        val specs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = dipPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = ClimberMapConfig(enabled = true, simplification = ElevationSimplification.NONE, skipBands = 0),
        ).polylines
        // Two runs, one per above-threshold segment (non-adjacent, different colours).
        // IDs are contiguous run indices — not the original VW vertex indices.
        assertEquals(2, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals("barberfish-seg-1", specs[1].id)
    }

    @Test fun simplification_reduces_segment_count() {
        // Noisy elevation polyline with ±1 m wiggles over a 500 m climb.
        val noisy = mutableListOf<Pair<Float, Float>>()
        for (d in 0..500 step 20) {
            val e = 100f + d * 0.08f + if ((d / 20) % 2 == 0) 1f else -1f
            noisy += d.toFloat() to e
        }
        val noisyPolyline = encodeElevationManually(noisy)
        val rawSpecs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = noisyPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = ClimberMapConfig(enabled = true, simplification = ElevationSimplification.NONE, skipBands = 0),
        ).polylines
        val heavySpecs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = noisyPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = ClimberMapConfig(enabled = true, simplification = ElevationSimplification.HEAVY, skipBands = 0),
        ).polylines
        assertTrue(
            "expected HEAVY to produce fewer specs than NONE (raw=${rawSpecs.size}, heavy=${heavySpecs.size})",
            heavySpecs.size < rawSpecs.size,
        )
    }

    @Test fun chevrons_sit_on_a_shared_route_distance_grid() {
        // Two runs: a 200 m yellow run and a 100 m flat run. At 60 m spacing the grid is
        // phase + k·60 with phase = 30 (half-spacing), measured from the route start:
        // 30/90/150 fall in the yellow run, 210/270 in the flat run. Both runs draw from
        // the same global grid — they are not sampled run-relative.
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        assertEquals(2, overlay.polylines.size)
        assertEquals(5, overlay.chevrons.size)
        assertEquals("barberfish-chev-0-0", overlay.chevrons[0].id)
        assertEquals("barberfish-chev-0-2", overlay.chevrons[2].id)
        assertEquals("barberfish-chev-1-0", overlay.chevrons[3].id)
        assertEquals("barberfish-chev-1-1", overlay.chevrons[4].id)
    }

    @Test fun chevrons_omitted_when_includeChevrons_false() {
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
            includeChevrons = false,
        )
        assertEquals(2, overlay.polylines.size)
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test fun straight_route_keeps_all_chevrons_with_curvature_filter() {
        // The default route is 4 points along the equator — bearing is constant 90° (east).
        // With the curvature filter enabled, every spacing interval emits a chevron because
        // the local bearing spread is 0°.
        val unfiltered = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        val filtered = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
            chevronWindowHalfM = 40.0,
            chevronHeadingThresholdDeg = 30.0,
        )
        assertEquals(unfiltered.chevrons.size, filtered.chevrons.size)
    }

    @Test fun tight_bend_suppresses_chevrons_near_the_corner() {
        // L-shaped route: 200 m east, then 200 m north — a single 90° turn at the corner.
        // Climb covers the full path. With a window wide enough to contain both edges
        // at the corner, the bearing spread is 90° → corner-spanning chevrons are dropped.
        val lEast = 0.001813  // ~200 m east at equator (1° longitude ≈ 111_320 m)
        val lNorth = 0.001797 // ~200 m north (1° latitude ≈ 111_320 m)
        val bendyPolyline = encodeGpsManually(
            listOf(
                0.0 to 0.0,
                0.0 to lEast,
                lNorth to lEast,
            ),
        )
        val climbPolyline = encodeElevationManually(
            listOf(
                0f to 100f,
                400f to 116f,    // 4% climb over the full 400 m — falls in the KAROO yellow band
            ),
        )
        val unfiltered = buildClimbOverlaySpecs(
            routePolyline = bendyPolyline,
            routeElevationPolyline = climbPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        val filtered = buildClimbOverlaySpecs(
            routePolyline = bendyPolyline,
            routeElevationPolyline = climbPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
            // 250 m window: grid points whose neighbourhood spans both legs (the 90° corner)
            // see a 90° spread and drop; points past the corner keep their chevrons.
            chevronWindowHalfM = 250.0,
            chevronHeadingThresholdDeg = 30.0,
        )
        assertTrue(
            "expected fewer chevrons with curvature filter (unfiltered=${unfiltered.chevrons.size}, filtered=${filtered.chevrons.size})",
            filtered.chevrons.size < unfiltered.chevrons.size,
        )
        assertTrue("corner suppression should not wipe the whole run", filtered.chevrons.isNotEmpty())
    }

    @Test fun chevron_carries_run_color() {
        // The default fixture's first run is yellow (8% + 12% in the KAROO yellow band).
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        assertTrue(overlay.chevrons.isNotEmpty())
        val expectedYellow = gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb()
        assertEquals(expectedYellow, overlay.chevrons[0].colorArgb)
    }

    @Test fun short_run_gets_exactly_one_chevron() {
        // A 25 m climb is shorter than the grid's half-spacing phase (30 m at 60 m
        // spacing), so no grid point lands inside it. The per-segment guarantee still
        // places a single chevron at the run midpoint so no coloured segment is bare.
        val shortPoly = encodeElevationManually(
            listOf(
                0f to 100f,
                25f to 102f,    // 8% grade → KAROO yellow band
            ),
        )
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = shortPoly,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        )
        assertEquals(1, overlay.polylines.size)
        assertEquals(1, overlay.chevrons.size)
        assertEquals("barberfish-chev-0-0", overlay.chevrons[0].id)
    }

    // --- inline polyline encoders (test-only) -----------------------------------

    // Precision-5 Google polyline encoder.
    private fun encodeGpsManually(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for ((lat, lng) in points) {
            val l = Math.round(lat * 1e5)
            val g = Math.round(lng * 1e5)
            encodeSigned(l - prevLat, sb)
            encodeSigned(g - prevLng, sb)
            prevLat = l
            prevLng = g
        }
        return sb.toString()
    }

    // Precision-1 elevation polyline encoder (lat = distance in 0.1 m, lng = elevation in 0.1 m).
    private fun encodeElevationManually(points: List<Pair<Float, Float>>): String {
        val sb = StringBuilder()
        var prevDist = 0L
        var prevElev = 0L
        for ((d, e) in points) {
            val dInt = Math.round(d * 10.0).toLong()
            val eInt = Math.round(e * 10.0).toLong()
            encodeSigned(dInt - prevDist, sb)
            encodeSigned(eInt - prevElev, sb)
            prevDist = dInt
            prevElev = eInt
        }
        return sb.toString()
    }

    private fun encodeSigned(v: Long, sb: StringBuilder) {
        var value = if (v < 0) (v shl 1).inv() else (v shl 1)
        while (value >= 0x20) {
            sb.append(((0x20 or (value and 0x1f).toInt()) + 63).toChar())
            value = value shr 5
        }
        sb.append((value.toInt() + 63).toChar())
    }
}
