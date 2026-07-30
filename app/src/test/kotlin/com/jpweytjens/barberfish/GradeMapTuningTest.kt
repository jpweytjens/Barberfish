package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
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

    // BarberfishExtension's map overlay de-dupes rebuilds by comparing a file-private
    // GradeMapConfigSignature, not the config object. It must hash climbEdge/descentEdge or an
    // edge-only change is invisible and the overlay goes stale. The class is deliberately
    // file-private (nothing outside the dedup check should construct one), so reflection reaches
    // its constructor here rather than widening that visibility for a test.
    private fun gradeMapConfigSignature(climbEdge: Double?, descentEdge: Double?): Any {
        val ctor =
            Class.forName("com.jpweytjens.barberfish.extension.GradeMapConfigSignature")
                .declaredConstructors
                .single()
        ctor.isAccessible = true
        return ctor.newInstance(
            true, // enabled
            true, // showPolylines
            true, // showChevrons
            GradePalette.KAROO, // palette
            ElevationSimplification.HEAVY, // simplification
            1, // skipBands
            climbEdge,
            descentEdge,
            0, // routeElevationHash
            0, // routePolylineHash
            0, // climbsHash
            false, // reversed
            0, // rejoinBucket
        )
    }

    @Test
    fun `map rebuild signature differs when only climbEdge changes`() {
        val a = gradeMapConfigSignature(climbEdge = 5.0, descentEdge = null)
        val b = gradeMapConfigSignature(climbEdge = 6.0, descentEdge = null)
        assertNotEquals(a, b)
    }

    @Test
    fun `map rebuild signature differs when only descentEdge changes`() {
        val a = gradeMapConfigSignature(climbEdge = null, descentEdge = -3.0)
        val b = gradeMapConfigSignature(climbEdge = null, descentEdge = -6.0)
        assertNotEquals(a, b)
    }
}
