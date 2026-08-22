package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.round

/** A single coloured fill polyline for a route gradient segment. */
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
 * A single chevron symbol placed along the route. Placement is route-wide, then filtered onto the
 * coloured gradient run it lands on. The bearing is the direction the chevron points, taken as a
 * chord across the bearing window (about 24 m on device); 10 m survives only as the fallback when
 * no window is configured. [colorArgb] is the gradient-band colour of the polyline run the chevron
 * sits on.
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
    fun contains(lat: Double, lng: Double): Boolean = lat in minLat..maxLat && lng in minLng..maxLng
}

/** Default chevron spacing when no zoom-adaptive step is supplied. */
internal const val DEFAULT_CHEVRON_SPACING_M = 60.0

/**
 * A maximal stretch of route sharing one band colour.
 *
 * Four granularities are in play here, finest to coarsest:
 * - segment: one adjacent pair of simplified elevation vertices.
 * - cell: a fixed length of route, coloured by its mean grade across the segments it covers.
 * - run: consecutive cells resolving to the same colour, merged.
 * - chain: consecutive runs with no distance gap between them.
 *
 * Under full coverage every cell yields a colour, so every run abuts its neighbours and the whole
 * route is a single chain.
 */
internal data class GradeRun(val startM: Double, val endM: Double, val colorArgb: Int)

/** One stroke width on screen: the width the run is drawn at is the length it must own. */
internal const val MIN_RUN_PX = 12.0

/**
 * Ceiling on the cells one call may tile, and so the resampler's real precondition on its cell
 * length. See [resampleRunsToCells].
 */
private const val MAX_CELLS = 1e6

/**
 * Cell length: the grade baseline, or one stroke width on screen, whichever is larger.
 *
 * This is a legibility guard — it sets how much route one colour must own before the overlay may
 * change colour again, so a run is never drawn shorter than it is wide. The sparkline's own
 * `MIN_FILL_PX` answers a different question: it drops a fill narrower than a single pixel, which
 * is a rendering guard against a band that would come out invisible.
 */
internal fun minRunLengthM(metresPerPixel: Double): Double =
    maxOf(GRADE_BASELINE_M, MIN_RUN_PX * metresPerPixel)

/**
 * Tiles `[0, routeEndM]` into cells of [cellM], colours each by the band of its mean grade, and
 * merges adjacent cells that land in the same band. No run comes out shorter than [cellM] except
 * the tail, where the route is not a whole number of cells.
 *
 * The mean grade is the chord `(elevAtM(end) - elevAtM(start)) / (end - start)`. A chord rather
 * than a fitted line: it meets the true profile at every cell boundary, where a fit meets it
 * nowhere. A mean rather than the cell's steepest segment: a short ramp inside an otherwise gentle
 * cell would otherwise repaint the whole cell at its own colour.
 */
internal fun resampleRunsToCells(
    routeEndM: Double,
    cellM: Double,
    elevAtM: (Double) -> Double,
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
    neutral: Color,
    readable: Boolean,
): List<GradeRun> {
    // The loop advances by `startM = endM`, and for a cell short enough against the route that
    // addition is a no-op in double precision, so the loop never ends. Bounding the cell
    // count is the stronger precondition: it holds [cellM] many orders of magnitude above the
    // step between doubles near [routeEndM], and caps the work besides. Callers floor the cell
    // at GRADE_BASELINE_M, far inside this.
    if (routeEndM <= 0.0 || cellM <= 0.0 || routeEndM / cellM > MAX_CELLS) return emptyList()
    val runs = mutableListOf<GradeRun>()
    // Tiling starts at 0 rather than at the profile's first vertex — the signature carries no
    // start distance. A profile beginning past 0 would have its leading stretch coloured by a
    // chord clamped flat, so neutral, where the old per-segment walk left it uncoloured. Every
    // profile we decode starts at 0.
    var startM = 0.0
    while (startM < routeEndM) {
        val endM = minOf(startM + cellM, routeEndM)
        val chordPct = ((elevAtM(endM) - elevAtM(startM)) / (endM - startM)) * 100.0
        // Rounded before the band lookup. The chord subtracts two elevations interpolated
        // from vertices held as Float, so a cell inside a stretch of constant grade lands
        // either side of the grade the stretch actually holds — by up to one Float step at
        // the base elevation, spread over the cell. On the 30 m cell that is 4e-4 per cent at
        // 1200 m and 2e-3 per cent at 5000 m; the double arithmetic on top of it contributes
        // 1e-14. Band edges are whole per cents and a steady climb sits on one often enough
        // that this noise alone would stripe it into alternating bands.
        //
        // Rounding to 0.01 per cent is coarser than twice that error at any elevation a road
        // reaches, so both sides of an on-edge grade land back on the edge, and every band
        // edge is a whole multiple of it, so no edge shifts. It stays far finer than the
        // profile can resolve: one elevation quantum, 0.1 m, over a 30 m cell is a third of a
        // per cent, thirty times coarser than what this discards.
        val meanGradePct = round(chordPct * 100.0) / 100.0
        val color =
            gradeBandColor(
                    grade = meanGradePct,
                    palette = palette,
                    climbEdge = climbEdge,
                    descentEdge = descentEdge,
                    neutral = neutral,
                    readable = readable,
                )
                .toArgb()
        val last = runs.lastOrNull()
        if (last != null && last.colorArgb == color) {
            runs[runs.lastIndex] = last.copy(endM = endM)
        } else {
            runs += GradeRun(startM, endM, color)
        }
        startM = endM
    }
    return runs
}

/**
 * Linear-interpolated elevation at [distanceM] along [points], a distance-ascending profile. Clamps
 * to the first and last vertex outside the profile's own extent. [points] must hold at least one
 * vertex.
 */
internal fun elevationAtM(points: List<Pair<Float, Float>>, distanceM: Double): Double {
    val first = points.first()
    if (distanceM <= first.first) return first.second.toDouble()
    val last = points.last()
    if (distanceM >= last.first) return last.second.toDouble()
    var lo = 0
    var hi = points.lastIndex
    while (lo + 1 < hi) {
        val mid = (lo + hi) / 2
        if (points[mid].first <= distanceM) lo = mid else hi = mid
    }
    val (d0, e0) = points[lo]
    val (d1, e1) = points[hi]
    val spanM = (d1 - d0).toDouble()
    if (spanM <= 0.0) return e1.toDouble()
    return e0 + (e1 - e0) * (distanceM - d0) / spanM
}

/**
 * Builds gradient polyline specs and chevron symbol specs along a route's elevation profile,
 * mirroring the HUD elevation sparkline. The Karoo SDK `Climb` list is intentionally not used — the
 * rider's mental model of "where it gets coloured" must match the sparkline, and the sparkline is
 * driven purely by the elevation polyline + per-consumer simplification and grade edges. [tuning]
 * is the already-resolved tuning: callers hand it the output of `resolveGradeMapTuning`, so the
 * sparkline-sync question is settled before we are called and can never be answered differently
 * here.
 *
 * Algorithm:
 * 1. Decode the GPS polyline and compute cumulative distance.
 * 2. Decode the elevation polyline and run Visvalingam–Whyatt simplification at
 *    [effectiveMinAreaM2], which scales up from the base [EffectiveGradeMapTuning.simplification]
 *    as [metresPerPixel] grows (zooming out), so the profile coarsens instead of flooding the
 *    overlay with detail no zoom level can render.
 * 3. Resample the simplified profile into fixed-length cells with [resampleRunsToCells], sized by
 *    [minRunLengthM] from the same [metresPerPixel]. Every cell takes a colour from its mean grade:
 *    its grade band's when the mean is past that side's edge, the flat grey when it is not.
 *    Adjacent same-colour cells merge into runs, each emitting a single polyline spanning
 *    `[runStartM, runEndM]`. The runs tile the route, so our overlay covers Karoo's own route line
 *    everywhere, in both directions of travel, and none of them is too short to read.
 * 4. Place chevrons along the whole route with [placeChevrons], then colour each placement with the
 *    run containing it and drop the placements that sit on no run. Placement is a property of the
 *    route, not of the runs: a run shorter than the spacing carries a chevron only when a cadence
 *    position happens to fall inside it, so short runs are marked by their colour alone.
 * 5. If [chevronViewport] is non-null, drop any chevron whose lat/lng falls outside the viewport
 *    bounds. This keeps the emitted symbol count bounded regardless of route length — we only
 *    render what the rider can see.
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
    metresPerPixel: Double = 0.0,
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
    val elevPoints =
        visvalingamWhyatt(rawElev, effectiveMinAreaM2(tuning.simplification, metresPerPixel))
    if (elevPoints.size < 2) return GradeMapSpecs(emptyList(), emptyList())

    // Runs are cut on the elevation polyline's distance axis, but drawn by looking their
    // bounds up in arclength over the GPS polyline. The GPS polyline cuts chords across
    // curves, so its arclength runs slightly short of the elevation axis and the gap grows
    // along the route — segment bounds and chevron colours drift, worst at the far end.
    // Scaling elevation-axis distances by the ratio of the two totals pins the ends back
    // together. Chord shortfall stays well within a percent; a ratio outside the guard band
    // means the two polylines do not span the same extent (an elevation profile covering
    // only part of the route), where scaling would misplace every run, so it stays off.
    val elevSpanM = elevPoints.last().first.toDouble()
    val ratio = if (elevSpanM > 0.0) cumDist.last() / elevSpanM else 1.0
    val elevToGps = if (ratio in 0.9..1.1) ratio else 1.0

    // The runs have one derivation: the profile is resampled into cells and each takes the
    // band of its mean grade, so the vertex spacing decides nothing about where a colour may
    // change. Every cell gets a colour, so the runs tile the route and no gap can open onto
    // the line underneath.
    val runs =
        resampleRunsToCells(
            routeEndM = elevPoints.last().first.toDouble(),
            // Below 2.5 m/px (MIN_RUN_PX * metresPerPixel < GRADE_BASELINE_M) the 30 m floor
            // binds and this is unchanged from zoomed-in; it only grows zoomed out.
            cellM = minRunLengthM(metresPerPixel),
            elevAtM = { distanceM -> elevationAtM(elevPoints, distanceM) },
            palette = palette,
            climbEdge = tuning.climbEdge,
            descentEdge = tuning.descentEdge,
            neutral = mapNeutral(palette, readable),
            readable = readable,
        )

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
        val sub = extractSubPolyline(gps, cumDist, drawStart * elevToGps, drawEnd * elevToGps)
        if (sub.size >= 2) {
            polylines +=
                GradeMapPolylineSpec(
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
    val chevrons =
        if (includeChevrons) {
            val tuning =
                ChevronTuning(
                    spacingM = chevronSpacingM,
                    windowHalfM = chevronWindowHalfM,
                    collisionRadiusM = chevronMinSpacingM,
                    headingThresholdDeg = chevronHeadingThresholdDeg,
                )
            placeChevrons(gps, cumDist, tuning).mapIndexedNotNull { idx, placement ->
                val run =
                    runs.firstOrNull {
                        placement.distanceM >= it.startM * elevToGps &&
                            placement.distanceM < it.endM * elevToGps
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
    val filteredChevrons =
        if (chevronViewport != null) {
            chevrons.filter { chevronViewport.contains(it.lat, it.lng) }
        } else {
            chevrons
        }
    return GradeMapSpecs(polylines, filteredChevrons)
}
