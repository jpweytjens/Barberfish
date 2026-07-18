package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig

/** Emphasis/simplification actually used to render the climb overlay. */
data class EffectiveGradeMapTuning(
    val skipBands: Int,
    val simplification: ElevationSimplification,
)

/**
 * When [map].syncWithSparkline is true the overlay follows the field sparkline's emphasis and
 * simplification; otherwise it uses its own stored values.
 */
fun resolveGradeMapTuning(map: GradeMapConfig, sparkline: SparklineConfig): EffectiveGradeMapTuning =
    if (map.syncWithSparkline) {
        EffectiveGradeMapTuning(sparkline.skipBands, sparkline.simplification)
    } else {
        EffectiveGradeMapTuning(map.skipBands, map.simplification)
    }
