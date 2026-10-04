package com.jpweytjens.barberfish.datatype.shared

/**
 * One pass of the route over one unit of ground. [key] is the visit's ordinal in route order, which
 * makes it a pure function of the route geometry and direction, so hidden state keyed on it
 * survives a rebuild and can be reconstructed after a restart. [depth] is one plus the number of
 * later visits to the same unit: a drawing priority, lowest drawn first, so the first visit lands
 * on top. [nextVisitM] is where the route next returns to this unit, absent for its last visit.
 */
internal data class RouteVisit(
    val key: Int,
    val startM: Double,
    val endM: Double,
    val unit: Int,
    val depth: Int,
    val nextVisitM: Double?,
    val bounds: LatLngBounds,
)

/**
 * The route's ground, in ride order. A unit is either one matched edge group or one maximal stretch
 * of ground the route covers once; the visits tile `[0, lengthM]` on the GPS distance axis with no
 * gaps. Built once per route identity and shared by every rebuild.
 */
internal class RouteIndex(
    val gps: List<LatLng>,
    val cumDist: DoubleArray,
    val visits: List<RouteVisit>,
) {
    val lengthM: Double
        get() = if (cumDist.isEmpty()) 0.0 else cumDist.last()

    private val byUnit: Map<Int, List<RouteVisit>> = visits.groupBy { it.unit }

    fun visit(key: Int): RouteVisit = visits[key]

    fun visitsOfUnit(unit: Int): List<RouteVisit> = byUnit[unit].orEmpty()

    /** The visit covering [distanceM]: start inclusive, end exclusive, closed at the route end. */
    fun visitAt(distanceM: Double): RouteVisit? {
        if (visits.isEmpty() || distanceM < 0.0 || distanceM > lengthM) return null
        var lo = 0
        var hi = visits.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (visits[mid].startM <= distanceM) lo = mid else hi = mid - 1
        }
        return visits[lo]
    }
}

internal fun buildRouteIndex(
    routePolyline: String,
    reversed: Boolean,
    matchM: Double = MATCH_M,
): RouteIndex {
    val decoded = decodeGpsPolyline(routePolyline)
    val gps = if (reversed) decoded.asReversed() else decoded
    return buildRouteIndex(gps, cumulativeDistancesM(gps), matchM)
}

internal fun buildRouteIndex(
    gps: List<LatLng>,
    cumDist: DoubleArray,
    matchM: Double = MATCH_M,
): RouteIndex {
    if (gps.size < 2) return RouteIndex(gps, cumDist, emptyList())
    val groups = matchRepeatedEdges(gps, matchM)
    val edgeCount = gps.size - 1
    val unitOfEdge = IntArray(edgeCount) { -1 }
    groups.forEachIndexed { unit, group ->
        group.occurrences.forEach { unitOfEdge[it.edge] = unit }
    }

    // Walk the edges in ride order: a matched edge is one span of its group's unit, a run of
    // unmatched edges is one span of a fresh unit. Zero-length spans (a duplicate vertex between
    // two matched edges) are dropped so the visits tile the route without empty entries.
    class Span(val unit: Int, val firstEdge: Int, val lastEdge: Int)
    val spans = mutableListOf<Span>()
    var nextUnit = groups.size
    var edge = 0
    while (edge < edgeCount) {
        if (unitOfEdge[edge] >= 0) {
            spans += Span(unitOfEdge[edge], edge, edge)
            edge++
        } else {
            var last = edge
            while (last + 1 < edgeCount && unitOfEdge[last + 1] < 0) last++
            if (cumDist[last + 1] > cumDist[edge]) spans += Span(nextUnit++, edge, last)
            edge = last + 1
        }
    }

    val visitsPerUnit = IntArray(nextUnit)
    spans.forEach { visitsPerUnit[it.unit]++ }
    val nextVisitM = arrayOfNulls<Double>(spans.size)
    val laterStartByUnit = HashMap<Int, Double>()
    for (i in spans.indices.reversed()) {
        val span = spans[i]
        nextVisitM[i] = laterStartByUnit[span.unit]
        laterStartByUnit[span.unit] = cumDist[span.firstEdge]
    }
    val rankInUnit = IntArray(nextUnit)
    val visits = spans.mapIndexed { i, span ->
        val rank = rankInUnit[span.unit]++
        RouteVisit(
            key = i,
            startM = cumDist[span.firstEdge],
            endM = cumDist[span.lastEdge + 1],
            unit = span.unit,
            depth = visitsPerUnit[span.unit] - rank,
            nextVisitM = nextVisitM[i],
            bounds = boundsOf(gps.subList(span.firstEdge, span.lastEdge + 2)),
        )
    }
    return RouteIndex(gps, cumDist, visits)
}

internal fun boundsOf(points: List<LatLng>): LatLngBounds =
    LatLngBounds(
        minLat = points.minOf { it.lat },
        maxLat = points.maxOf { it.lat },
        minLng = points.minOf { it.lng },
        maxLng = points.maxOf { it.lng },
    )

/** Metres from [point] to the nearest point of [bounds]; zero inside. */
internal fun distanceToBoundsM(point: LatLng, bounds: LatLngBounds): Double =
    latLngDistanceM(
        point,
        LatLng(
            point.lat.coerceIn(bounds.minLat, bounds.maxLat),
            point.lng.coerceIn(bounds.minLng, bounds.maxLng),
        ),
    )
