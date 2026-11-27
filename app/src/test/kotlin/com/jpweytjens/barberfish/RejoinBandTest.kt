package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.buildGradedRejoinSpecs
import com.jpweytjens.barberfish.datatype.shared.buildRejoinSpecs
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.heightsOnLine
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.RejoinRoad
import org.junit.Assert.assertEquals
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
        val fill = specs.fills.single()
        assertEquals("barberfish-rejoin", fill.id)
        assertEquals(red, fill.colorArgb)
        assertTrue(fill.trimStart && fill.trimEnd)
        val pathM = lenM(path)
        assertEquals(pathM - 20.0, lenM(fill.encoded), 1.0)
        assertEquals(pathM - 24.0, lenM(specs.casing), 1.0)
    }

    @Test
    fun without_chevrons_the_fill_and_casing_remain() {
        val specs =
            buildRejoinSpecs(
                rejoinPolyline = path,
                colorArgb = red,
                chevronSpacingM = 200.0,
                chevronMinSpacingM = 20.0,
                includeChevrons = false,
            )
        assertTrue(specs.chevrons.isEmpty())
        assertEquals(1, specs.fills.size)
        assertTrue(specs.casing.isNotEmpty())
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
        assertTrue(specs.fills.isEmpty())
        assertTrue(specs.chevrons.isEmpty())
    }

    // A 2 km rejoin line due east, its road 3 m north of it: flat for 500 m, an 8% climb to
    // 1500 m, flat again.
    private fun gradedRoad(): RejoinRoad {
        val points =
            decodeGpsPolyline(RouteFixtures.encoded(RouteFixtures.straight(2_000.0, 100.0)))
        val cumDist = cumulativeDistancesM(points)
        val road =
            MatchedRoad(
                points.map { it.copy(lat = it.lat + 3.0 / RouteFixtures.M_PER_DEG) },
                listOf(0f to 100f, 500f to 100f, 1_500f to 180f, 2_000f to 180f),
            )
        val onLine = heightsOnLine(points, cumDist, road) ?: error("heights expected")
        return RejoinRoad(encodeGpsPolyline(points), onLine.line, onLine.heights)
    }

    // How far north of the line every point of [encoded] lies, in metres.
    private fun northM(encoded: String) =
        decodeGpsPolyline(encoded).map { it.lat * RouteFixtures.M_PER_DEG }

    @Test
    fun with_heights_the_band_is_graded_on_the_road_under_a_red_casing() {
        val specs =
            buildGradedRejoinSpecs(
                road = gradedRoad(),
                palette = GradePalette.KAROO,
                readable = false,
                tuning =
                    EffectiveGradeMapTuning(
                        skipBands = 0,
                        simplification = ElevationSimplification.NONE,
                        climbEdge = 2.0,
                        descentEdge = null,
                    ),
                chevronBlend = 0.5,
                chevronGradeFullPct = 15.0,
                chevronChangeWindowM = 60.0,
                chevronChangeFullPctPerM = 0.2,
                chevronSpacingMaxM = 200.0,
                chevronSpacingMinM = 200.0,
                chevronWindowHalfM = 0.0,
                chevronMinSpacingM = 0.0,
                capTrimM = 10.0,
                casingCapTrimM = 12.0,
                metresPerPixel = 0.0,
            )
        assertTrue(specs.fills.map { it.colorArgb }.distinct().size > 1)
        assertTrue(specs.fills.all { it.id.startsWith("barberfish-rejoin-seg-") })
        assertEquals(specs.fills.size, specs.segmentIdSpan)
        // The polyline grid is 1.1 m at the equator.
        val onRoad = 2.4..3.6
        assertTrue(specs.fills.flatMap { northM(it.encoded) }.all { it in onRoad })
        assertTrue(northM(specs.casing).all { it in onRoad })
        assertEquals(2_000.0 - 24.0, lenM(specs.casing), 1.0)
        assertTrue(specs.chevrons.isNotEmpty())
        assertTrue(specs.chevrons.all { it.id.startsWith("barberfish-rejoin-chev-") })
        assertTrue(specs.chevrons.all { it.lat * RouteFixtures.M_PER_DEG in 2.9..3.1 })
        // The span, not the count, bounds the ids a later startMap must hide.
        val chevronIds = (0 until specs.chevronIdSpan).map { "barberfish-rejoin-chev-$it" }
        assertTrue(specs.chevrons.all { it.id in chevronIds })
    }

    @Test
    fun the_solid_band_keeps_the_black_casing() {
        val specs = buildRejoinSpecs(path, red, chevronSpacingM = 200.0, chevronMinSpacingM = 20.0)
        assertEquals(0, specs.segmentIdSpan)
        assertTrue(specs.chevrons.isNotEmpty())
        assertEquals(specs.chevrons.size, specs.chevronIdSpan)
    }
}
