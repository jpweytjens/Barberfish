package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.resolveClimbTuning
import com.jpweytjens.barberfish.extension.ClimberMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ClimbTuningTest {

    @Test
    fun `synced reads from sparkline, ignoring overlay values`() {
        val map =
            ClimberMapConfig(
                syncWithSparkline = true,
                skipBands = 3,
                simplification = ElevationSimplification.NONE,
            )
        val sparkline =
            SparklineConfig(
                skipBands = 1,
                simplification = ElevationSimplification.HEAVY,
            )

        val tuning = resolveClimbTuning(map, sparkline)

        assertEquals(1, tuning.skipBands)
        assertEquals(ElevationSimplification.HEAVY, tuning.simplification)
    }

    @Test
    fun `independent reads from overlay, ignoring sparkline values`() {
        val map =
            ClimberMapConfig(
                syncWithSparkline = false,
                skipBands = 3,
                simplification = ElevationSimplification.NONE,
            )
        val sparkline =
            SparklineConfig(
                skipBands = 1,
                simplification = ElevationSimplification.HEAVY,
            )

        val tuning = resolveClimbTuning(map, sparkline)

        assertEquals(3, tuning.skipBands)
        assertEquals(ElevationSimplification.NONE, tuning.simplification)
    }
}
