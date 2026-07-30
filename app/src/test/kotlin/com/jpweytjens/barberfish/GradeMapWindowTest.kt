package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.GRADE_BASELINE_M
import com.jpweytjens.barberfish.datatype.shared.minRunLengthM
import com.jpweytjens.barberfish.datatype.shared.resampleRunsToCells
import com.jpweytjens.barberfish.extension.GradePalette
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
    fun guard_floors_at_the_grade_baseline() {
        assertEquals(GRADE_BASELINE_M, minRunLengthM(metresPerPixel = 1.0), 0.001)
        assertEquals(120.0, minRunLengthM(metresPerPixel = 10.0), 0.001)
    }
}
