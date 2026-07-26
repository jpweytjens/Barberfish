package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette

/**
 * A single coloured fill polyline for a route gradient segment.
 */
internal data class GradeMapPolylineSpec(
    val id: String,
    val encoded: String,
    val colorArgb: Int,
    // True when this end is the outer end of a contiguous coloured chain (no adjacent
    // run beyond it). Renderers trim only these ends to cancel the round cap overhang;
    // interior junctions keep their cap overlap so no gap opens to the native line.
    val trimStart: Boolean = false,
    val trimEnd: Boolean = false,
)

/**
 * A single chevron symbol placed along a coloured gradient run. The bearing is the
 * direction the chevron points (derived from two adjacent route vertices ~10 m apart).
 * [colorArgb] is the gradient-band colour of the polyline run the chevron sits on.
 */
internal data class ClimbChevronSpec(
    val id: String,
    val lat: Double,
    val lng: Double,
    val bearingDeg: Float,
    val colorArgb: Int,
)

/** Specs produced by [buildGradeMapSpecs]. */
internal data class GradeMapSpecs(
    val polylines: List<GradeMapPolylineSpec>,
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
 * intentionally not used — the rider's mental model of "where it gets coloured" must
 * match the sparkline, and the sparkline is driven purely by the elevation polyline +
 * per-consumer simplification/skipBands.
 *
 * Algorithm:
 * 1. Decode the GPS polyline and compute cumulative distance.
 * 2. Decode the elevation polyline and run Visvalingam–Whyatt simplification.
 * 3. Walk adjacent elevation-vertex pairs, computing local grade. Above-threshold segments
 *    take a grade colour; below-threshold segments that fall inside a [climbRanges] entry
 *    take the native route yellow so our overlay fully covers Karoo's blue `CLIMB_LINE`;
 *    all other below-threshold segments are skipped (the native route line shows there).
 *    Consecutive same-colour segments are grouped into runs, each emitting a single
 *    polyline spanning `[runStartM, runEndM]`.
 * 4. Place chevrons along the whole route with [placeChevrons], then colour each placement
 *    with the run containing it and drop the placements that sit on no run. Placement is a
 *    property of the route, not of the runs: a run shorter than the spacing carries a
 *    chevron only when a cadence position happens to fall inside it, so short runs are
 *    marked by their colour alone.
 * 5. If [chevronViewport] is non-null, drop any chevron whose lat/lng falls outside the
 *    viewport bounds. This keeps the emitted symbol count bounded regardless of route
 *    length — we only render what the rider can see.
 *
 * Returns empty lists if either polyline is missing.
 */
internal fun buildGradeMapSpecs(
    routePolyline: String,
    routeElevationPolyline: String?,
    palette: GradePalette,
    readable: Boolean,
    cfg: GradeMapConfig,
    climbRanges: List<Pair<Double, Double>> = emptyList(),
    includeChevrons: Boolean = true,
    chevronSpacingM: Double = DEFAULT_CHEVRON_SPACING_M,
    chevronWindowHalfM: Double = 0.0,
    chevronHeadingThresholdDeg: Double = 0.0,
    chevronMinSpacingM: Double = 0.0,
    chevronViewport: LatLngBounds? = null,
    capTrimM: Double = 0.0,
): GradeMapSpecs {
    if (routePolyline.isBlank() || routeElevationPolyline.isNullOrBlank()) {
        return GradeMapSpecs(emptyList(), emptyList())
    }
    val gps = decodeGpsPolyline(routePolyline)
    if (gps.size < 2) return GradeMapSpecs(emptyList(), emptyList())
    val cumDist = cumulativeDistancesM(gps)
    val rawElev = decodeElevationPolyline(routeElevationPolyline)
    if (rawElev.isEmpty()) return GradeMapSpecs(emptyList(), emptyList())
    val elevPoints = visvalingamWhyatt(rawElev, cfg.simplification.minAreaM2)
    val threshold = gradeFillRange(palette, skipBandsClimb = cfg.skipBands).posMin
        ?: return GradeMapSpecs(emptyList(), emptyList())

    // Collect same-colour runs, then emit one polyline per run. A below-threshold segment
    // inside a Karoo climb range is kept as a route-yellow run so our overlay covers the
    // whole climb — the native CLIMB_LINE (blue) can never show through a gap; below-
    // threshold segments outside any climb are skipped and the native route line shows.
    val fillerArgb = LemonYellow.toArgb()
    data class Run(val startM: Double, var endM: Double, val colorArgb: Int)
    val runs = mutableListOf<Run>()
    elevPoints.windowed(2).forEach { pair ->
        val d0 = pair[0].first.toDouble()
        val d1 = pair[1].first.toDouble()
        if (d1 <= d0) return@forEach
        val e0 = pair[0].second.toDouble()
        val e1 = pair[1].second.toDouble()
        val localGradePct = ((e1 - e0) / (d1 - d0)) * 100.0
        val color = when {
            localGradePct >= threshold ->
                gradeColor(localGradePct, palette, readable)?.toArgb() ?: fillerArgb
            climbRanges.any { (s, e) -> d0 < e && d1 > s } -> fillerArgb
            else -> return@forEach
        }
        val last = runs.lastOrNull()
        if (last != null && last.colorArgb == color && last.endM == d0) {
            last.endM = d1
        } else {
            runs += Run(d0, d1, color)
        }
    }

    val polylines = mutableListOf<GradeMapPolylineSpec>()
    runs.forEachIndexed { runIdx, run ->
        // A contiguous chain is a maximal run sequence with no distance gap between
        // neighbours. Only the chain's outer ends overhang the true climb extent via the
        // renderer's round line-cap, so only those are pulled in by capTrimM; the cap then
        // lands on the true endpoint. Interior junctions stay full (cap overlap, no gap).
        val chainStart = runIdx == 0 || runs[runIdx - 1].endM != run.startM
        val chainEnd = runIdx == runs.lastIndex || runs[runIdx + 1].startM != run.endM
        // Cap each end's trim so the two never cross: the 0.5 m buffer keeps drawEnd >
        // drawStart even when both ends of an isolated run trim to the maximum.
        val maxTrim = ((run.endM - run.startM) * 0.5 - 0.5).coerceAtLeast(0.0)
        val drawStart = run.startM + (if (chainStart) capTrimM else 0.0).coerceAtMost(maxTrim)
        val drawEnd = run.endM - (if (chainEnd) capTrimM else 0.0).coerceAtMost(maxTrim)
        val sub = extractSubPolyline(gps, cumDist, drawStart, drawEnd)
        if (sub.size >= 2) {
            polylines += GradeMapPolylineSpec(
                id = "barberfish-seg-$runIdx",
                encoded = encodeGpsPolyline(sub),
                colorArgb = run.colorArgb,
                trimStart = chainStart,
                trimEnd = chainEnd,
            )
        }
    }
    // Cap-trim shifts where the polylines are drawn, never where chevrons sit, so placement
    // runs against the untrimmed run bounds.
    val chevrons = if (includeChevrons) {
        val tuning = ChevronTuning(
            spacingM = chevronSpacingM,
            windowHalfM = chevronWindowHalfM,
            collisionRadiusM = chevronMinSpacingM,
            headingThresholdDeg = chevronHeadingThresholdDeg,
        )
        placeChevrons(gps, cumDist, tuning).mapIndexedNotNull { idx, placement ->
            val run = runs.firstOrNull {
                placement.distanceM >= it.startM && placement.distanceM < it.endM
            } ?: return@mapIndexedNotNull null
            ClimbChevronSpec(
                // Indexed over every placement on the route, so an id stays put when a
                // neighbouring run changes colour or the run list is re-cut.
                id = "barberfish-chev-$idx",
                lat = placement.lat,
                lng = placement.lng,
                bearingDeg = placement.bearingDeg,
                colorArgb = run.colorArgb,
            )
        }
    } else {
        emptyList()
    }
    val filteredChevrons = if (chevronViewport != null) {
        chevrons.filter { chevronViewport.contains(it.lat, it.lng) }
    } else {
        chevrons
    }
    return GradeMapSpecs(polylines, filteredChevrons)
}
