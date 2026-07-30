package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import org.junit.Assert.assertEquals
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
}
