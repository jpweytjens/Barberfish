package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.buildClimbOverlaySpecs
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.extension.ElevationRenderConfig
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

    private val noneCfg = ElevationRenderConfig(ElevationSimplification.NONE, skipBands = 0)

    @Test fun blank_route_polyline_returns_empty() {
        val overlay = buildClimbOverlaySpecs(
            routePolyline = "",
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            renderCfg = noneCfg,
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
            renderCfg = noneCfg,
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
            renderCfg = noneCfg,
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
            renderCfg = noneCfg,
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
            renderCfg = noneCfg,
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
            renderCfg = ElevationRenderConfig(ElevationSimplification.NONE, skipBands = 1),
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
            renderCfg = ElevationRenderConfig(ElevationSimplification.NONE, skipBands = 0),
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
            renderCfg = ElevationRenderConfig(ElevationSimplification.NONE, skipBands = 0),
        ).polylines
        val heavySpecs = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = noisyPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            renderCfg = ElevationRenderConfig(ElevationSimplification.HEAVY, skipBands = 0),
        ).polylines
        assertTrue(
            "expected HEAVY to produce fewer specs than NONE (raw=${rawSpecs.size}, heavy=${heavySpecs.size})",
            heavySpecs.size < rawSpecs.size,
        )
    }

    @Test fun chevrons_are_emitted_per_run_with_fixed_spacing() {
        // The merged_adjacent_same_colour fixture yields two runs: a 200 m yellow run
        // (8% + 12% segments, merged) and a 100 m dark-green flat run. At 60 m spacing,
        // the yellow run gets floor(200/60) = 3 chevrons and the flat run gets
        // floor(100/60) = 1 chevron.
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            renderCfg = noneCfg,
        )
        assertEquals(2, overlay.polylines.size)
        assertEquals(4, overlay.chevrons.size)
        assertEquals("barberfish-chev-0-0", overlay.chevrons[0].id)
        assertEquals("barberfish-chev-0-1", overlay.chevrons[1].id)
        assertEquals("barberfish-chev-0-2", overlay.chevrons[2].id)
        assertEquals("barberfish-chev-1-0", overlay.chevrons[3].id)
    }

    @Test fun chevrons_omitted_when_includeChevrons_false() {
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            renderCfg = noneCfg,
            includeChevrons = false,
        )
        assertEquals(2, overlay.polylines.size)
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test fun short_run_gets_no_chevron_at_default_spacing() {
        // A single 40 m above-threshold segment shorter than DEFAULT_CHEVRON_SPACING_M
        // (60 m) gets a polyline but no chevron — at this zoom level the gradient colour
        // alone is sufficient. Chevrons only appear when the run is at least as long as
        // the spacing.
        val shortPoly = encodeElevationManually(
            listOf(
                0f to 100f,
                40f to 103.2f,    // 8% grade → yellow band
            ),
        )
        val overlay = buildClimbOverlaySpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = shortPoly,
            palette = GradePalette.KAROO,
            readable = true,
            renderCfg = noneCfg,
        )
        assertEquals(1, overlay.polylines.size)
        assertTrue(overlay.chevrons.isEmpty())
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
