package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ClimberMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig

/** Emphasis/simplification actually used to render the climb overlay. */
data class EffectiveClimbTuning(
    val skipBands: Int,
    val simplification: ElevationSimplification,
)

/**
 * When [map].syncWithSparkline is true the overlay follows the field sparkline's emphasis and
 * simplification; otherwise it uses its own stored values.
 */
fun resolveClimbTuning(map: ClimberMapConfig, sparkline: SparklineConfig): EffectiveClimbTuning =
    if (map.syncWithSparkline) {
        EffectiveClimbTuning(sparkline.skipBands, sparkline.simplification)
    } else {
        EffectiveClimbTuning(map.skipBands, map.simplification)
    }
