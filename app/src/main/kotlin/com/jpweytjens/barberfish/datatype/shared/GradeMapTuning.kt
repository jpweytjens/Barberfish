package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import kotlin.math.pow

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
 *
 * The descent edge is the exception: the GRADE MAP card has no descent control, so an unsynced
 * overlay keeps following the field sparkline's descent edge rather than dropping to an edge of 0
 * and colouring every descent. A stored [GradeMapConfig.descentEdge] still wins once something
 * writes one.
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
        val (climbEdge, _) = map.gradeEdges(palette)
        val (_, sparklineDescentEdge) = sparkline.gradeEdges(palette)
        EffectiveGradeMapTuning(
            map.skipBands,
            map.simplification,
            climbEdge,
            map.descentEdge ?: sparklineDescentEdge,
        )
    }

// Metres per screen pixel measured at REFERENCE_ZOOM, three independent scale-bar anchors
// agreeing within 0.008 zoom; the doubling law below verified across zoom 12.13-15.13 with
// max deviation 0.55%.
internal const val REFERENCE_ZOOM = 14.98
internal const val REFERENCE_M_PER_PX = 1.54

/** Metres per screen pixel at [zoom], anchored on the measured reference. */
internal fun metresPerPixel(zoom: Double): Double =
    REFERENCE_M_PER_PX * 2.0.pow(REFERENCE_ZOOM - zoom)

/**
 * Visvalingam area threshold for the map at the current zoom. Area is metres of distance times
 * metres of elevation, and only the distance axis scales with zoom, so the threshold scales
 * linearly with [metresPerPixel] rather than quadratically.
 */
internal fun effectiveMinAreaM2(base: ElevationSimplification, metresPerPixel: Double): Float =
    (base.minAreaM2 * maxOf(1.0, metresPerPixel / REFERENCE_M_PER_PX)).toFloat()
