package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.REFERENCE_ZOOM
import com.jpweytjens.barberfish.datatype.shared.effectiveMinAreaM2
import com.jpweytjens.barberfish.datatype.shared.lineCapTrimM
import com.jpweytjens.barberfish.datatype.shared.metresPerPixel
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradeMapConfigInputs
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import io.hammerhead.karooext.models.OnNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapTuningTest {

    @Test
    fun `synced reads from sparkline, ignoring overlay values`() {
        val map =
            GradeMapConfig(
                syncWithSparkline = true,
                skipBands = 3,
                simplification = ElevationSimplification.NONE,
            )
        val sparkline =
            SparklineConfig(
                skipBands = 1,
                simplification = ElevationSimplification.HEAVY,
            )

        val tuning = resolveGradeMapTuning(map, sparkline, GradePalette.KAROO)

        assertEquals(1, tuning.skipBands)
        assertEquals(ElevationSimplification.HEAVY, tuning.simplification)
    }

    @Test
    fun `independent reads from overlay, ignoring sparkline values`() {
        val map =
            GradeMapConfig(
                syncWithSparkline = false,
                skipBands = 3,
                simplification = ElevationSimplification.NONE,
            )
        val sparkline =
            SparklineConfig(
                skipBands = 1,
                simplification = ElevationSimplification.HEAVY,
            )

        val tuning = resolveGradeMapTuning(map, sparkline, GradePalette.KAROO)

        assertEquals(3, tuning.skipBands)
        assertEquals(ElevationSimplification.NONE, tuning.simplification)
    }

    // The overlay's grade edges follow the same sync switch as its emphasis: reading
    // GradeMapConfig.gradeEdges() directly would give the overlay's own count even while synced.
    // Karoo climb stops are 2/5/8/11/14/20, so a count of 1 is 2% and a count of 3 is 8%.

    @Test
    fun `synced edges come from the sparkline`() {
        val map = GradeMapConfig(syncWithSparkline = true, skipBands = 3)
        val sparkline = SparklineConfig(skipBands = 1, skipBandsDescent = 0)

        val tuning = resolveGradeMapTuning(map, sparkline, GradePalette.KAROO)

        assertEquals(2.0, tuning.climbEdge)
        assertNull(tuning.descentEdge)
    }

    @Test
    fun `independent edges come from the overlay`() {
        val map = GradeMapConfig(syncWithSparkline = false, skipBands = 3)
        val sparkline = SparklineConfig(skipBands = 1, skipBandsDescent = 0)

        val tuning = resolveGradeMapTuning(map, sparkline, GradePalette.KAROO)

        assertEquals(8.0, tuning.climbEdge)
        assertNull(tuning.descentEdge)
    }

    // The GRADE MAP card sets the climb side only. Switching to Independent must not silently
    // widen the descent side to every descent, which is what the overlay's own (absent) descent
    // count resolves to. Barberfish descent stops are -2/-6/-10.

    @Test
    fun `the descent edge survives the switch to independent`() {
        val sparkline = SparklineConfig(skipBands = 1, skipBandsDescent = 3)
        val palette = GradePalette.BARBERFISH

        val synced =
            resolveGradeMapTuning(GradeMapConfig(syncWithSparkline = true), sparkline, palette)
        val independent =
            resolveGradeMapTuning(
                GradeMapConfig(syncWithSparkline = false, skipBands = 3),
                sparkline,
                palette,
            )

        assertEquals(-10.0, synced.descentEdge)
        assertEquals(-10.0, independent.descentEdge)
        // Only the climb side parts company.
        assertEquals(2.0, synced.climbEdge)
        assertEquals(8.0, independent.climbEdge)
    }

    @Test
    fun `an overlay descent edge of its own still wins when independent`() {
        val tuning =
            resolveGradeMapTuning(
                GradeMapConfig(syncWithSparkline = false, descentEdge = -3.0),
                SparklineConfig(skipBandsDescent = 3),
                GradePalette.BARBERFISH,
            )

        assertEquals(-3.0, tuning.descentEdge)
    }

    // BarberfishExtension's map overlay de-dupes rebuilds by comparing GradeMapConfigInputs'
    // signature(), not the config object. It must hash climbEdge/descentEdge or an edge-only
    // change is invisible and the overlay goes stale.
    private fun inputsWithEdges(climbEdge: Double?, descentEdge: Double?) =
        GradeMapConfigInputs(
            enabled = true,
            chevronBlend = 0.5,
            palette = GradePalette.KAROO,
            tuning =
                EffectiveGradeMapTuning(
                    skipBands = 1,
                    simplification = ElevationSimplification.HEAVY,
                    climbEdge = climbEdge,
                    descentEdge = descentEdge,
                ),
            state = OnNavigationState.NavigationState.Idle,
        )

    @Test
    fun `map rebuild signature differs when only climbEdge changes`() {
        val a = inputsWithEdges(climbEdge = 5.0, descentEdge = null)
        val b = inputsWithEdges(climbEdge = 6.0, descentEdge = null)
        assertNotEquals(a.signature(), b.signature())
    }

    @Test
    fun `map rebuild signature differs when only descentEdge changes`() {
        val a = inputsWithEdges(climbEdge = null, descentEdge = -3.0)
        val b = inputsWithEdges(climbEdge = null, descentEdge = -6.0)
        assertNotEquals(a.signature(), b.signature())
    }

    // The same scenario the fix targets: with syncWithSparkline on, only the resolved edge
    // carries a sparkline change through to the signature (the count itself is hashed nowhere).
    @Test
    fun `synced descent-count change reaches the signature via the resolved edge`() {
        val map = GradeMapConfig(syncWithSparkline = true)
        val palette = GradePalette.TURBO

        fun signatureFor(sparkline: SparklineConfig): Any =
            GradeMapConfigInputs(
                    enabled = map.enabled,
                    chevronBlend = map.chevronBlend,
                    palette = palette,
                    tuning = resolveGradeMapTuning(map, sparkline, palette),
                    state = OnNavigationState.NavigationState.Idle,
                )
                .signature()

        val a = signatureFor(SparklineConfig(skipBands = 1, skipBandsDescent = 0))
        val b = signatureFor(SparklineConfig(skipBands = 1, skipBandsDescent = 1))

        assertNotEquals(a, b)
    }

    @Test
    fun `simplification scales with the zoom band`() {
        val base = ElevationSimplification.HEAVY
        assertEquals(base.minAreaM2, effectiveMinAreaM2(base, metresPerPixel = 1.5), 0.01f)
        assertTrue(effectiveMinAreaM2(base, metresPerPixel = 24.0) > base.minAreaM2)
    }

    @Test
    fun `metres per pixel doubles with each zoom step out`() {
        assertEquals(3.08, metresPerPixel(zoom = REFERENCE_ZOOM - 1.0), 0.01)
        assertEquals(1.54, metresPerPixel(zoom = REFERENCE_ZOOM), 0.01)
    }

    @Test
    fun `map rebuild signature differs when only the rejoin path changes`() {
        fun route(rejoin: String?) =
            OnNavigationState.NavigationState.NavigatingRoute(
                routePolyline = "abc",
                routeDistance = 1000.0,
                routeElevationPolyline = null,
                rejoinPolyline = rejoin,
                rejoinDistance = null,
                name = "r",
                reversed = false,
                breadcrumb = false,
                pois = emptyList(),
                climbs = emptyList(),
            )
        fun signatureFor(rejoin: String?) =
            inputsWithEdges(climbEdge = 5.0, descentEdge = null)
                .copy(state = route(rejoin))
                .signature()
        assertNotEquals(signatureFor(null), signatureFor("xyz"))
        assertNotEquals(signatureFor("xyz"), signatureFor("uvw"))
        assertEquals(signatureFor("xyz"), signatureFor("xyz"))
    }

    @Test
    fun lineCapTrim_is_half_the_width_in_ground_metres_at_the_route_latitude() {
        // 18 dp at density 1.875 is 33.75 px; the trim is the half of that, 16.875 px, priced at
        // the midpoint of zoom band 15 (1.10 m/px at 49.5 N, within 2% of the measured law).
        val atRoute = lineCapTrimM(18, 1.875f, lat = 49.5, zoom = 15.96)
        assertEquals(16.875 * 1.097, atRoute, 0.2)
        // The equator, where the rider sits until the first fix, prices 1.54x more metres per
        // pixel at this latitude and would trim that much too far.
        val atEquator = lineCapTrimM(18, 1.875f, lat = 0.0, zoom = 15.96)
        assertEquals(1.54, atEquator / atRoute, 0.01)
        // A wider casing trims proportionally further.
        assertEquals(21.0 / 18.0, lineCapTrimM(21, 1.875f, 49.5, 15.96) / atRoute, 1e-9)
    }
}
