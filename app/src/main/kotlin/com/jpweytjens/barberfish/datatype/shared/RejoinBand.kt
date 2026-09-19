package com.jpweytjens.barberfish.datatype.shared

/** The reroute band's fill: the path back to the route, drawn in the rerouting red. */
internal const val GRADE_MAP_REJOIN_ID = "barberfish-rejoin"

/** The reroute band's casing. */
internal const val GRADE_MAP_REJOIN_CASING_ID = "barberfish-rejoin-casing"

/** Positional chevron ids along the rejoin path, separate from the route's own range. */
internal fun gradeMapRejoinChevronId(index: Int): String = "barberfish-rejoin-chev-$index"

/**
 * What the reroute band draws: one fill along the rejoin path, its casing geometry, and chevrons at
 * a constant cadence. [fill] is null when the path is too short to draw.
 */
internal data class RejoinSpecs(
    val fill: GradeMapPolylineSpec?,
    val casing: String,
    val chevrons: List<ClimbChevronSpec>,
)

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
): RejoinSpecs {
    val gps = decodeGpsPolyline(rejoinPolyline)
    if (gps.size < 2) return RejoinSpecs(null, "", emptyList())
    val cumDist = cumulativeDistancesM(gps)
    val totalM = cumDist.last()
    if (totalM <= 0.0) return RejoinSpecs(null, "", emptyList())
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
        fill = fill,
        casing = if (casingPoints.size >= 2) encodeGpsPolyline(casingPoints) else rejoinPolyline,
        chevrons = chevrons,
    )
}
