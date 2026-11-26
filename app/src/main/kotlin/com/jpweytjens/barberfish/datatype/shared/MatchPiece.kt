package com.jpweytjens.barberfish.datatype.shared

/** [points] is what is sent to be matched, overlap included; [chords] is what is kept. */
internal data class MatchPiece(val points: IntRange, val chords: IntRange) {
    /** The same piece on the line read from its other end, [pointCount] points long. */
    fun mirrored(pointCount: Int): MatchPiece =
        MatchPiece(
            points = pointCount - 1 - points.last..pointCount - 1 - points.first,
            chords = pointCount - 2 - chords.last..pointCount - 2 - chords.first,
        )
}

/** Length of the piece the rider is on, matched first so the road nearby shows soonest. */
internal const val FIRST_PIECE_M = 10_000.0

/** Length of every other piece. */
internal const val PIECE_M = 25_000.0

/** How far a piece is extended at both ends, so the match has settled where its chords begin. */
internal const val PIECE_OVERLAP_M = 300.0

// Cumulative distances are sums of rounded terms: a point meant to lie exactly at a cut can sit
// a hair to either side of it.
private const val CUT_SLACK_M = 0.001

private fun piece(cumDist: DoubleArray, from: Int, to: Int): MatchPiece {
    val firstM = cumDist[from] - PIECE_OVERLAP_M - CUT_SLACK_M
    val lastM = cumDist[to] + PIECE_OVERLAP_M + CUT_SLACK_M
    return MatchPiece(
        points = cumDist.indexOfFirst { it >= firstM }..cumDist.indexOfLast { it <= lastM },
        chords = from until to,
    )
}

/**
 * The route cut into pieces, in the order to match them: the piece from the rider on, then onward
 * to the finish, then back from the rider to the start. Cuts fall on points. [cumDist] and [riderM]
 * are in ride order.
 */
internal fun matchPieces(cumDist: DoubleArray, riderM: Double): List<MatchPiece> {
    val last = cumDist.lastIndex
    val pieces = mutableListOf<MatchPiece>()
    if (last < 1) return pieces
    val rider = cumDist.indexOfLast { it <= riderM }.coerceAtLeast(0)
    var from = rider
    var lengthM = FIRST_PIECE_M
    while (from < last) {
        val cutM = cumDist[from] + lengthM - CUT_SLACK_M
        val to = (from + 1..last).firstOrNull { cumDist[it] >= cutM } ?: last
        pieces += piece(cumDist, from, to)
        from = to
        lengthM = PIECE_M
    }
    var to = rider
    while (to > 0) {
        val cutM = cumDist[to] - PIECE_M + CUT_SLACK_M
        val start = (to - 1 downTo 0).firstOrNull { cumDist[it] <= cutM } ?: 0
        pieces += piece(cumDist, start, to)
        to = start
    }
    return pieces
}
