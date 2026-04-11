package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.ElevationRenderConfig
import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A single coloured fill polyline for a route gradient segment.
 */
internal data class ClimbPolylineSpec(
    val id: String,
    val encoded: String,
    val colorArgb: Int,
)

/**
 * A single chevron symbol placed along a coloured gradient run. The bearing is the
 * direction the chevron points (derived from two adjacent route vertices ~10 m apart).
 */
internal data class ClimbChevronSpec(
    val id: String,
    val lat: Double,
    val lng: Double,
    val bearingDeg: Float,
)

/** Specs produced by [buildClimbOverlaySpecs]. */
internal data class ClimbOverlaySpecs(
    val polylines: List<ClimbPolylineSpec>,
    val chevrons: List<ClimbChevronSpec>,
)

/** Axis-aligned viewport bounding box in lat/lng. */
internal data class LatLngBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun contains(lat: Double, lng: Double): Boolean =
        lat in minLat..maxLat && lng in minLng..maxLng
}

/** Default chevron spacing when no zoom-adaptive step is supplied. */
internal const val DEFAULT_CHEVRON_SPACING_M = 60.0

/**
 * Builds gradient polyline specs and chevron symbol specs along a route's elevation
 * profile, mirroring the HUD elevation sparkline. The Karoo SDK `Climb` list is
 * intentionally not used — the rider's mental model of "where it gets coloured" must be
 * identical on the sparkline and on the map, and the sparkline is driven purely by the
 * elevation polyline + `ElevationRenderConfig`.
 *
 * Algorithm:
 * 1. Decode the GPS polyline and compute cumulative distance.
 * 2. Decode the elevation polyline and run Visvalingam–Whyatt simplification.
 * 3. Walk adjacent elevation-vertex pairs, computing local grade. Below-threshold segments
 *    get skipped. Above-threshold segments are grouped into runs of consecutive same-colour
 *    segments, and each run emits a single polyline spanning `[runStartM, runEndM]`.
 *    Merging same-colour runs eliminates the visible double-stroke at each elevation-vertex
 *    junction and cuts the emission count.
 * 4. For each run, sample chevron positions every [chevronSpacingM] metres, computing
 *    each chevron's bearing from two route points 10 m apart. Short runs still get at
 *    least one chevron at their midpoint so climbs shorter than the spacing remain marked.
 * 5. If [chevronViewport] is non-null, drop any chevron whose lat/lng falls outside the
 *    viewport bounds. This keeps the emitted symbol count bounded regardless of route
 *    length — we only render what the rider can see.
 *
 * Returns empty lists if either polyline is missing.
 */
internal fun buildClimbOverlaySpecs(
    routePolyline: String,
    routeElevationPolyline: String?,
    palette: GradePalette,
    readable: Boolean,
    renderCfg: ElevationRenderConfig,
    includeChevrons: Boolean = true,
    chevronSpacingM: Double = DEFAULT_CHEVRON_SPACING_M,
    chevronViewport: LatLngBounds? = null,
): ClimbOverlaySpecs {
    if (routePolyline.isBlank() || routeElevationPolyline.isNullOrBlank()) {
        return ClimbOverlaySpecs(emptyList(), emptyList())
    }
    val gps = decodeGpsPolyline(routePolyline)
    if (gps.size < 2) return ClimbOverlaySpecs(emptyList(), emptyList())
    val cumDist = cumulativeDistancesM(gps)
    val rawElev = decodeElevationPolyline(routeElevationPolyline)
    if (rawElev.isEmpty()) return ClimbOverlaySpecs(emptyList(), emptyList())
    val elevPoints = visvalingamWhyatt(rawElev, renderCfg.simplification.minAreaM2)
    val threshold = gradeThreshold(palette, renderCfg.skipBands)

    // Collect same-colour runs, then emit one polyline per run.
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

    val polylines = mutableListOf<ClimbPolylineSpec>()
    val chevrons = mutableListOf<ClimbChevronSpec>()
    runs.forEachIndexed { runIdx, run ->
        val sub = extractSubPolyline(gps, cumDist, run.startM, run.endM)
        if (sub.size < 2) return@forEachIndexed
        polylines += ClimbPolylineSpec(
            id = "barberfish-seg-$runIdx",
            encoded = encodeGpsPolyline(sub),
            colorArgb = run.colorArgb,
        )
        if (includeChevrons) {
            chevrons += chevronsForRun(runIdx, run.startM, run.endM, gps, cumDist, chevronSpacingM)
        }
    }
    val filteredChevrons = if (chevronViewport != null) {
        chevrons.filter { chevronViewport.contains(it.lat, it.lng) }
    } else {
        chevrons
    }
    return ClimbOverlaySpecs(polylines, filteredChevrons)
}

private fun chevronsForRun(
    runIdx: Int,
    startM: Double,
    endM: Double,
    gps: List<LatLng>,
    cumDist: DoubleArray,
    spacingM: Double,
): List<ClimbChevronSpec> {
    val lengthM = endM - startM
    if (lengthM <= 0.0 || spacingM <= 0.0) return emptyList()
    val total = cumDist.last()
    // Sample count: floor(length / spacing). Runs shorter than the step get no
    // chevron — at wide zoom the gradient colour alone is sufficient.
    val count = (lengthM / spacingM).toInt()
    if (count == 0) return emptyList()
    val specs = ArrayList<ClimbChevronSpec>(count)
    for (i in 0 until count) {
        // Place chevrons at the centre of each equal-length sub-span so the first and last
        // sit off the run endpoints (avoids stacking at run boundaries).
        val t = (i + 0.5) / count
        val d = (startM + lengthM * t).coerceIn(0.0, total)
        val here = interpolateAt(gps, cumDist, d)
        // Bearing: point 10 m further along (or 10 m backwards if the run ends first).
        val lookaheadD = (d + 10.0).coerceAtMost(total)
        val aheadD = if (lookaheadD > d) lookaheadD else (d - 10.0).coerceAtLeast(0.0)
        val ahead = interpolateAt(gps, cumDist, aheadD)
        val bearing = if (aheadD >= d) {
            bearingDeg(here, ahead)
        } else {
            // Flip 180° if we had to look backwards to keep the chevron pointing forward.
            (bearingDeg(ahead, here) + 180f) % 360f
        }
        specs += ClimbChevronSpec(
            id = "barberfish-chev-$runIdx-$i",
            lat = here.lat,
            lng = here.lng,
            bearingDeg = bearing,
        )
    }
    return specs
}

/**
 * Initial bearing in degrees from [from] to [to], measured clockwise from North
 * (0 = N, 90 = E, 180 = S, 270 = W). Standard spherical forward-azimuth formula.
 */
private fun bearingDeg(from: LatLng, to: LatLng): Float {
    val lat1 = from.lat * PI / 180.0
    val lat2 = to.lat * PI / 180.0
    val dLon = (to.lng - from.lng) * PI / 180.0
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val deg = atan2(y, x) * 180.0 / PI
    return ((deg + 360.0) % 360.0).toFloat()
}
