package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.GRADE_BASELINE_M
import com.jpweytjens.barberfish.datatype.shared.elevationAtM
import com.jpweytjens.barberfish.datatype.shared.minRunLengthM
import com.jpweytjens.barberfish.datatype.shared.resampleRunsToCells
import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapWindowTest {

    private val neutral = Color(0xFFC4C4C4)

    /** Flat for 700 m, then a 100 m wall at 20 per cent. */
    private fun spikeElev(d: Double): Double = if (d <= 700.0) 0.0 else (d - 700.0) * 0.20

    /** Steady 6 per cent for the whole route. */
    private fun steadyElev(d: Double): Double = d * 0.06

    /**
     * Alternating 200 m blocks of 10 per cent and 3 per cent. Against 200 m cells every cell
     * covers exactly one block, so consecutive cells land in different bands (salmon, then
     * mint) and no two of them coalesce — the run list is the cell list.
     */
    private fun staircaseElev(d: Double): Double {
        var elev = 0.0
        var at = 0.0
        while (at < d) {
            val step = minOf(200.0, d - at)
            elev += step * (if ((at / 200.0).toInt() % 2 == 0) 0.10 else 0.03)
            at += step
        }
        return elev
    }

    /**
     * A straight 8.00 per cent climb from 1234.5 m, as a simplified profile actually reaches
     * the resampler: vertices about 90 m apart, distances and elevations quantised to 0.1 m
     * and held as `Float`. 8 per cent is a band edge, so the band a cell lands in turns on
     * which side of 8.0 its chord falls, and `Float` storage of the vertices alone is enough
     * to put neighbouring cells on opposite sides. The base elevation is what makes it bite:
     * the chord strays from 8.00 by 8e-5 per cent here, against 2e-5 from a zero base and
     * 3e-4 at 5000 m.
     */
    private val steadyEightFromAltitude: List<Pair<Float, Float>> =
        List(31) { i ->
            val distanceM = i * 90.0
            val elevationM = 1234.5 + distanceM * 0.08
            (distanceM * 10).roundToInt() / 10f to (elevationM * 10).roundToInt() / 10f
        }

    private fun guard(cellM: Double, endM: Double, elev: (Double) -> Double) =
        resampleRunsToCells(
            routeEndM = endM,
            cellM = cellM,
            elevAtM = elev,
            palette = GradePalette.BARBERFISH,
            climbEdge = 2.0,
            descentEdge = -2.0,
            neutral = neutral,
            readable = false,
        )

    @Test
    fun every_run_is_at_least_the_cell_length_except_the_tail() {
        val runs = guard(cellM = 200.0, endM = 2050.0, elev = ::staircaseElev)
        val measurable = runs.dropLast(1)
        // Guards the guard: a fixture whose cells all share a band would coalesce into a
        // single run and leave nothing here to measure.
        assertTrue("fixture produced no full-length runs to measure", measurable.isNotEmpty())
        measurable.forEach {
            assertTrue("run ${it.startM}-${it.endM} is shorter than the cell",
                it.endM - it.startM >= 200.0)
        }
    }

    @Test
    fun cell_takes_the_band_of_its_mean_grade_not_its_steepest() {
        // One 800 m cell: 700 m flat then 100 m at 20 per cent. Mean is 2.5 per cent,
        // so the 2 to 5 band, not the 20-plus band.
        val runs = guard(cellM = 800.0, endM = 800.0, elev = ::spikeElev)
        assertEquals(1, runs.size)
        assertEquals(Color(0xFF40D078).toArgb(), runs.single().colorArgb)
    }

    @Test
    fun the_tail_cell_may_be_shorter_than_the_cell_length() {
        val runs = guard(cellM = 200.0, endM = 450.0, elev = ::steadyElev)
        assertEquals(450.0, runs.last().endM, 0.001)
    }

    @Test
    fun a_steady_climb_sitting_on_a_band_edge_stays_one_run() {
        // Pins the rounding step in resampleRunsToCells. Without it the chord noise alone
        // decides the band on every cell of a climb whose true grade is a whole per cent, and
        // the overlay stripes between salmon and yellow down a stretch the rider sees as one
        // steady gradient.
        val runs = guard(
            cellM = GRADE_BASELINE_M,
            endM = steadyEightFromAltitude.last().first.toDouble(),
            elev = { distanceM -> elevationAtM(steadyEightFromAltitude, distanceM) },
        )
        assertEquals(
            "a straight 8.00 per cent climb striped into ${runs.size} runs",
            1,
            runs.size,
        )
        // Rounded back onto the edge, so it lands in the band its true grade belongs to.
        assertEquals(Color(0xFFF08868).toArgb(), runs.single().colorArgb)
    }

    @Test(timeout = 10_000)
    fun a_cell_too_short_to_advance_the_tiling_returns_nothing() {
        // Without the cell-count bound, `startM + cellM` rounds back to `startM` and the loop
        // never ends. Unreachable through buildGradeMapSpecs, which floors the cell at the
        // grade baseline, but it is this function's own precondition.
        assertTrue(guard(cellM = 1e-9, endM = 1e9, elev = ::steadyElev).isEmpty())
    }

    @Test
    fun guard_floors_at_the_grade_baseline() {
        assertEquals(GRADE_BASELINE_M, minRunLengthM(metresPerPixel = 1.0), 0.001)
        assertEquals(120.0, minRunLengthM(metresPerPixel = 10.0), 0.001)
    }
}
