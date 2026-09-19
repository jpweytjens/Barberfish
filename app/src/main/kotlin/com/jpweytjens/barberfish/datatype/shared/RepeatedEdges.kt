package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Endpoint tolerance for two GPS edges to count as the same piece of road, in metres. A planned
 * route that reuses a road encodes the same vertices for both passes; the tolerance only has to
 * absorb the polyline's five-decimal rounding, which moves a vertex by up to about 0.7 m.
 */
internal const val MATCH_M = 1.5

/** How an edge occurrence runs relative to the first occurrence in its group. */
internal enum class Traversal {
    SAME,
    OPPOSITE,
}

/** Edge [edge] of the route (linking vertex `edge` to `edge + 1`), ridden [traversal]. */
internal data class EdgeOccurrence(val edge: Int, val traversal: Traversal)

/** One piece of road the route covers more than once: its edge occurrences in ride order. */
internal data class EdgeGroup(val occurrences: List<EdgeOccurrence>)

private class Metres(val x: Double, val y: Double)

/**
 * Groups the edges of [gps] that the route rides more than once. An edge A-B joins an earlier group
 * when both its endpoints lie within [matchM] of the group's endpoints, in either orientation,
 * against every member already in the group. One shared endpoint (a junction, a crossing) is not
 * enough, and proximity is not transitive: a chain of parallel roads each within tolerance of the
 * next does not collapse into one group. Edges no longer than `2 * matchM` are skipped: at that
 * scale the tolerance cannot tell a neighbour from a return visit.
 *
 * Endpoints are hashed into cells [matchM] wide on a local metre projection; a lookup checks the
 * cell and its eight neighbours, then the actual distances, so a pair straddling a cell boundary
 * still matches. When several groups qualify the smallest maximum endpoint error wins, ties going
 * to the earliest group.
 */
internal fun matchRepeatedEdges(gps: List<LatLng>, matchM: Double = MATCH_M): List<EdgeGroup> {
    if (gps.size < 2 || matchM <= 0.0) return emptyList()
    val degToM = EARTH_RADIUS_M * PI / 180.0
    val lngScale = cos(gps.first().lat * PI / 180.0)
    val pts = gps.map { Metres(it.lng * lngScale * degToM, it.lat * degToM) }
    fun dist(a: Metres, b: Metres) = hypot(a.x - b.x, a.y - b.y)
    fun cell(p: Metres) = floor(p.x / matchM).toLong() to floor(p.y / matchM).toLong()
    fun endpoints(o: EdgeOccurrence): Pair<Metres, Metres> {
        val start = pts[o.edge]
        val end = pts[o.edge + 1]
        return if (o.traversal == Traversal.SAME) start to end else end to start
    }

    val groups = mutableListOf<MutableList<EdgeOccurrence>>()
    val cells = HashMap<Pair<Long, Long>, MutableList<Int>>()
    for (edge in 0 until gps.size - 1) {
        val a = pts[edge]
        val b = pts[edge + 1]
        if (dist(a, b) <= 2.0 * matchM) continue
        val (cx, cy) = cell(a)
        val candidates = sortedSetOf<Int>()
        for (dx in -1L..1L) {
            for (dy in -1L..1L) {
                cells[(cx + dx) to (cy + dy)]?.let { candidates += it }
            }
        }
        var bestGroup = -1
        var bestErr = Double.MAX_VALUE
        var bestTraversal = Traversal.SAME
        for (group in candidates) {
            for (traversal in Traversal.entries) {
                val (start, end) = if (traversal == Traversal.SAME) a to b else b to a
                var err = 0.0
                for (member in groups[group]) {
                    val (memberStart, memberEnd) = endpoints(member)
                    err = maxOf(err, dist(start, memberStart), dist(end, memberEnd))
                    if (err > matchM) break
                }
                if (err <= matchM && err < bestErr) {
                    bestGroup = group
                    bestErr = err
                    bestTraversal = traversal
                }
            }
        }
        if (bestGroup >= 0) {
            groups[bestGroup] += EdgeOccurrence(edge, bestTraversal)
        } else {
            groups += mutableListOf(EdgeOccurrence(edge, Traversal.SAME))
            val group = groups.lastIndex
            cells.getOrPut(cell(a)) { mutableListOf() } += group
            cells.getOrPut(cell(b)) { mutableListOf() } += group
        }
    }
    return groups.filter { it.size > 1 }.map { EdgeGroup(it.toList()) }
}
