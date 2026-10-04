package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.chevronIntensity
import com.jpweytjens.barberfish.datatype.shared.chevronSpacingM
import com.jpweytjens.barberfish.datatype.shared.latLngDistanceM
import com.jpweytjens.barberfish.datatype.shared.placeChevronsByCadence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChevronPlacementTest {
    // A straight 1 km due-east route: 11 points 100 m apart at the equator.
    private val gps = (0..10).map { LatLng(0.0, it * 0.000898315) } // ~100 m per step at lat 0
    private val cum =
        DoubleArray(gps.size).also { c ->
            for (i in 1 until gps.size) c[i] = c[i - 1] + latLngDistanceM(gps[i - 1], gps[i])
        }

    @Test
    fun intensity_blends_grade_and_change() {
        // alpha 0 -> grade only; alpha 1 -> change only; gradeFull 15, changeFull 0.3
        assertEquals(10.0 / 15.0, chevronIntensity(10.0, 0.0, 0.0, 15.0, 0.3), 1e-9)
        assertEquals(0.2 / 0.3, chevronIntensity(0.0, 0.2, 1.0, 15.0, 0.3), 1e-9)
        assertEquals(
            0.5 * (10.0 / 15.0) + 0.5 * (0.2 / 0.3),
            chevronIntensity(10.0, 0.2, 0.5, 15.0, 0.3),
            1e-9,
        )
    }

    @Test
    fun intensity_clamps_to_unit() {
        assertEquals(1.0, chevronIntensity(99.0, 0.0, 0.0, 15.0, 0.3), 1e-9)
        assertEquals(0.0, chevronIntensity(0.0, 0.0, 0.5, 15.0, 0.3), 1e-9)
    }

    @Test
    fun spacing_interpolates_max_to_min() {
        assertEquals(200.0, chevronSpacingM(0.0, 200.0, 40.0), 1e-9) // sparse ceiling
        assertEquals(40.0, chevronSpacingM(1.0, 200.0, 40.0), 1e-9) // dense floor
        assertEquals(120.0, chevronSpacingM(0.5, 200.0, 40.0), 1e-9)
    }

    @Test
    fun flat_route_still_emits_at_the_sparse_spacing() {
        val placed =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 0.0 },
                changeAtM = { 0.0 },
                alpha = 0.5,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        assertTrue("flat road is not bare", placed.isNotEmpty())
        // intensity 0 -> spacing 200 m -> marks ~200 m apart on a 1 km route
        for (i in 1 until placed.size) {
            assertEquals(200.0, placed[i].distanceM - placed[i - 1].distanceM, 1e-6)
        }
    }

    @Test
    fun steady_steep_uses_grade_only_spacing() {
        val placed =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 12.0 },
                changeAtM = { 0.0 },
                alpha = 0.5,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        // intensity = 0.5 * (12/15) = 0.4 -> spacing = 200 + (40-200)*0.4 = 136
        for (i in 1 until placed.size) {
            assertEquals(136.0, placed[i].distanceM - placed[i - 1].distanceM, 1e-6)
        }
    }

    @Test
    fun change_tightens_relative_to_the_same_grade_held_steady() {
        val steady =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 8.0 },
                changeAtM = { 0.0 },
                alpha = 1.0,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        val kicking =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 8.0 },
                changeAtM = { 0.3 },
                alpha = 1.0,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        assertTrue("kick packs more marks than the steady grade", kicking.size > steady.size)
    }

    @Test
    fun alpha_zero_ignores_change_alpha_one_ignores_grade() {
        val gradeOnly =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 15.0 },
                changeAtM = { 0.6 },
                alpha = 0.0,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        // alpha 0, grade at full -> intensity 1 -> spacing 40
        for (i in 1 until gradeOnly.size) {
            assertEquals(40.0, gradeOnly[i].distanceM - gradeOnly[i - 1].distanceM, 1e-6)
        }
        val changeOnly =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 15.0 },
                changeAtM = { 0.0 },
                alpha = 1.0,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        // alpha 1, change 0 -> intensity 0 -> spacing 200
        for (i in 1 until changeOnly.size) {
            assertEquals(200.0, changeOnly[i].distanceM - changeOnly[i - 1].distanceM, 1e-6)
        }
    }

    @Test
    fun spacing_scales_linearly_with_the_metre_ceiling() {
        val tight =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 0.0 },
                changeAtM = { 0.0 },
                alpha = 0.5,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 100.0,
                spacingMinM = 20.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        val loose =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 0.0 },
                changeAtM = { 0.0 },
                alpha = 0.5,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 40.0,
                collisionRadiusM = 0.0,
                windowHalfM = 20.0,
            )
        assertTrue("doubling the ceiling roughly halves the count", tight.size > loose.size)
    }

    @Test
    fun collision_radius_drops_near_neighbours() {
        val dense =
            placeChevronsByCadence(
                gps,
                cum,
                gradeAtM = { 30.0 },
                changeAtM = { 0.0 },
                alpha = 0.0,
                gradeFullPct = 15.0,
                changeFullPctPerM = 0.3,
                spacingMaxM = 200.0,
                spacingMinM = 5.0,
                collisionRadiusM = 90.0,
                windowHalfM = 20.0,
            )
        for (i in 1 until dense.size) {
            assertTrue(
                "no two marks closer than the collision radius",
                latLngDistanceM(
                    LatLng(dense[i - 1].lat, dense[i - 1].lng),
                    LatLng(dense[i].lat, dense[i].lng),
                ) >= 90.0 - 1e-6,
            )
        }
    }
}
