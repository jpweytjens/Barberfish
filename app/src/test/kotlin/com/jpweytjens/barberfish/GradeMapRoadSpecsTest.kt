package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.RouteFixtures.M_PER_DEG
import com.jpweytjens.barberfish.RouteFixtures.point
import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import com.jpweytjens.barberfish.datatype.shared.EffectiveGradeMapTuning
import com.jpweytjens.barberfish.datatype.shared.GradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.RideVisibility
import com.jpweytjens.barberfish.datatype.shared.alignChords
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.buildRouteIndex
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.latLngDistanceM
import com.jpweytjens.barberfish.datatype.shared.placeChevronsByCadence
import com.jpweytjens.barberfish.datatype.shared.selectChevrons
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [buildGradeMapSpecs] with a matched road: the band and its chevrons move onto it. */
class GradeMapRoadSpecsTest {
    // 2 km east along y = 0, as the Karoo hands it over (rounded to the polyline grid).
    private val routePolyline = encodeGpsPolyline(RouteFixtures.straight(2000.0, 50.0))
    private val saved = decodeGpsPolyline(routePolyline)

    // Two climbs and two flats, so the band has several pieces.
    private val elevationPolyline =
        RouteFixtures.encodeElevation(
            listOf(0f to 0f, 500f to 40f, 1000f to 40f, 1500f to 80f, 2000f to 80f)
        )

    private val tuning =
        EffectiveGradeMapTuning(
            skipBands = 0,
            simplification = ElevationSimplification.NONE,
            climbEdge = -1000.0,
            descentEdge = 1000.0,
        )

    private fun north(points: List<LatLng>, m: Double) = points.map {
        it.copy(lat = it.lat + m / M_PER_DEG)
    }

    // Every chord of [points] following a road 3 m to the north.
    private fun onRoad(points: List<LatLng>): MatchedRoadLine {
        val cum = cumulativeDistancesM(points)
        return MatchedRoadLine(points, cum)
            .with(0, alignChords(points, cum, points.indices, north(points, 3.0)))
    }

    private fun specsFor(line: MatchedRoadLine?, reversed: Boolean = false): GradeMapSpecs =
        buildGradeMapSpecs(
            routePolyline = routePolyline,
            routeElevationPolyline = elevationPolyline,
            palette = GradePalette.KAROO,
            readable = true,
            tuning = tuning,
            chevronSpacingMaxM = 60.0,
            chevronSpacingMinM = 60.0,
            chevronWindowHalfM = 12.0,
            reversed = reversed,
            matchedRoadLine = line,
        )

    // Metres north of y = 0, to the 0.6 m the polyline grid allows.
    private fun assertNorth(expectedM: Double, encoded: String) {
        val points = decodeGpsPolyline(encoded)
        assertTrue(points.size >= 2)
        points.forEach { assertEquals(expectedM, it.lat * M_PER_DEG, 0.6) }
    }

    @Test
    fun the_fixture_has_several_pieces_and_chevrons() {
        val specs = specsFor(null)
        assertTrue(specs.polylines.size >= 3)
        assertTrue(specs.chevrons.size >= 10)
        assertEquals(saved.size - 1, onRoad(saved).followingChords)
    }

    @Test
    fun an_empty_matched_line_changes_nothing() {
        val plain = specsFor(null)
        val empty = specsFor(MatchedRoadLine(saved, cumulativeDistancesM(saved)))
        assertEquals(plain.polylines, empty.polylines)
        assertEquals(plain.chevrons, empty.chevrons)
    }

    @Test
    fun a_line_built_for_another_route_is_ignored() {
        val other =
            onRoad(decodeGpsPolyline(encodeGpsPolyline(RouteFixtures.straight(1900.0, 50.0))))
        val specs = specsFor(other)
        assertEquals(specsFor(null), specs)
    }

    @Test
    fun the_band_and_its_casing_follow_the_road() {
        val plain = specsFor(null).polylines
        val road = specsFor(onRoad(saved)).polylines
        assertEquals(plain.size, road.size)
        road.forEachIndexed { i, spec ->
            assertNorth(3.0, spec.encoded)
            assertNorth(3.0, spec.casingEncoded)
            assertNorth(0.0, plain[i].encoded)
            assertEquals(
                plain[i].copy(encoded = spec.encoded, casingEncoded = spec.casingEncoded),
                spec,
            )
        }
    }

    @Test
    fun the_candidate_chevrons_sit_on_the_road_and_are_otherwise_the_same() {
        val plain = specsFor(null).chevrons
        val road = specsFor(onRoad(saved)).chevrons
        assertEquals(plain.size, road.size)
        road.zip(plain).forEach { (onRoad, onLine) ->
            assertEquals(3.0, (onRoad.lat - onLine.lat) * M_PER_DEG, 0.05)
            assertEquals(onLine.lng, onRoad.lng, 1e-9)
            assertTrue(abs(onLine.bearingDeg - onRoad.bearingDeg) < 1f)
            assertEquals(onLine.copy(lat = onRoad.lat, bearingDeg = onRoad.bearingDeg), onRoad)
        }
    }

    @Test
    fun selection_measures_to_the_road_position() {
        val gps = decodeGpsPolyline(routePolyline)
        val index = buildRouteIndex(gps, cumulativeDistancesM(gps))
        val plain = specsFor(null).chevrons
        val road = specsFor(onRoad(saved)).chevrons
        // 100 m north of the first mark on the line, so 97 m from where the road puts it.
        val first = plain.first()
        val rider = LatLng(first.lat + 100.0 / M_PER_DEG, first.lng)
        fun selected(candidates: List<ClimbChevronSpec>) =
            selectChevrons(candidates, RideVisibility(index), 0.0, rider, 98.5, 1e6)
        assertTrue(selected(plain).isEmpty())
        assertEquals(listOf(road.first()), selected(road))
    }

    @Test
    fun a_chevron_takes_the_roads_bearing() {
        // The handed-over line cuts a bend that the road takes through (150, 30).
        val line = (0..3).map { point(it * 100.0, 0.0) }
        val bend =
            listOf(
                point(0.0, 0.0),
                point(100.0, 0.0),
                point(150.0, 30.0),
                point(200.0, 0.0),
                point(300.0, 0.0),
            )
        val cum = cumulativeDistancesM(line)
        val road = MatchedRoadLine(line, cum).with(0, alignChords(line, cum, line.indices, bend))
        // One mark, half of [spacingM] in.
        fun onlyMark(spacingM: Double, reversed: Boolean) =
            placeChevronsByCadence(
                    gps = if (reversed) line.asReversed() else line,
                    cumDist = cum,
                    gradeAtM = { 0.0 },
                    changeAtM = { 0.0 },
                    alpha = 0.0,
                    gradeFullPct = 15.0,
                    changeFullPctPerM = 0.2,
                    spacingMaxM = spacingM,
                    spacingMinM = spacingM,
                    collisionRadiusM = 0.0,
                    windowHalfM = 10.0,
                    positionAt = { road.positionAt(it, reversed) },
                )
                .single()
        val mark = onlyMark(250.0, reversed = false)
        assertEquals(125.0, mark.distanceM, 0.0)
        assertTrue(latLngDistanceM(point(125.0, 15.0), LatLng(mark.lat, mark.lng)) < 0.01)
        // Up the first leg of the bend: 50 m east for 30 m north.
        assertEquals(59.0, mark.bearingDeg.toDouble(), 1.0)
        // Ridden the other way the same spot is 175 m in, and the mark points back down the leg.
        val back = onlyMark(350.0, reversed = true)
        assertEquals(175.0, back.distanceM, 0.0)
        assertTrue(latLngDistanceM(point(125.0, 15.0), LatLng(back.lat, back.lng)) < 0.01)
        assertEquals(239.0, back.bearingDeg.toDouble(), 1.0)
    }

    @Test
    fun a_reversed_route_follows_the_same_road() {
        val plain = specsFor(null, reversed = true)
        val road = specsFor(onRoad(saved), reversed = true)
        assertEquals(plain.polylines.size, road.polylines.size)
        road.polylines.forEachIndexed { i, spec ->
            assertNorth(3.0, spec.encoded)
            assertNorth(3.0, spec.casingEncoded)
            // Still in ride order: each piece starts where the chord piece starts.
            val start = decodeGpsPolyline(spec.encoded).first()
            val plainStart = decodeGpsPolyline(plain.polylines[i].encoded).first()
            assertEquals(plainStart.lng * M_PER_DEG, start.lng * M_PER_DEG, 1.2)
            assertEquals(plain.polylines[i].id, spec.id)
            assertEquals(plain.polylines[i].startM, spec.startM, 0.0)
        }
        // The ride starts at the saved line's end.
        assertEquals(
            2000.0,
            decodeGpsPolyline(road.polylines.first().encoded).first().lng * M_PER_DEG,
            1.2,
        )
        val onLine = plain.chevrons.first()
        val onRoad = road.chevrons.first()
        assertEquals(3.0, (onRoad.lat - onLine.lat) * M_PER_DEG, 0.05)
        assertEquals(onLine.lng, onRoad.lng, 1e-9)
    }
}
