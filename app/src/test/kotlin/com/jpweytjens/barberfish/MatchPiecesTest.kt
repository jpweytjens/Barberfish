package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.MatchPiece
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.matchPieces
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPiecesTest {
    // 100 km along x at 50 m steps: 2001 points, 2000 chords.
    private val cum = cumulativeDistancesM(RouteFixtures.straight(100_000.0, 50.0))

    // A piece's kept chords as the km they run from and to.
    private fun MatchPiece.keptKm() =
        (cum[chords.first] / 1000).roundToInt() to (cum[chords.last + 1] / 1000).roundToInt()

    @Test
    fun the_riders_piece_comes_first_then_ahead_then_behind() {
        assertEquals(
            listOf(40 to 50, 50 to 75, 75 to 100, 15 to 40, 0 to 15),
            matchPieces(cum, 40_000.0).map { it.keptKm() },
        )
    }

    @Test
    fun kept_chords_tile_the_route_once() {
        for (riderM in listOf(0.0, 40_000.0, 40_020.0, 99_990.0, 100_000.0, 250_000.0, -5.0)) {
            val kept = matchPieces(cum, riderM).map { it.chords }.sortedBy { it.first }
            assertEquals(0, kept.first().first)
            assertEquals(1999, kept.last().last)
            kept.zipWithNext { a, b -> assertEquals(a.last + 1, b.first) }
            assertTrue(kept.none { it.isEmpty() })
        }
    }

    @Test
    fun each_piece_reaches_300_m_past_its_kept_chords() {
        val piece = matchPieces(cum, 40_000.0)[1]
        assertEquals(49_700.0, cum[piece.points.first], 1.0)
        assertEquals(75_300.0, cum[piece.points.last], 1.0)
        assertEquals(0, matchPieces(cum, 0.0).first().points.first)
        assertEquals(2000, matchPieces(cum, 0.0).last().points.last)
    }

    @Test
    fun a_rider_between_two_points_starts_at_the_point_behind() {
        assertEquals(800, matchPieces(cum, 40_020.0).first().chords.first)
    }

    @Test
    fun a_rider_at_the_start_has_no_pieces_behind() {
        assertEquals(
            listOf(0 to 10, 10 to 35, 35 to 60, 60 to 85, 85 to 100),
            matchPieces(cum, 0.0).map { it.keptKm() },
        )
    }

    @Test
    fun a_rider_at_the_finish_has_only_pieces_behind() {
        assertEquals(
            listOf(75 to 100, 50 to 75, 25 to 50, 0 to 25),
            matchPieces(cum, 100_000.0).map { it.keptKm() },
        )
    }

    @Test
    fun a_short_route_is_one_piece() {
        val short = cumulativeDistancesM(RouteFixtures.straight(3_000.0, 50.0))
        assertEquals(listOf(MatchPiece(0..60, 0..59)), matchPieces(short, 0.0))
    }

    @Test
    fun fewer_than_two_points_give_no_pieces() {
        assertEquals(emptyList<MatchPiece>(), matchPieces(DoubleArray(0), 0.0))
        assertEquals(emptyList<MatchPiece>(), matchPieces(doubleArrayOf(0.0), 0.0))
    }

    @Test
    fun mirrored_reads_the_piece_from_the_other_end() {
        assertEquals(MatchPiece(10..18, 11..15), MatchPiece(2..10, 4..8).mirrored(21))
    }
}
