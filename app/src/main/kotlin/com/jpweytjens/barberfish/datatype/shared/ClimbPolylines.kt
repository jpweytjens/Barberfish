package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.ElevationRenderConfig
import com.jpweytjens.barberfish.extension.GradePalette

/**
 * A single coloured fill polyline for a route gradient segment. The map controller pairs
 * each spec with a wider black outline polyline at emit time (see ClimbMapController), so
 * this type only carries the fill geometry + colour.
 */
internal data class ClimbPolylineSpec(
    val id: String,        // fill id; outline id is derived as "$id-outline" by the controller
    val encoded: String,
    val colorArgb: Int,
)

/**
 * Builds gradient polyline specs along a route's elevation profile, mirroring the HUD
 * elevation sparkline. The Karoo SDK `Climb` list is intentionally not used — the rider's
 * mental model of "where it gets coloured" must be identical on the sparkline and on the map,
 * and the sparkline is driven purely by the elevation polyline + `ElevationRenderConfig`.
 *
 * Algorithm:
 * 1. Decode the GPS polyline and compute cumulative distance.
 * 2. Decode the elevation polyline and run Visvalingam–Whyatt simplification.
 * 3. Walk adjacent elevation-vertex pairs, computing local grade. Below-threshold segments
 *    become "gap" runs that get skipped entirely. Above-threshold segments are grouped
 *    into runs of consecutive same-colour segments, and each run emits a single polyline
 *    spanning `[runStartM, runEndM]`. Merging same-colour runs eliminates the visible
 *    double-stroke at each elevation-vertex junction and cuts the emission count.
 *
 * Returns an empty list if either polyline is missing. The returned specs are fill-only;
 * the controller adds a black outline polyline with the same encoded geometry per spec
 * at emit time.
 */
internal fun buildClimbPolylineSpecs(
    routePolyline: String,
    routeElevationPolyline: String?,
    palette: GradePalette,
    readable: Boolean,
    renderCfg: ElevationRenderConfig,
): List<ClimbPolylineSpec> {
    if (routePolyline.isBlank() || routeElevationPolyline.isNullOrBlank()) return emptyList()
    val gps = decodeGpsPolyline(routePolyline)
    if (gps.size < 2) return emptyList()
    val cumDist = cumulativeDistancesM(gps)
    val rawElev = decodeElevationPolyline(routeElevationPolyline)
    if (rawElev.isEmpty()) return emptyList()
    val elevPoints = visvalingamWhyatt(rawElev, renderCfg.simplification.minAreaM2)
    val threshold = gradeThreshold(palette, renderCfg.skipBands)

    // Collect same-colour runs, then emit one spec per run.
    data class Run(val startM: Double, var endM: Double, val colorArgb: Int)
    val runs = mutableListOf<Run>()
    elevPoints.windowed(2).forEach { pair ->
        val d0 = pair[0].first.toDouble()
        val d1 = pair[1].first.toDouble()
        if (d1 <= d0) return@forEach
        val e0 = pair[0].second.toDouble()
        val e1 = pair[1].second.toDouble()
        val localGradePct = ((e1 - e0) / (d1 - d0)) * 100.0
        if (localGradePct < threshold) return@forEach
        val color = (gradeColor(localGradePct, palette, readable) ?: return@forEach).toArgb()
        val last = runs.lastOrNull()
        if (last != null && last.colorArgb == color && last.endM == d0) {
            last.endM = d1
        } else {
            runs += Run(d0, d1, color)
        }
    }

    val specs = mutableListOf<ClimbPolylineSpec>()
    runs.forEachIndexed { runIdx, run ->
        val sub = extractSubPolyline(gps, cumDist, run.startM, run.endM)
        if (sub.size < 2) return@forEachIndexed
        specs += ClimbPolylineSpec(
            id = "barberfish-seg-$runIdx",
            encoded = encodeGpsPolyline(sub),
            colorArgb = run.colorArgb,
        )
    }
    return specs
}
