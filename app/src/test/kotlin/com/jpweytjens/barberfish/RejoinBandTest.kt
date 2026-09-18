package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.buildRejoinSpecs
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RejoinBandTest {

    private val red = 0xFFF80000.toInt()

    // Straight east along the equator; 0.018 degrees of longitude is about 2003 m.
    private val path = encodeGpsPolyline(listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.018)))

    private fun lenM(encoded: String) = cumulativeDistancesM(decodeGpsPolyline(encoded)).last()

    @Test
    fun fill_and_casing_span_the_path_trimmed_at_both_ends() {
        val specs =
            buildRejoinSpecs(
                rejoinPolyline = path,
                colorArgb = red,
                chevronSpacingM = 200.0,
                chevronMinSpacingM = 20.0,
                capTrimM = 10.0,
                casingCapTrimM = 12.0,
            )
        val fill = specs.fill ?: error("fill expected")
        assertEquals("barberfish-rejoin", fill.id)
        assertEquals(red, fill.colorArgb)
        assertTrue(fill.trimStart && fill.trimEnd)
        val pathM = lenM(path)
        assertEquals(pathM - 20.0, lenM(fill.encoded), 1.0)
        assertEquals(pathM - 24.0, lenM(specs.casing), 1.0)
    }

    @Test
    fun chevrons_sit_at_a_constant_cadence_with_their_own_ids() {
        val specs =
            buildRejoinSpecs(
                rejoinPolyline = path,
                colorArgb = red,
                chevronSpacingM = 200.0,
                chevronMinSpacingM = 20.0,
            )
        assertTrue(specs.chevrons.size in 9..11)
        assertEquals(
            specs.chevrons.indices.map { "barberfish-rejoin-chev-$it" },
            specs.chevrons.map { it.id },
        )
        val gaps = specs.chevrons.zipWithNext { a, b -> b.distanceM - a.distanceM }
        gaps.forEach { assertEquals(200.0, it, 2.0) }
        assertTrue(specs.chevrons.all { it.colorArgb == red })
    }

    @Test
    fun a_degenerate_path_draws_nothing() {
        val dot = encodeGpsPolyline(listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.0)))
        val specs = buildRejoinSpecs(dot, red, chevronSpacingM = 200.0, chevronMinSpacingM = 20.0)
        assertNull(specs.fill)
        assertTrue(specs.chevrons.isEmpty())
    }
}
