package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
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
    // count resolves to. Barberfish descent stops are -2/-6/-12.

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

        assertEquals(-12.0, synced.descentEdge)
        assertEquals(-12.0, independent.descentEdge)
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
            showPolylines = true,
            showChevrons = true,
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
                    showPolylines = map.showPolylines,
                    showChevrons = map.showChevrons,
                    palette = palette,
                    tuning = resolveGradeMapTuning(map, sparkline, palette),
                    state = OnNavigationState.NavigationState.Idle,
                )
                .signature()

        val a = signatureFor(SparklineConfig(skipBands = 1, skipBandsDescent = 0))
        val b = signatureFor(SparklineConfig(skipBands = 1, skipBandsDescent = 1))

        assertNotEquals(a, b)
    }
}
