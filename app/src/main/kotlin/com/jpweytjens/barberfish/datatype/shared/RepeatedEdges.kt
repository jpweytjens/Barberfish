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

private fun dist(a: Metres, b: Metres) = hypot(a.x - b.x, a.y - b.y)

/** Which group an edge joins, and how it runs against that group's first occurrence. */
private class GroupMatch(val group: Int, val traversal: Traversal)

/**
 * Builds the groups edge by edge in ride order. The cell index holds, per group, the cells of the
 * endpoints of its first occurrence, so a lookup only tests the groups that start near the edge.
 */
private class EdgeGrouper(private val pts: List<Metres>, private val matchM: Double) {
    val groups = mutableListOf<MutableList<EdgeOccurrence>>()
    private val cells = HashMap<Pair<Long, Long>, MutableList<Int>>()

    fun add(edge: Int) {
        val a = pts[edge]
        val b = pts[edge + 1]
        if (dist(a, b) <= 2.0 * matchM) return
        val match = bestMatch(a, b)
        if (match != null) {
            groups[match.group] += EdgeOccurrence(edge, match.traversal)
        } else {
            groups += mutableListOf(EdgeOccurrence(edge, Traversal.SAME))
            val group = groups.lastIndex
            cells.getOrPut(cell(a)) { mutableListOf() } += group
            cells.getOrPut(cell(b)) { mutableListOf() } += group
        }
    }

    private fun cell(p: Metres) = floor(p.x / matchM).toLong() to floor(p.y / matchM).toLong()

    private fun endpoints(o: EdgeOccurrence): Pair<Metres, Metres> {
        val start = pts[o.edge]
        val end = pts[o.edge + 1]
        return if (o.traversal == Traversal.SAME) start to end else end to start
    }

    /** The groups indexed in [p]'s cell or one of its eight neighbours, in group order. */
    private fun candidatesNear(p: Metres): Set<Int> {
        val (cx, cy) = cell(p)
        val candidates = sortedSetOf<Int>()
        for (dx in -1L..1L) {
            for (dy in -1L..1L) {
                cells[(cx + dx) to (cy + dy)]?.let { candidates += it }
            }
        }
        return candidates
    }

    /**
     * The largest endpoint error between the edge [start]-[end] and any member of group [group],
     * giving up as soon as it passes [matchM].
     */
    private fun endpointError(start: Metres, end: Metres, group: Int): Double {
        var err = 0.0
        for (member in groups[group]) {
            val (memberStart, memberEnd) = endpoints(member)
            err = maxOf(err, dist(start, memberStart), dist(end, memberEnd))
            if (err > matchM) break
        }
        return err
    }

    /** The group the edge [a]-[b] joins: smallest maximum endpoint error, ties to the earliest. */
    private fun bestMatch(a: Metres, b: Metres): GroupMatch? {
        var best: GroupMatch? = null
        var bestErr = Double.MAX_VALUE
        for (group in candidatesNear(a)) {
            for (traversal in Traversal.entries) {
                val (start, end) = if (traversal == Traversal.SAME) a to b else b to a
                val err = endpointError(start, end, group)
                if (err <= matchM && err < bestErr) {
                    best = GroupMatch(group, traversal)
                    bestErr = err
                }
            }
        }
        return best
    }
}

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
    val midLat = (gps.minOf { it.lat } + gps.maxOf { it.lat }) * 0.5
    val lngScale = cos(midLat * PI / 180.0)
    val pts = gps.map { Metres(it.lng * lngScale * degToM, it.lat * degToM) }
    val grouper = EdgeGrouper(pts, matchM)
    for (edge in 0 until gps.size - 1) grouper.add(edge)
    return grouper.groups.filter { it.size > 1 }.map { EdgeGroup(it.toList()) }
}
