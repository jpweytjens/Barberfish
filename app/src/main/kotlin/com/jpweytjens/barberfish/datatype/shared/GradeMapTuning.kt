package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig

/** Emphasis/simplification/edges actually used to render the climb overlay. */
data class EffectiveGradeMapTuning(
    val skipBands: Int,
    val simplification: ElevationSimplification,
    val climbEdge: Double?,
    val descentEdge: Double?,
)

/**
 * When [map].syncWithSparkline is true the overlay follows the field sparkline's emphasis,
 * simplification and grade edges; otherwise it uses its own stored values. The edges resolve
 * against [palette] because a stored config may still carry the legacy band-skip count.
 */
fun resolveGradeMapTuning(
    map: GradeMapConfig,
    sparkline: SparklineConfig,
    palette: GradePalette,
): EffectiveGradeMapTuning =
    if (map.syncWithSparkline) {
        val (climbEdge, descentEdge) = sparkline.gradeEdges(palette)
        EffectiveGradeMapTuning(
            sparkline.skipBands,
            sparkline.simplification,
            climbEdge,
            descentEdge,
        )
    } else {
        val (climbEdge, descentEdge) = map.gradeEdges(palette)
        EffectiveGradeMapTuning(map.skipBands, map.simplification, climbEdge, descentEdge)
    }
