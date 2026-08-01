package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.FlatGrey
import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
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
            )
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
            )
        )

    private val noneCfg =
        GradeMapConfig(
            enabled = true,
            simplification = ElevationSimplification.NONE,
            skipBands = 0,
        )

    // buildGradeMapSpecs takes an already-resolved tuning, so these tests resolve their config
    // through the same function the live callers use. Sync is turned off first: with it on the
    // overlay would follow the field sparkline, and it is the map config the test configures.
    private fun GradeMapConfig.resolvedFor(palette: GradePalette): EffectiveGradeMapTuning =
        resolveGradeMapTuning(copy(syncWithSparkline = false), SparklineConfig(), palette)

    @Test
    fun blank_route_polyline_returns_empty() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = "",
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
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
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
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
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        assertTrue(overlay.polylines.isEmpty())
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test
    fun merges_adjacent_same_colour_segments() {
        // Segments in the KAROO palette: 8% and 10% both fall in the "salmon" band (8–10.9%),
        // so their cells merge into one run; the 0% segment is flat, neither a climb nor a
        // descent, so it takes the neutral. Between them sits the 30 m cell straddling the
        // 200 m band boundary: its mean is 6.7%, which is a band of its own.
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .polylines
        assertEquals(3, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals("barberfish-seg-1", specs[1].id)
        assertEquals("barberfish-seg-2", specs[2].id)
        val yellow = gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb()
        assertEquals(yellow, specs[0].colorArgb)
        assertEquals(gradeColor(6.7, GradePalette.KAROO, true)!!.toArgb(), specs[1].colorArgb)
        assertEquals(FlatGrey.toArgb(), specs[2].colorArgb)
    }

    @Test
    fun different_colour_adjacent_segments_do_not_merge() {
        // Segments at 8% (yellow band) and 16% (orange band) are adjacent but different colours,
        // so they stay apart, with the cell straddling their 100 m boundary taking the 13.3%
        // band that lies between them.
        val twoBandsPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 108f, // 8% → yellow
                    200f to 124f, // 16% → orange
                )
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = twoBandsPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .polylines
        assertEquals(3, specs.size)
        assertEquals(
            gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb(),
            specs[0].colorArgb,
        )
        assertEquals(
            gradeColor(13.3, GradePalette.KAROO, true)!!.toArgb(),
            specs[1].colorArgb,
        )
        assertEquals(
            gradeColor(16.0, GradePalette.KAROO, true)!!.toArgb(),
            specs[2].colorArgb,
        )
    }

    @Test
    fun below_the_climb_edge_takes_the_neutral() {
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning =
                        GradeMapConfig(
                                enabled = true,
                                simplification = ElevationSimplification.NONE,
                                skipBands = 1,
                            )
                            .resolvedFor(GradePalette.KAROO),
                )
                .polylines
        // The two climbing segments (8% and 10%) are both in the salmon band and merge into a
        // single run; the flat 0% segment is below the 2% climb edge, so it keeps the neutral
        // instead of dropping out of the overlay. The cell across their boundary means 6.7%,
        // still above the 2% edge, so it takes a band rather than the neutral.
        assertEquals(3, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals(gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb(), specs[0].colorArgb)
        assertEquals(gradeColor(6.7, GradePalette.KAROO, true)!!.toArgb(), specs[1].colorArgb)
        assertEquals(FlatGrey.toArgb(), specs[2].colorArgb)
    }

    @Test
    fun dip_segment_takes_the_neutral_sparkline_parity() {
        // Elevation goes up, down, up → middle segment has a negative local grade.
        val dipPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 110f, // +10 m in 100 m = 10% grade
                    200f to 105f, // -5 m in 100 m = -5% grade (KAROO has no descent bands)
                    300f to 120f, // +15 m in 100 m = 15% grade
                )
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning =
                        GradeMapConfig(
                                enabled = true,
                                simplification = ElevationSimplification.NONE,
                                skipBands = 0,
                            )
                            .resolvedFor(GradePalette.KAROO),
                )
                .polylines
        // Four runs: a climb band, the neutral over the dip (KAROO colours no descent), the
        // shallow band of the cell straddling the dip's end, then the second climb band. IDs
        // are contiguous run indices — not the original VW vertex indices.
        assertEquals(4, specs.size)
        assertEquals("barberfish-seg-0", specs[0].id)
        assertEquals("barberfish-seg-1", specs[1].id)
        assertEquals("barberfish-seg-2", specs[2].id)
        assertEquals("barberfish-seg-3", specs[3].id)
        assertEquals(FlatGrey.toArgb(), specs[1].colorArgb)
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
                    tuning =
                        GradeMapConfig(
                                enabled = true,
                                simplification = ElevationSimplification.NONE,
                                skipBands = 0,
                            )
                            .resolvedFor(GradePalette.KAROO),
                )
                .polylines
        val heavySpecs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = noisyPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning =
                        GradeMapConfig(
                                enabled = true,
                                simplification = ElevationSimplification.HEAVY,
                                skipBands = 0,
                            )
                            .resolvedFor(GradePalette.KAROO),
                )
                .polylines
        assertTrue(
            "expected HEAVY to produce fewer specs than NONE (raw=${rawSpecs.size}, heavy=${heavySpecs.size})",
            heavySpecs.size < rawSpecs.size,
        )
    }

    @Test
    fun chevrons_come_from_one_route_wide_cadence() {
        // Three runs covering the profile's first 300 m. At 60 m spacing the cadence is
        // 30, 90, 150, 210, 270 and continues past the runs to the end of the 3336 m route.
        // The first five placements fall inside the runs, so ids 0 to 4 are emitted and
        // every later placement is dropped for sitting on no run.
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        assertEquals(3, overlay.polylines.size)
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
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                includeChevrons = false,
            )
        assertEquals(3, overlay.polylines.size)
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
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val filtered =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
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
                )
            )
        val climbPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    400f to 116f, // 4% climb over the full 400 m — falls in the KAROO yellow band
                )
            )
        val unfiltered =
            buildGradeMapSpecs(
                routePolyline = bendyPolyline,
                routeElevationPolyline = climbPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val filtered =
            buildGradeMapSpecs(
                routePolyline = bendyPolyline,
                routeElevationPolyline = climbPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
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
            filtered.chevrons.isNotEmpty(),
        )
    }

    @Test
    fun descent_takes_its_band_colour_on_a_two_sided_palette() {
        // Barberfish colours both sides, so the -5% dip takes its own descent band rather
        // than the neutral KAROO gives it. The cell across the summit at 100 m means out
        // level, so the neutral sits between the climb and the descent.
        val dipPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 110f, // +10% → climb band
                    200f to 105f, // -5% → descent band
                )
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.BARBERFISH,
                    readable = false,
                    tuning = noneCfg.resolvedFor(GradePalette.BARBERFISH),
                )
                .polylines
        assertEquals(3, specs.size)
        assertEquals(FlatGrey.toArgb(), specs[1].colorArgb)
        assertEquals(
            gradeColor(-5.0, GradePalette.BARBERFISH, false)!!.toArgb(),
            specs[2].colorArgb,
        )
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
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        assertTrue(overlay.chevrons.isNotEmpty())
        val expectedYellow = gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb()
        assertEquals(expectedYellow, overlay.chevrons[0].colorArgb)
    }

    @Test
    fun collision_radius_stretches_the_cadence() {
        // A 300 m climb covering the first 300 m of the route, straight along the equator.
        // At 60 m spacing the uncollided cadence is 30, 90, 150, 210, 270. With a 100 m
        // collision radius: 90 collides with 30 (60 m apart, every offset still within 100 m
        // of it), so the walk skips to 150 (120 m from 30, clear); 210 collides with 150 the
        // same way, so the walk skips to 270 (120 m from 150, clear). The walk lands on
        // 30, 150, 270.
        val longClimb =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    300f to 124f, // 8% over 300 m → KAROO yellow band
                )
            )
        val noDedup =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longClimb,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val deduped =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longClimb,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                chevronMinSpacingM = 100.0,
            )
        assertEquals(5, noDedup.chevrons.size)
        assertEquals(3, deduped.chevrons.size)
        assertEquals(
            listOf("barberfish-chev-0", "barberfish-chev-1", "barberfish-chev-2"),
            deduped.chevrons.map { it.id },
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
                )
            )
        val longEnough =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    60f to 104.8f, // 8% grade → KAROO yellow band
                )
            )
        val bare =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = tooShort,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val marked =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longEnough,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
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

    @Test
    fun cap_trim_flags_mark_the_route_ends() {
        // Default fixture: salmon, a transition cell and neutral tile the coloured extent.
        // First run owns the start end, last owns the finish end, the middle owns neither.
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .polylines
        assertEquals(3, specs.size)
        assertTrue(specs[0].trimStart)
        assertFalse(specs[0].trimEnd)
        assertFalse(specs[1].trimStart)
        assertFalse(specs[1].trimEnd)
        assertFalse(specs[2].trimStart)
        assertTrue(specs[2].trimEnd)
    }

    @Test
    fun cap_trim_shortens_outer_ends_only() {
        val full =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .polylines
        val trimmed =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                    capTrimM = 20.0,
                )
                .polylines
        // First run: only the chain-start end is pulled in by ~20 m.
        assertEquals(segLenM(full[0]) - 20.0, segLenM(trimmed[0]), 2.0)
        // Last run: only the chain-end is pulled in by ~20 m.
        assertEquals(segLenM(full.last()) - 20.0, segLenM(trimmed.last()), 2.0)
        // Interior junction is untouched: trimmed[0] still ends where trimmed[1] starts.
        val end0 = decodeGpsPolyline(trimmed[0].encoded).last()
        val start1 = decodeGpsPolyline(trimmed[1].encoded).first()
        assertEquals(end0.lat, start1.lat, 1e-6)
        assertEquals(end0.lng, start1.lng, 1e-6)
    }

    @Test
    fun cap_trim_leaves_an_interior_run_untouched() {
        // dip: 10% [0,100], neutral -5% dip [100,200], 15% [200,300], plus the shallow cell
        // straddling the dip's end → four runs that abut, so the two middle ones are interior
        // at both ends and keep their full geometry.
        val dipPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 110f,
                    200f to 105f,
                    300f to 120f,
                )
            )
        val full =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .polylines
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                    capTrimM = 15.0,
                )
                .polylines
        assertEquals(4, specs.size)
        assertTrue(specs[0].trimStart)
        assertFalse(specs[0].trimEnd)
        assertFalse(specs[1].trimStart)
        assertFalse(specs[1].trimEnd)
        assertFalse(specs[2].trimStart)
        assertFalse(specs[2].trimEnd)
        assertFalse(specs[3].trimStart)
        assertTrue(specs[3].trimEnd)
        // Outer runs lose ~15 m at their outer end only; the interior runs lose nothing.
        assertEquals(segLenM(full[0]) - 15.0, segLenM(specs[0]), 3.0)
        assertEquals(segLenM(full[1]), segLenM(specs[1]), 1e-6)
        assertEquals(segLenM(full[2]), segLenM(specs[2]), 1e-6)
        assertEquals(segLenM(full[3]) - 15.0, segLenM(specs[3]), 3.0)
    }

    @Test
    fun cap_trim_does_not_move_chevrons() {
        val none =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .chevrons
        val trimmed =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                    capTrimM = 20.0,
                )
                .chevrons
        assertEquals(none.map { it.id }, trimmed.map { it.id })
        assertEquals(none.map { it.lat to it.lng }, trimmed.map { it.lat to it.lng })
    }

    @Test
    fun map_specs_cover_every_metre_of_the_route() {
        val specs =
            buildGradeMapSpecs(
                    routePolyline = TranquiloFixture.routePolyline,
                    routeElevationPolyline = TranquiloFixture.elevationPolyline,
                    palette = GradePalette.BARBERFISH,
                    readable = false,
                    tuning =
                        GradeMapConfig(climbEdge = 2.0, descentEdge = -2.0)
                            .resolvedFor(GradePalette.BARBERFISH),
                )
                .polylines
        assertTrue("expected polylines for the whole route", specs.isNotEmpty())

        // Every band below the descent edge, i.e. the ones only a descent can reach.
        val descentArgbs =
            gradeBands(GradePalette.BARBERFISH, readable = false)
                .filter { band -> band.hi?.let { it <= -2.0 } == true }
                .map { it.color.toArgb() }
                .toSet()
        assertTrue(
            "descent bands must produce polylines",
            specs.any { it.colorArgb in descentArgbs },
        )

        // No gap between consecutive runs: each starts exactly where the previous ended.
        specs.zipWithNext().forEachIndexed { i, (a, b) ->
            val end = decodeGpsPolyline(a.encoded).last()
            val start = decodeGpsPolyline(b.encoded).first()
            assertEquals("run $i to ${i + 1} lat gap", end.lat, start.lat, 1e-9)
            assertEquals("run $i to ${i + 1} lng gap", end.lng, start.lng, 1e-9)
        }

        // ...and together the runs span the route end to end.
        val route = decodeGpsPolyline(TranquiloFixture.routePolyline)
        val first = decodeGpsPolyline(specs.first().encoded).first()
        val last = decodeGpsPolyline(specs.last().encoded).last()
        assertEquals("runs must start at the route start", route.first().lat, first.lat, 1e-5)
        assertEquals("runs must start at the route start", route.first().lng, first.lng, 1e-5)
        assertEquals("runs must end at the route end", route.last().lat, last.lat, 1e-5)
        assertEquals("runs must end at the route end", route.last().lng, last.lng, 1e-5)
        assertEquals(
            "runs must cover the route length",
            TranquiloFixture.routeLengthM,
            specs.sumOf { segLenM(it) },
            TranquiloFixture.routeLengthM * 0.01,
        )
    }

    @Test
    fun reversed_places_runs_at_mirrored_end() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                reversed = true,
            )
        assertTrue(overlay.polylines.isNotEmpty())
        val lngs = overlay.polylines.flatMap { decodeGpsPolyline(it.encoded) }.map { it.lng }
        assertTrue(
            "reversed runs should sit at the lng 0.03 end, got ${lngs.minOrNull()}..${lngs.maxOrNull()}",
            lngs.all { it > 0.027 },
        )
    }

    @Test
    fun forward_places_runs_at_route_start() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        assertTrue(overlay.polylines.isNotEmpty())
        val lngs = overlay.polylines.flatMap { decodeGpsPolyline(it.encoded) }.map { it.lng }
        assertTrue(
            "forward runs should sit at the lng 0.0 end, got ${lngs.minOrNull()}..${lngs.maxOrNull()}",
            lngs.all { it < 0.003 },
        )
    }

    @Test
    fun reversed_flips_chevron_bearings() {
        val forward =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val reversed =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                reversed = true,
            )
        assertTrue(forward.chevrons.isNotEmpty())
        assertTrue(reversed.chevrons.isNotEmpty())
        forward.chevrons.forEach { assertEquals(90.0f, it.bearingDeg, 1.0f) }
        reversed.chevrons.forEach { assertEquals(270.0f, it.bearingDeg, 1.0f) }
    }

    @Test
    fun reversed_preserves_grade_colors() {
        val forward =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val reversed =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                reversed = true,
            )
        assertEquals(
            forward.polylines.map { it.colorArgb },
            reversed.polylines.map { it.colorArgb },
        )
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
