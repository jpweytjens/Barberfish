package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.extension.CASING_COLOR
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.RejoinRoad

/** The reroute band's fill: the path back to the route, drawn in the rerouting red. */
internal const val GRADE_MAP_REJOIN_ID = "barberfish-rejoin"

/** The reroute band's casing. */
internal const val GRADE_MAP_REJOIN_CASING_ID = "barberfish-rejoin-casing"

/** Positional chevron ids along the rejoin path, separate from the route's own range. */
internal fun gradeMapRejoinChevronId(index: Int): String = "barberfish-rejoin-chev-$index"

/** Positional ids of the graded rejoin band's fill pieces, separate from the route's own range. */
internal fun gradeMapRejoinSegmentId(index: Int): String = "barberfish-rejoin-seg-$index"

/**
 * What the reroute band draws: its fills along the rejoin path, the casing geometry beneath them in
 * [casingColorArgb], and its chevrons. [fills] is empty when the path is too short to draw.
 * [segmentIdSpan] bounds the positional fill ids, 0 for the solid band, whose one fill has a fixed
 * id. [chevronIdSpan] bounds the positional chevron ids; for the solid band it is their count.
 */
internal data class RejoinSpecs(
    val fills: List<GradeMapPolylineSpec>,
    val casing: String,
    val chevrons: List<ClimbChevronSpec>,
    val segmentIdSpan: Int = 0,
    val chevronIdSpan: Int = 0,
    val casingColorArgb: Int = CASING_COLOR,
)

/**
 * Builds the reroute band for a rejoin line matched to the road with its heights: the route's own
 * builder, given the road as the line and its heights as the profile, so the band takes grade
 * colours, the road's shape and the route's chevrons. Chevrons are returned on the road. The casing
 * runs along the whole road in the rerouting red, the one mark of the way back. The other arguments
 * are [buildGradeMapSpecs]'s.
 */
// Suppressed: the arguments are buildGradeMapSpecs's, passed straight through.
@Suppress("LongParameterList")
internal fun buildGradedRejoinSpecs(
    road: RejoinRoad,
    palette: GradePalette,
    readable: Boolean,
    tuning: EffectiveGradeMapTuning,
    includeChevrons: Boolean = true,
    chevronBlend: Double = 0.5,
    chevronGradeFullPct: Double = 15.0,
    chevronChangeWindowM: Double = 60.0,
    chevronChangeFullPctPerM: Double = 0.2,
    chevronSpacingMaxM: Double = 0.0,
    chevronSpacingMinM: Double = 0.0,
    chevronWindowHalfM: Double = 0.0,
    chevronMinSpacingM: Double = 0.0,
    capTrimM: Double = 0.0,
    casingCapTrimM: Double = 0.0,
    metresPerPixel: Double = 0.0,
): RejoinSpecs {
    val specs =
        buildGradeMapSpecs(
            routePolyline = road.rejoinPolyline,
            routeElevationPolyline = null,
            palette = palette,
            readable = readable,
            tuning = tuning,
            includeChevrons = includeChevrons,
            chevronBlend = chevronBlend,
            chevronGradeFullPct = chevronGradeFullPct,
            chevronChangeWindowM = chevronChangeWindowM,
            chevronChangeFullPctPerM = chevronChangeFullPctPerM,
            chevronSpacingMaxM = chevronSpacingMaxM,
            chevronSpacingMinM = chevronSpacingMinM,
            chevronWindowHalfM = chevronWindowHalfM,
            chevronMinSpacingM = chevronMinSpacingM,
            capTrimM = capTrimM,
            casingCapTrimM = casingCapTrimM,
            reversed = false,
            metresPerPixel = metresPerPixel,
            routeIndex = null,
            matchedRoadLine = road.line,
            ids = GradeMapIds.REJOIN,
            elevation = road.heights,
        )
    val casing = road.line.pointsBetween(casingCapTrimM, road.lengthM - casingCapTrimM)
    return RejoinSpecs(
        fills = specs.polylines,
        casing =
            encodeGpsPolyline(
                casing.takeIf { it.size >= 2 } ?: road.line.pointsBetween(0.0, road.lengthM)
            ),
        chevrons = specs.chevrons.map { specs.chevronRoad?.moved(it) ?: it },
        segmentIdSpan = specs.segmentIdSpan,
        chevronIdSpan = specs.chevronIdSpan,
        casingColorArgb = RerouteRed.toArgb(),
    )
}

/**
 * Builds the reroute band for [rejoinPolyline], the path back to the route while the rider is off
 * it. There is no elevation for the detour, so the whole band takes [colorArgb] and the chevrons
 * sit at [chevronSpacingM], the sparse end of the route's own cadence, no closer than
 * [chevronMinSpacingM]. Fill and casing are pulled in at both ends by [capTrimM] and
 * [casingCapTrimM] so their round caps land on the path's true ends.
 */
internal fun buildRejoinSpecs(
    rejoinPolyline: String,
    colorArgb: Int,
    chevronSpacingM: Double,
    chevronMinSpacingM: Double,
    capTrimM: Double = 0.0,
    casingCapTrimM: Double = 0.0,
    includeChevrons: Boolean = true,
): RejoinSpecs {
    val gps = decodeGpsPolyline(rejoinPolyline)
    if (gps.size < 2) return RejoinSpecs(emptyList(), "", emptyList())
    val cumDist = cumulativeDistancesM(gps)
    val totalM = cumDist.last()
    if (totalM <= 0.0) return RejoinSpecs(emptyList(), "", emptyList())
    fun trimmed(trimM: Double): List<LatLng> {
        val trim = trimM.coerceAtMost((totalM * 0.5 - 0.5).coerceAtLeast(0.0))
        return extractSubPolyline(gps, cumDist, trim, totalM - trim)
    }
    val fillPoints = trimmed(capTrimM)
    val casingPoints = trimmed(casingCapTrimM)
    val fill =
        if (fillPoints.size >= 2) {
            GradeMapPolylineSpec(
                id = GRADE_MAP_REJOIN_ID,
                encoded = encodeGpsPolyline(fillPoints),
                colorArgb = colorArgb,
                trimStart = true,
                trimEnd = true,
            )
        } else {
            null
        }
    val chevrons =
        if (!includeChevrons) emptyList()
        else
            placeChevronsByCadence(
                    gps = gps,
                    cumDist = cumDist,
                    gradeAtM = { 0.0 },
                    changeAtM = { 0.0 },
                    alpha = 0.0,
                    gradeFullPct = 1.0,
                    changeFullPctPerM = 1.0,
                    spacingMaxM = chevronSpacingM,
                    spacingMinM = chevronSpacingM,
                    collisionRadiusM = chevronMinSpacingM,
                    windowHalfM = 0.0,
                )
                .mapIndexed { index, p ->
                    ClimbChevronSpec(
                        id = gradeMapRejoinChevronId(index),
                        lat = p.lat,
                        lng = p.lng,
                        bearingDeg = p.bearingDeg,
                        colorArgb = colorArgb,
                        distanceM = p.distanceM,
                    )
                }
    return RejoinSpecs(
        fills = listOfNotNull(fill),
        casing = if (casingPoints.size >= 2) encodeGpsPolyline(casingPoints) else rejoinPolyline,
        chevrons = chevrons,
        chevronIdSpan = chevrons.size,
    )
}
