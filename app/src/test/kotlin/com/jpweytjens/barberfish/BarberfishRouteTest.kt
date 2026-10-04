package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.Traversal
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.buildRouteIndex
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.matchRepeatedEdges
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real route the design was measured on. The numbers below were established off-device on the
 * GPX and confirmed against the SDK's polyline for the same route on a Karoo 3.
 */
class BarberfishRouteTest {

    private val gps = decodeGpsPolyline(BarberfishFixture.routePolyline)
    private val index = buildRouteIndex(BarberfishFixture.routePolyline, reversed = false)

    @Test
    fun the_opening_and_closing_legs_match_as_64_opposite_edges() {
        val groups = matchRepeatedEdges(gps)
        val later = groups.flatMap { it.occurrences.drop(1) }
        assertEquals(64, later.size)
        assertTrue(later.all { it.traversal == Traversal.OPPOSITE })
        assertTrue(groups.all { it.occurrences.size == 2 })
        val laterM = later.sumOf { index.cumDist[it.edge + 1] - index.cumDist[it.edge] }
        assertEquals(3135.0, laterM, 5.0)
    }

    @Test
    fun the_return_visits_form_two_stretches_around_the_roundabout() {
        val returning =
            index.visits.filter { it.depth == 1 && index.visitsOfUnit(it.unit).size == 2 }
        assertEquals(64, returning.size)
        assertEquals(30915.0, returning.minOf { it.startM }, 3.0)
        assertEquals(index.lengthM, returning.maxOf { it.endM }, 1e-6)
        // The roundabout: outbound and return take different paths, so no return visit covers
        // the gap between the two stretches.
        assertTrue(returning.none { it.startM > 31428.0 && it.endM < 31492.0 })
        val outbound = index.visits.filter { it.depth == 2 }
        assertEquals(64, outbound.size)
        assertEquals(0.0, outbound.minOf { it.startM }, 1e-6)
        assertEquals(3208.0, outbound.maxOf { it.endM }, 3.0)
    }

    @Test
    fun pieces_and_casings_are_counted_for_the_device_check() {
        val tuning =
            resolveGradeMapTuning(
                GradeMapConfig(enabled = true, syncWithSparkline = false),
                SparklineConfig(),
                GradePalette.KAROO,
            )
        val specs =
            buildGradeMapSpecs(
                routePolyline = BarberfishFixture.routePolyline,
                routeElevationPolyline = BarberfishFixture.elevationPolyline,
                palette = GradePalette.KAROO,
                readable = false,
                tuning = tuning,
                includeChevrons = false,
                routeIndex = index,
            )
        // Every piece belongs to a visit and every visit is drawable somewhere.
        assertTrue(specs.polylines.all { it.visitKey in index.visits.indices })
        assertEquals(specs.polylines.size, specs.segmentIdSpan)
        println("barberfish pieces=${specs.polylines.size} visits=${index.visits.size}")
    }
}
