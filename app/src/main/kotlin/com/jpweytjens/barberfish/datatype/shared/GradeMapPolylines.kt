package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.GradePalette

/**
 * A single coloured fill polyline for a route gradient segment.
 */
internal data class GradeMapPolylineSpec(
    val id: String,
    val encoded: String,
    val colorArgb: Int,
    // True when this end is the route's start or end. Renderers trim only these ends to
    // cancel the round cap overhang; interior junctions keep their cap overlap so no gap
    // opens to the native line.
    val trimStart: Boolean = false,
    val trimEnd: Boolean = false,
)

/**
 * A single chevron symbol placed along the route. Placement is route-wide, then filtered
 * onto the coloured gradient run it lands on. The bearing is the direction the chevron
 * points, taken as a chord across the bearing window (about 24 m on device); 10 m survives
 * only as the fallback when no window is configured.
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
 * per-consumer simplification and grade edges. [tuning] is the already-resolved tuning:
 * callers hand it the output of `resolveGradeMapTuning`, so the sparkline-sync question is
 * settled before we are called and can never be answered differently here.
 *
 * Algorithm:
 * 1. Decode the GPS polyline and compute cumulative distance.
 * 2. Decode the elevation polyline and run Visvalingam–Whyatt simplification.
 * 3. Walk adjacent elevation-vertex pairs, computing local grade. Every segment takes a
 *    colour: its grade band's when the grade is past that side's edge, the flat grey when
 *    it is not. Consecutive same-colour segments are grouped into runs, each emitting a
 *    single polyline spanning `[runStartM, runEndM]`. The runs tile the route, so our
 *    overlay covers Karoo's own route line everywhere, in both directions of travel.
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
    tuning: EffectiveGradeMapTuning,
    includeChevrons: Boolean = true,
    chevronSpacingM: Double = DEFAULT_CHEVRON_SPACING_M,
    chevronWindowHalfM: Double = 0.0,
    chevronHeadingThresholdDeg: Double = 0.0,
    chevronMinSpacingM: Double = 0.0,
    chevronViewport: LatLngBounds? = null,
    capTrimM: Double = 0.0,
    reversed: Boolean = false,
): GradeMapSpecs {
    if (routePolyline.isBlank() || routeElevationPolyline.isNullOrBlank()) {
        return GradeMapSpecs(emptyList(), emptyList())
    }
    val decoded = decodeGpsPolyline(routePolyline)
    val gps = if (reversed) decoded.asReversed() else decoded
    if (gps.size < 2) return GradeMapSpecs(emptyList(), emptyList())
    val cumDist = cumulativeDistancesM(gps)
    val rawElev = decodeElevationPolyline(routeElevationPolyline)
    if (rawElev.isEmpty()) return GradeMapSpecs(emptyList(), emptyList())
    val elevPoints = visvalingamWhyatt(rawElev, tuning.simplification.minAreaM2)

    // Collect same-colour runs, then emit one polyline per run. Every segment gets a colour,
    // so the runs tile the route and no gap can open onto the line underneath.
    data class Run(val startM: Double, var endM: Double, val colorArgb: Int)
    val runs = mutableListOf<Run>()
    elevPoints.windowed(2).forEach { pair ->
        val d0 = pair[0].first.toDouble()
        val d1 = pair[1].first.toDouble()
        if (d1 <= d0) return@forEach
        val e0 = pair[0].second.toDouble()
        val e1 = pair[1].second.toDouble()
        val localGradePct = ((e1 - e0) / (d1 - d0)) * 100.0
        val color = gradeBandColor(
            grade = localGradePct,
            palette = palette,
            climbEdge = tuning.climbEdge,
            descentEdge = tuning.descentEdge,
            neutral = FlatGrey,
            readable = readable,
        ).toArgb()
        val last = runs.lastOrNull()
        if (last != null && last.colorArgb == color && last.endM == d0) {
            last.endM = d1
        } else {
            runs += Run(d0, d1, color)
        }
    }

    val polylines = mutableListOf<GradeMapPolylineSpec>()
    runs.forEachIndexed { runIdx, run ->
        // Runs tile the route, so the only ends that overhang the coloured extent via the
        // renderer's round line-cap are the route's own two: only those are pulled in by
        // capTrimM, and the cap then lands on the true endpoint. Interior junctions stay
        // full (cap overlap, no gap).
        val atRouteStart = runIdx == 0
        val atRouteEnd = runIdx == runs.lastIndex
        // Cap each end's trim so the two never cross: the 0.5 m buffer keeps drawEnd >
        // drawStart even when both ends of a lone run trim to the maximum.
        val maxTrim = ((run.endM - run.startM) * 0.5 - 0.5).coerceAtLeast(0.0)
        val drawStart = run.startM + (if (atRouteStart) capTrimM else 0.0).coerceAtMost(maxTrim)
        val drawEnd = run.endM - (if (atRouteEnd) capTrimM else 0.0).coerceAtMost(maxTrim)
        val sub = extractSubPolyline(gps, cumDist, drawStart, drawEnd)
        if (sub.size >= 2) {
            polylines += GradeMapPolylineSpec(
                id = "barberfish-seg-$runIdx",
                encoded = encodeGpsPolyline(sub),
                colorArgb = run.colorArgb,
                trimStart = atRouteStart,
                trimEnd = atRouteEnd,
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
