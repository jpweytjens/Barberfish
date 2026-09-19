package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.FlatGrey
import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.datatype.shared.GradeRun
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.buildRouteIndex
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.cutRunsByVisits
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.drawnExtent
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.datatype.shared.mapNeutral
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.trimPieceFrom
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // Chevron spacing tests that only care about run/id/colour behaviour, not the cadence
    // blend itself, pin both spacing bounds to one value so placement is a fixed grid again.
    private val FIXED_CHEVRON_SPACING_M = 60.0

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
        // so their cells merge into one run; the 0% segment is flat, and with the climb side
        // fully on (skipBands = 0) an exact-0% cell takes the flattest band rather than the
        // neutral. Between them sits the 30 m cell straddling the 200 m band boundary: its mean
        // is 6.7%, which is a band of its own.
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
        assertEquals(gradeColor(0.0, GradePalette.KAROO, true)!!.toArgb(), specs[2].colorArgb)
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
        // single run; the cell across their boundary means 6.7%, still above the 2% climb edge,
        // so it takes a band of its own. The flat 0% segment is below the climb edge, so it
        // resolves to the map neutral and still draws: the band tiles the whole route, and
        // emphasis only decides which runs take a colour.
        assertEquals(3, specs.size)
        assertEquals(
            listOf("barberfish-seg-0", "barberfish-seg-1", "barberfish-seg-2"),
            specs.map { it.id },
        )
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
        // Five runs: a climb band, the flattest band on the cell straddling the peak (its chord
        // is exactly 0% and the climb side is fully on), the dip itself in the map neutral
        // (KAROO colours no descent), the shallow band of the cell straddling the dip's end,
        // then the second climb band. Neutral runs draw like any other, so the ids run
        // without gaps.
        assertEquals(5, specs.size)
        assertEquals((0 until 5).map { "barberfish-seg-$it" }, specs.map { it.id })
        assertEquals(gradeColor(0.0, GradePalette.KAROO, true)!!.toArgb(), specs[1].colorArgb)
        assertEquals(FlatGrey.toArgb(), specs[2].colorArgb)
    }

    @Test
    fun simplification_reduces_segment_count() {
        // A 9% climb whose grade alternates every 30 m cell between two clearly separated
        // colour bands (never dipping toward the flat/neutral band), so NONE keeps every
        // alternation as its own run while HEAVY smooths the oscillation into a handful of
        // merged colour runs.
        val noisy = mutableListOf<Pair<Float, Float>>()
        for (d in 0..600 step 30) {
            val e = 100f + d * 0.09f + if ((d / 30) % 2 == 0) 0.9f else -0.9f
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
        assertTrue(rawSpecs.isNotEmpty())
        assertTrue(heavySpecs.isNotEmpty())
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
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
            )
        assertEquals(3, overlay.polylines.size)
        assertTrue(overlay.chevrons.isEmpty())
    }

    @Test
    fun descent_takes_its_band_colour_on_a_two_sided_palette() {
        // Barberfish colours both sides, so the -5% dip takes its own descent band rather
        // than the neutral KAROO gives it. The cell across the summit at 100 m means out flat,
        // so it draws in the map neutral between the climb and the descent.
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
        assertEquals(mapNeutral(GradePalette.BARBERFISH, false).toArgb(), specs[1].colorArgb)
        assertEquals(
            gradeColor(-5.0, GradePalette.BARBERFISH, false)!!.toArgb(),
            specs[2].colorArgb,
        )
    }

    @Test
    fun neutral_runs_draw_in_the_map_neutral_on_barberfish() {
        // At a normal emphasis (climb 2, descent -10) the 0% summit cell and the -5% dip both
        // sit inside the edges. On Barberfish, as on every palette, an unemphasised run draws
        // in the map neutral, so the band tiles the route without gaps in the id sequence.
        val dipPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 110f, // +10% → climb band
                    200f to 105f, // -5%  → inside the descent edge
                    300f to 120f, // +15% → climb band
                )
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.BARBERFISH,
                    readable = false,
                    tuning =
                        EffectiveGradeMapTuning(
                            skipBands = 1,
                            simplification = ElevationSimplification.NONE,
                            climbEdge = 2.0,
                            descentEdge = -10.0,
                        ),
                )
                .polylines
        val neutral = mapNeutral(GradePalette.BARBERFISH, false).toArgb()
        assertEquals(specs.indices.map { "barberfish-seg-$it" }, specs.map { it.id })
        assertTrue(specs.any { it.colorArgb == neutral })
        assertTrue(specs.any { it.colorArgb != neutral })
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
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
            )
        assertTrue(overlay.chevrons.isNotEmpty())
        val expectedYellow = gradeColor(8.0, GradePalette.KAROO, true)!!.toArgb()
        assertEquals(expectedYellow, overlay.chevrons[0].colorArgb)
    }

    @Test
    fun collision_radius_stretches_the_cadence() {
        // A 300 m climb covering the first 300 m of the route, straight along the equator.
        // At a fixed 60 m spacing the uncollided cadence is 30, 90, 150, 210, 270. With a
        // 100 m collision radius: 90 sits within 100 m of the mark already placed at 30, so
        // it is skipped (no retry at a nearby offset); the walk resumes its 60 m stride and
        // 150 is clear (120 m from 30); 210 sits within 100 m of 150 and is skipped the same
        // way; 270 is clear (120 m from 150). The walk lands on 30, 150, 270.
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
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
            )
        val deduped =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longClimb,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
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
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
            )
        val marked =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = longEnough,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
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
    fun piece_casings_tile_the_profiled_extent_trimmed_at_the_route_ends() {
        val full =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
            )
        val trimmed =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                casingCapTrimM = 15.0,
            )
        fun lenM(encoded: String) = cumulativeDistancesM(decodeGpsPolyline(encoded)).last()
        // The fixture's profile covers 300 m of a 3.3 km route: every piece has a casing of its
        // own extent, and together they stop with the fills rather than running to the route end.
        assertTrue(full.polylines.all { it.casingEncoded.isNotEmpty() })
        assertEquals(300.0, full.polylines.sumOf { lenM(it.casingEncoded) }, 1.0)
        // Trimmed, only the first and last piece lose a trim.
        assertEquals(270.0, trimmed.polylines.sumOf { lenM(it.casingEncoded) }, 3.0)
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
        // dip: 10% [0,100], -5% dip [100,200], 15% [200,300], plus the exact-0% cell straddling
        // the peak (flattest band, climb side fully on) and the shallow cell straddling the
        // dip's end: five runs, the dip in the map neutral. The three interior runs keep their
        // full geometry.
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
        assertEquals(5, specs.size)
        assertTrue(specs[0].trimStart)
        assertFalse(specs[0].trimEnd)
        (1..3).forEach { i ->
            assertFalse(specs[i].trimStart)
            assertFalse(specs[i].trimEnd)
        }
        assertFalse(specs[4].trimStart)
        assertTrue(specs[4].trimEnd)
        // Outer runs lose ~15 m at their outer end only; the interior runs lose nothing.
        assertEquals(segLenM(full[0]) - 15.0, segLenM(specs[0]), 3.0)
        (1..3).forEach { i -> assertEquals(segLenM(full[i]), segLenM(specs[i]), 1e-6) }
        assertEquals(segLenM(full[4]) - 15.0, segLenM(specs[4]), 3.0)
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
                    chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                    chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
                )
                .chevrons
        val trimmed =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = elevationPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                    chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                    chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
                    capTrimM = 20.0,
                )
                .chevrons
        assertEquals(none.map { it.id }, trimmed.map { it.id })
        assertEquals(none.map { it.lat to it.lng }, trimmed.map { it.lat to it.lng })
    }

    @Test
    fun id_spans_cover_every_minted_id() {
        // Chevron ids keep their route-wide placement index, so placements dropped for
        // sitting on no run leave gaps and the emitted count undercounts the highest id.
        // The reported spans must cover every minted id — they are what a fresh startMap
        // hides to clear a dead predecessor's symbols.
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
            )
        val segIds = (0 until overlay.segmentIdSpan).map { "barberfish-seg-$it" }
        assertTrue(overlay.polylines.all { it.id in segIds })
        val chevIds = (0 until overlay.chevronIdSpan).map { "barberfish-chev-$it" }
        assertTrue(overlay.chevrons.all { it.id in chevIds })
        // The fixture drops every placement past the 300 m of runs on the 3336 m route.
        assertTrue(overlay.chevronIdSpan > overlay.chevrons.size)
    }

    @Test
    fun chevron_id_span_is_zero_when_chevrons_excluded() {
        val overlay =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                includeChevrons = false,
            )
        assertEquals(0, overlay.chevronIdSpan)
    }

    // Route and elevation polylines whose distance axes deliberately disagree: the elevation
    // axis spans 2% more than the GPS polyline's arclength, standing in for the chord-cutting
    // shortfall a real GPS polyline accumulates across curves. 34 equator points ≈ 36.7 km,
    // so the 2% gap (~367 m) dwarfs the 30 m cell quantisation. The profile climbs at 8% to
    // half the elevation span, then runs flat.
    private fun mismatchedAxes(): Triple<String, String, Double> {
        val route = encodeGpsManually((0..33).map { 0.0 to it * 0.01 })
        val gpsArcM = cumulativeDistancesM(decodeGpsPolyline(route)).last()
        val elevSpanM = gpsArcM * 1.02
        val elevation =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    (elevSpanM / 2).toFloat() to (100.0 + 0.08 * elevSpanM / 2).toFloat(),
                    elevSpanM.toFloat() to (100.0 + 0.08 * elevSpanM / 2).toFloat(),
                )
            )
        return Triple(route, elevation, gpsArcM)
    }

    @Test
    fun run_bounds_scale_to_the_gps_arclength() {
        // The climb ends at half the elevation axis, which is half the route — so the drawn
        // climb run must end at half the GPS arclength, not at elevation-metre elevSpan/2,
        // which sits ~367 m further along the GPS polyline.
        val (route, elevation, gpsArcM) = mismatchedAxes()
        val specs =
            buildGradeMapSpecs(
                    routePolyline = route,
                    routeElevationPolyline = elevation,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                )
                .polylines
        assertEquals(gpsArcM / 2, segLenM(specs[0]), 50.0)
    }

    @Test
    fun chevron_run_matching_uses_the_scaled_bounds() {
        // Chevron placements measure GPS arclength. A placement just past the route's true
        // midpoint sits on the flat run — but compared against unscaled elevation-axis run
        // bounds it would still fall inside the climb run and come out in the climb colour.
        val (route, elevation, gpsArcM) = mismatchedAxes()
        val chevrons =
            buildGradeMapSpecs(
                    routePolyline = route,
                    routeElevationPolyline = elevation,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                    chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                    chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
                )
                .chevrons
        // Distance along the route recovered from longitude: the route runs straight along
        // the equator, so arclength is proportional to lng across the 0.33° span.
        fun distanceM(lng: Double): Double = lng / 0.33 * gpsArcM
        val pastMidpoint = chevrons.filter {
            distanceM(it.lng) in (gpsArcM / 2 + 60.0)..(gpsArcM * 1.02 / 2 - 60.0)
        }
        assertTrue("expected chevrons in the drifted window", pastMidpoint.isNotEmpty())
        val flat = gradeColor(0.0, GradePalette.KAROO, true)!!.toArgb()
        pastMidpoint.forEach { assertEquals(flat, it.colorArgb) }
    }

    @Test
    fun emphasised_runs_produce_polylines() {
        // The band tiles the whole route, unemphasised runs included; what this checks is
        // that the emphasised climb and descent bands themselves produce polylines.
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
        assertTrue("expected polylines for the emphasised route", specs.isNotEmpty())

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
    }

    @Test
    fun band_tiles_the_whole_route() {
        // With a normal emphasis most of the dip profile is unemphasised. The neutral runs
        // still draw, so the polylines together span the profiled 300 m end to end.
        val dipPolyline =
            encodeElevationManually(
                listOf(
                    0f to 100f,
                    100f to 110f,
                    200f to 105f,
                    300f to 120f,
                )
            )
        val specs =
            buildGradeMapSpecs(
                    routePolyline = routePolyline,
                    routeElevationPolyline = dipPolyline,
                    palette = GradePalette.KAROO,
                    readable = true,
                    tuning =
                        GradeMapConfig(climbEdge = 12.0, descentEdge = -20.0)
                            .resolvedFor(GradePalette.KAROO),
                )
                .polylines
        assertEquals(300.0, specs.sumOf { segLenM(it) }, 1.0)
        assertTrue(specs.count { it.colorArgb == FlatGrey.toArgb() } >= 1)
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
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
            )
        val reversed =
            buildGradeMapSpecs(
                routePolyline = routePolyline,
                routeElevationPolyline = elevationPolyline,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                chevronSpacingMaxM = FIXED_CHEVRON_SPACING_M,
                chevronSpacingMinM = FIXED_CHEVRON_SPACING_M,
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

    @Test
    fun steeper_grade_yields_more_chevrons_than_gentle() {
        val gentle =
            buildGradeMapSpecs(
                routePolyline = straightRoute2kmPolyline(),
                routeElevationPolyline = linearElevationPolyline(gradePct = 3.0, lengthM = 2000.0),
                palette = GradePalette.SURGEONFISH,
                readable = false,
                tuning = fullyOnTuning(),
                chevronBlend = 0.0,
                chevronSpacingMaxM = 200.0,
                chevronSpacingMinM = 40.0,
                chevronWindowHalfM = 20.0,
                metresPerPixel = 1.0,
            )
        val steep =
            buildGradeMapSpecs(
                routePolyline = straightRoute2kmPolyline(),
                routeElevationPolyline = linearElevationPolyline(gradePct = 12.0, lengthM = 2000.0),
                palette = GradePalette.SURGEONFISH,
                readable = false,
                tuning = fullyOnTuning(),
                chevronBlend = 0.0,
                chevronSpacingMaxM = 200.0,
                chevronSpacingMinM = 40.0,
                chevronWindowHalfM = 20.0,
                metresPerPixel = 1.0,
            )
        assertTrue(steep.chevrons.size > gentle.chevrons.size)
    }

    @Test
    fun pieces_are_runs_cut_at_visit_boundaries_and_inherit_depth() {
        // Flat out-and-back: one colour run, six visits, so six pieces in route order.
        val route = RouteFixtures.outAndBack(300.0, 100.0)
        val specs =
            buildGradeMapSpecs(
                routePolyline = RouteFixtures.encoded(route),
                routeElevationPolyline = RouteFixtures.flatElevation(600.0),
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                includeChevrons = false,
            )
        val index = buildRouteIndex(RouteFixtures.encoded(route), reversed = false)
        assertEquals(6, specs.polylines.size)
        assertEquals(6, specs.segmentIdSpan)
        assertEquals((0 until 6).toList(), specs.polylines.map { it.visitKey })
        assertEquals(listOf(2, 2, 2, 1, 1, 1), specs.polylines.map { it.depth })
        // Encoding quantises the fixture's coordinates, so compare against the index built from
        // the same encoded route rather than against round metres.
        specs.polylines.forEachIndexed { i, piece ->
            assertEquals(index.visits[i].startM, piece.startM, 1e-6)
            assertEquals(index.visits[i].endM, piece.endM, 1e-6)
        }
        assertEquals((0 until 6).map { "barberfish-seg-$it" }, specs.polylines.map { it.id })
    }

    @Test
    fun a_colour_change_inside_a_visit_makes_separate_pieces() {
        // 600 m straight, 8 per cent for the first 300 m then flat: two runs, one visit.
        val route = RouteFixtures.straight(600.0, 100.0)
        val elevation =
            RouteFixtures.encodeElevation(listOf(0f to 100f, 300f to 124f, 600f to 124f))
        val specs =
            buildGradeMapSpecs(
                routePolyline = RouteFixtures.encoded(route),
                routeElevationPolyline = elevation,
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                includeChevrons = false,
            )
        assertEquals(2, specs.polylines.size)
        assertTrue(specs.polylines.all { it.visitKey == 0 })
        // The run boundary is on the elevation axis and rescaled onto the GPS axis, which the
        // encoding stretched by a fraction of a per cent.
        assertEquals(300.0, specs.polylines[0].endM, 2.0)
    }

    @Test
    fun cutRunsByVisits_intersects_runs_with_visits() {
        // Built from raw points, so visit boundaries sit on exact 100 m multiples.
        val route = RouteFixtures.outAndBack(300.0, 100.0)
        val index = buildRouteIndex(route, cumulativeDistancesM(route))
        val runs = listOf(GradeRun(0.0, 250.0, 1), GradeRun(250.0, 600.0, 2))
        val pieces = cutRunsByVisits(runs, index)
        val expected =
            listOf(
                0.0 to 100.0,
                100.0 to 200.0,
                200.0 to 250.0,
                250.0 to 300.0,
                300.0 to 400.0,
                400.0 to 500.0,
                500.0 to 600.0,
            )
        assertEquals(expected.size, pieces.size)
        expected.forEachIndexed { i, (s, e) ->
            assertEquals(s, pieces[i].startM, 1e-6)
            assertEquals(e, pieces[i].endM, 1e-6)
        }
        assertEquals(listOf(1, 1, 1, 2, 2, 2, 2), pieces.map { it.colorArgb })
        assertEquals(listOf(0, 1, 2, 2, 3, 4, 5), pieces.map { it.visitKey })
    }

    @Test
    fun drawnExtent_trims_only_the_flagged_ends_and_never_past_the_middle() {
        assertEquals(
            20.0 to 100.0,
            drawnExtent(0.0, 100.0, trimStart = true, trimEnd = false, capTrimM = 20.0),
        )
        assertEquals(
            0.0 to 80.0,
            drawnExtent(0.0, 100.0, trimStart = false, trimEnd = true, capTrimM = 20.0),
        )
        assertEquals(
            0.0 to 100.0,
            drawnExtent(0.0, 100.0, trimStart = false, trimEnd = false, capTrimM = 20.0),
        )
        // A 10 m piece trimmed at both ends by 20 m keeps a 1 m stub in the middle.
        val (s, e) = drawnExtent(0.0, 10.0, trimStart = true, trimEnd = true, capTrimM = 20.0)
        assertEquals(4.5, s, 1e-9)
        assertEquals(5.5, e, 1e-9)
    }

    @Test
    fun trimPieceFrom_recuts_fill_and_casing_from_progress() {
        val route = RouteFixtures.straight(600.0, 100.0)
        val index = buildRouteIndex(RouteFixtures.encoded(route), reversed = false)
        val specs =
            buildGradeMapSpecs(
                routePolyline = RouteFixtures.encoded(route),
                routeElevationPolyline = RouteFixtures.flatElevation(600.0),
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                includeChevrons = false,
                capTrimM = 10.0,
                casingCapTrimM = 15.0,
                routeIndex = index,
            )
        val piece = specs.polylines.single()
        val trimmed =
            checkNotNull(
                trimPieceFrom(
                    piece,
                    fromM = 250.0,
                    index = index,
                    capTrimM = 10.0,
                    casingCapTrimM = 15.0,
                )
            )
        fun lenM(encoded: String) = cumulativeDistancesM(decodeGpsPolyline(encoded)).last()
        // The route-end trim still applies; only the start moved to progress.
        assertEquals(index.lengthM - 10.0 - 250.0, lenM(trimmed.encoded), 0.5)
        assertEquals(index.lengthM - 15.0 - 250.0, lenM(trimmed.casingEncoded), 0.5)
        // Identity and layout are untouched: the planner must see an in-place update.
        assertEquals(piece.id, trimmed.id)
        assertEquals(piece.startM, trimmed.startM, 0.0)
        assertEquals(piece.endM, trimmed.endM, 0.0)
        assertEquals(piece.depth, trimmed.depth)
        assertEquals(piece.visitKey, trimmed.visitKey)
    }

    @Test
    fun trimPieceFrom_returns_null_once_progress_is_inside_the_end_trim() {
        val route = RouteFixtures.straight(600.0, 100.0)
        val index = buildRouteIndex(RouteFixtures.encoded(route), reversed = false)
        val specs =
            buildGradeMapSpecs(
                routePolyline = RouteFixtures.encoded(route),
                routeElevationPolyline = RouteFixtures.flatElevation(600.0),
                palette = GradePalette.KAROO,
                readable = true,
                tuning = noneCfg.resolvedFor(GradePalette.KAROO),
                includeChevrons = false,
                capTrimM = 10.0,
                casingCapTrimM = 15.0,
                routeIndex = index,
            )
        val piece = specs.polylines.single()
        // Inside the casing's 15 m trim but outside the fill's 10 m: nothing drawable for the
        // casing, so the piece goes rather than regrowing to full length.
        assertNull(trimPieceFrom(piece, index.lengthM - 12.0, index, 10.0, 15.0))
        assertNotNull(trimPieceFrom(piece, index.lengthM - 20.0, index, 10.0, 15.0))
    }

    // Straight 2 km route east along the equator, for chevron cadence tests that only care
    // about grade magnitude, not curvature or run boundaries.
    private fun straightRoute2kmPolyline(): String {
        val lngPerM = 1.0 / 111_320.0 // degrees longitude per metre at the equator
        return encodeGpsPolyline(listOf(LatLng(0.0, 0.0), LatLng(0.0, 2000.0 * lngPerM)))
    }

    // Two-point elevation polyline climbing at a constant [gradePct] over [lengthM].
    private fun linearElevationPolyline(gradePct: Double, lengthM: Double): String =
        encodeElevationManually(
            listOf(0f to 0f, lengthM.toFloat() to (lengthM * gradePct / 100.0).toFloat())
        )

    // Emphasis fully on (every band, including flat, takes its own colour) and unsimplified,
    // so a chevron cadence test sees every metre of the profile it feeds in. The edges reach
    // across zero (a crossover pair), which gradeBandColor clamps to the flat band's own lo/hi,
    // so every grade colours: climb bands above it, descent bands below it, and the flat band
    // itself in between.
    private fun fullyOnTuning(): EffectiveGradeMapTuning =
        EffectiveGradeMapTuning(
            skipBands = 0,
            simplification = ElevationSimplification.NONE,
            climbEdge = -1000.0,
            descentEdge = 1000.0,
        )

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
