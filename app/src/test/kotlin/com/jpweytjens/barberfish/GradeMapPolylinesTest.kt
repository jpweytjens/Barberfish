package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.datatype.shared.LemonYellow
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapPolylinesTest {

    // 4 points along the equator, spaced 0.01° of longitude apart.
    // At the equator, 0.01° of longitude ≈ 1112 m, so total route length ≈ 3336 m —
    // plenty of room for a 300 m elevation polyline covering the first third.
    private val routePolyline =
        encodeGpsManually(
            listOf(
                0.0 to 0.0,
                0.0 to 0.01,
                0.0 to 0.02,
                0.0 to 0.03,
            ),
        )

    // Elevation polyline: 4 points at 0, 100, 200, 300 m with elevations 100, 108, 118, 118.
    // Yields three segments with grades 8%, 10%, 0%.
    private val elevationPolyline =
        encodeElevationManually(
            listOf(
                0f to 100f,
                100f to 108f,
                200f to 118f,
                300f to 118f,
            ),
        )

    private val noneCfg =
        GradeMapConfig(
            enabled = true,
            simplification = ElevationSimplification.NONE,
            skipBands = 0
        )

    @Test
    fun blank_route_polyline_returns_empty() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = "",
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test
    fun missing_elevation_polyline_returns_empty() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = null,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test
    fun blank_elevation_polyline_returns_empty() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = "",
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test
    fun merges_adjacent_same_colour_segments() {
        // Segments in the KAROO palette: 8% and 10% both fall in the "salmon" band (8–10.9%),
        // so they merge into one run; the 0% segment is in the dark-green band (the lowest),
        // a different colour, so it becomes its own run.
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    cfg = noneCfg,
                )
                .polylines
        assertEquals(2, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals("barberfish-seg-1", specs[1].id)
        val yellow = gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb()
        val flat = gradeColor(0.0, GradePalette.KAROO, true)!!.toArgb()
        assertEquals(yellow, specs[0].colorArgb)
        assertEquals(flat, specs[1].colorArgb)
    }

    @Test
    fun different_colour_adjacent_segments_do_not_merge() {
        // Segments at 8% (yellow band) and 16% (orange band) are adjacent but different colours,
        // so they stay as two runs.
        val twoBandsPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 108f, // 8% → yellow
                    200f to 124f, // 16% → orange
                ),
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = twoBandsPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    cfg = noneCfg,
                )
                .polylines
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

    @Test
    fun skipBands_one_suppresses_flat_segment() {
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    cfg =
                        GradeMapConfig(
                            enabled = true,
                            simplification = ElevationSimplification.NONE,
                            skipBands = 1
                        ),
                )
                .polylines
        // Flat 0% segment drops to below threshold; the two climbing segments (8% and 10%)
        // are both in the salmon band and merge into a single run.
        assertEquals(1, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals(gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb(), specs[0].colorArgb)
    }

    @Test
    fun dip_segment_is_skipped_sparkline_parity() {
        // Elevation goes up, down, up → middle segment has a negative local grade.
        val dipPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 110f, // +10 m in 100 m = 10% grade
                    200f to 105f, // -5 m in 100 m = -5% grade (skipped)
                    300f to 120f, // +15 m in 100 m = 15% grade
                ),
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    cfg =
                        GradeMapConfig(
                            enabled = true,
                            simplification = ElevationSimplification.NONE,
                            skipBands = 0
                        ),
                )
                .polylines
        // Two runs, one per above-threshold segment (non-adjacent, different colours).
        // IDs are contiguous run indices — not the original VW vertex indices.
        assertEquals(2, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals("barberfish-seg-1", specs[1].id)
    }

    @Test
    fun simplification_reduces_segment_count() {
        // Noisy elevation polyline with ±1 m wiggles over a 500 m climb.
        val noisy = mutableListOf<Pair<Float, Float>>()
        for (d in 0..500 step 20) {
            val e = 100f + d * 0.08f + if ((d / 20) % 2 == 0) 1f else -1f
            noisy += d.toFloat() to e
        }
        val noisyPolyline = encodeElevationManually(noisy)
        val rawSpecs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = noisyPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    cfg =
                        GradeMapConfig(
                            enabled = true,
                            simplification = ElevationSimplification.NONE,
                            skipBands = 0
                        ),
                )
                .polylines
        val heavySpecs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = noisyPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    cfg =
                        GradeMapConfig(
                            enabled = true,
                            simplification = ElevationSimplification.HEAVY,
                            skipBands = 0
                        ),
                )
                .polylines
        assertTrue(
            "expected HEAVY to produce fewer specs than NONE (raw=${rawSpecs.size}, heavy=${heavySpecs.size})",
            heavySpecs.size < rawSpecs.size,
        )
    }

    @Test
    fun chevrons_come_from_one_route_wide_cadence() {
        // Two runs: a 200 m salmon run and a 100 m flat run. At 60 m spacing the cadence is
        // 30, 90, 150, 210, 270 and continues past the runs to the end of the 3336 m route.
        // The first five placements fall inside the two runs, so ids 0 to 4 are emitted and
        // every later placement is dropped for sitting on no run.
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        assertEquals(2, overlay.polylines.size)
        assertEquals(5, overlay.chevrons.size)
        assertEquals("barberfish-chev-0", overlay.chevrons[0].id)
        assertEquals("barberfish-chev-2", overlay.chevrons[2].id)
        assertEquals("barberfish-chev-4", overlay.chevrons[4].id)
    }

    @Test
    fun chevrons_omitted_when_includeChevrons_false() {
        val overlay =
            buildGradeMapSpecs(
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

    @Test
    fun straight_route_keeps_all_chevrons_with_curvature_filter() {
        // The default route is 4 points along the equator — bearing is constant 90° (east).
        // With the curvature filter enabled, every spacing interval emits a chevron because
        // the local bearing spread is 0°.
        val unfiltered =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        val filtered =
            buildGradeMapSpecs(
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

    @Test
    fun tight_bend_suppresses_chevrons_near_the_corner() {
        // L-shaped route: 200 m east, then 200 m north — a single 90° turn at the corner.
        // Climb covers the full path. With a window wide enough to contain both edges
        // at the corner, the bearing spread is 90° → corner-spanning chevrons are dropped.
        val lEast = 0.001813 // ~200 m east at equator (1° longitude ≈ 111_320 m)
        val lNorth = 0.001797 // ~200 m north (1° latitude ≈ 111_320 m)
        val bendyPolyline =
            encodeGpsManually(
                listOf(
                    0.0 to 0.0,
                    0.0 to lEast,
                    lNorth to lEast,
                ),
            )
        val climbPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    400f to 116f, // 4% climb over the full 400 m — falls in the KAROO yellow band
                ),
            )
        val unfiltered =
            buildGradeMapSpecs(
                routePolyline = bendyPolyline,
                routeElevationPolyline = climbPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        val filtered =
            buildGradeMapSpecs(
                routePolyline = bendyPolyline,
                routeElevationPolyline = climbPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
                // 40 m window, close to the native ~28 m at zoom 15: only the positions whose
                // neighbourhood spans both legs (the 90° corner) see a 90° spread and drop.
                // Positions along either straight leg keep their chevrons.
                chevronWindowHalfM = 40.0,
                chevronHeadingThresholdDeg = 30.0,
            )
        assertTrue(
            "expected fewer chevrons with curvature filter (unfiltered=${unfiltered.chevrons.size}, filtered=${filtered.chevrons.size})",
            filtered.chevrons.size < unfiltered.chevrons.size,
        )
        assertTrue(
            "corner suppression should not wipe the whole run",
            filtered.chevrons.isNotEmpty()
        )
    }

    @Test
    fun below_threshold_segment_inside_climb_gets_yellow_filler() {
        // skipBands=1 → KAROO climb threshold is 2.0%. A 1% segment is below it.
        val cfg =
            GradeMapConfig(
                enabled = true,
                simplification = ElevationSimplification.NONE,
                skipBands = 1,
            )
        val poly =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 101f, // 1% — below the 2.0% threshold
                    200f to 111f, // 10% — above → KAROO salmon band
                ),
            )
        // No climb ranges: the gentle 3% segment is skipped entirely.
        val plain =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = poly,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = cfg,
            )
        assertEquals(1, plain.polylines.size)

        // With a climb spanning the route, the 3% segment becomes a yellow filler run so
        // the overlay covers the whole climb.
        val withClimb =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = poly,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = cfg,
                climbRanges = listOf(0.0 to 220.0),
            )
        assertEquals(2, withClimb.polylines.size)
        assertEquals(LemonYellow.toArgb(), withClimb.polylines[0].colorArgb)
    }

    @Test
    fun chevron_carries_run_color() {
        // The default fixture's first run is salmon (8% + 10% in the KAROO salmon band).
        val overlay =
            buildGradeMapSpecs(
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

    @Test
    fun collision_radius_stretches_the_cadence() {
        // A 300 m climb covering the first 300 m of the route. At 60 m spacing the cadence
        // is 30, 90, 150, 210, 270. With a 100 m collision radius, 90 and 150 are inside
        // 100 m of the chevron at 30 and every one of their offsets collides too, so the
        // walk skips to 150 + 60 = 210 before it can place again.
        val longClimb =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    300f to 124f, // 8% over 300 m → KAROO yellow band
                ),
            )
        val noDedup =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longClimb,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        val deduped =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longClimb,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
                chevronMinSpacingM = 100.0,
            )
        assertEquals(5, noDedup.chevrons.size)
        assertTrue(
            "expected the collision radius to thin the cadence, got ${deduped.chevrons.size}",
            deduped.chevrons.size < noDedup.chevrons.size,
        )
    }

    @Test
    fun short_run_gets_a_chevron_only_when_a_cadence_position_lands_in_it() {
        // At 60 m spacing the first cadence position is 30 m. A 25 m climb ends before it,
        // so the run is drawn with no chevron. A 60 m climb contains it, so it gets one.
        val tooShort =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    25f to 102f, // 8% grade → KAROO yellow band
                ),
            )
        val longEnough =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    60f to 104.8f, // 8% grade → KAROO yellow band
                ),
            )
        val bare =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = tooShort,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        val marked =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longEnough,
                palette = GradePalette.KAROO,
                readable = true,
                cfg = noneCfg,
            )
        assertEquals(1, bare.polylines.size)
        assertTrue(bare.chevrons.isEmpty())
        assertEquals(1, marked.polylines.size)
        assertEquals(1, marked.chevrons.size)
        assertEquals("barberfish-chev-0", marked.chevrons[0].id)
    }

    private fun segLenM(spec: GradeMapPolylineSpec): Double =
        decodeGpsPolyline(spec.encoded).let {
            if (it.size < 2) 0.0 else cumulativeDistancesM(it).last()
        }

    @Test fun cap_trim_flags_mark_chain_outer_ends() {
        // Default fixture: salmon [0,200] and flat [200,300] are adjacent → one chain.
        // First run owns the chain start, second owns the chain end.
        val specs = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        ).polylines
        assertEquals(2, specs.size)
        assertTrue(specs[0].trimStart); assertFalse(specs[0].trimEnd)
        assertFalse(specs[1].trimStart); assertTrue(specs[1].trimEnd)
    }

    @Test fun cap_trim_shortens_outer_ends_only() {
        val full = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        ).polylines
        val trimmed = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
            capTrimM = 20.0,
        ).polylines
        // First run: only the chain-start end is pulled in by ~20 m.
        assertEquals(segLenM(full[0]) - 20.0, segLenM(trimmed[0]), 2.0)
        // Last run: only the chain-end is pulled in by ~20 m.
        assertEquals(segLenM(full[1]) - 20.0, segLenM(trimmed[1]), 2.0)
        // Interior junction is untouched: trimmed[0] still ends where trimmed[1] starts.
        val end0 = decodeGpsPolyline(trimmed[0].encoded).last()
        val start1 = decodeGpsPolyline(trimmed[1].encoded).first()
        assertEquals(end0.lat, start1.lat, 1e-6)
        assertEquals(end0.lng, start1.lng, 1e-6)
    }

    @Test fun cap_trim_treats_gap_separated_runs_as_separate_chains() {
        // dip: 10% [0,100], skipped -5% dip, 15% [200,300] → two non-adjacent chains,
        // so each run is trimmed at BOTH ends.
        val dipPolyline = encodeElevationManually(
            listOf(
                0f to 100f,
                100f to 110f,
                200f to 105f,
                300f to 120f,
            ),
        )
        val full = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = dipPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        ).polylines
        val specs = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = dipPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
            capTrimM = 15.0,
        ).polylines
        assertEquals(2, specs.size)
        assertTrue(specs[0].trimStart && specs[0].trimEnd)
        assertTrue(specs[1].trimStart && specs[1].trimEnd)
        // Both ends trimmed → each isolated run loses ~2 × 15 m of geometry.
        assertEquals(segLenM(full[0]) - 30.0, segLenM(specs[0]), 3.0)
        assertEquals(segLenM(full[1]) - 30.0, segLenM(specs[1]), 3.0)
    }

    @Test fun cap_trim_does_not_move_chevrons() {
        val none = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
        ).chevrons
        val trimmed = buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            cfg = noneCfg,
            capTrimM = 20.0,
        ).chevrons
        assertEquals(none.map { it.id }, trimmed.map { it.id })
        assertEquals(none.map { it.lat to it.lng }, trimmed.map { it.lat to it.lng })
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
