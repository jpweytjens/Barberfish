package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.KAROO_HEIGHT_STEP_M
import com.jpweytjens.barberfish.datatype.shared.ROAD_HEIGHT_FLOOR_M
import com.jpweytjens.barberfish.datatype.shared.STOCK_HEIGHT_STEP_M
import com.jpweytjens.barberfish.datatype.shared.karooHeightM
import com.jpweytjens.barberfish.datatype.shared.roadHeights
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadHeightsTest {
    // The engine's value that karooHeightM turns into [heightM], so the tests go through the
    // conversion.
    private fun reported(heightM: Double): Double =
        ROAD_HEIGHT_FLOOR_M +
            (heightM - ROAD_HEIGHT_FLOOR_M) * (STOCK_HEIGHT_STEP_M / KAROO_HEIGHT_STEP_M)

    private fun heightsOf(vararg heightsM: Double) = heightsM.map(::reported)

    @Test
    fun the_conversion_follows_from_its_three_sources() {
        assertEquals(3500.0, karooHeightM(0.0), 1e-9)
        assertEquals(-500.0, karooHeightM(-500.0), 1e-9)
        assertEquals(300.0, karooHeightM(-400.0), 1e-9)
        assertEquals(8.0, KAROO_HEIGHT_STEP_M / STOCK_HEIGHT_STEP_M, 0.0)
    }

    @Test
    fun samples_sit_every_30_m_and_the_last_at_the_roads_end() {
        // 4 values, road 80 m -> distances 0, 30, 60, 80
        val heights = roadHeights(heightsOf(100.0, 101.0, 102.0, 103.0), 80.0)!!
        assertEquals(listOf(0f, 30f, 60f, 80f), heights.map { it.first })
        assertEquals(listOf(100f, 101f, 102f, 103f), heights.map { it.second })
    }

    @Test
    fun a_count_with_a_sample_at_the_roads_end_fits() {
        // 0, 30, 60 and the end at 80: three intervals span 90 m
        assertNotNull(roadHeights(heightsOf(100.0, 101.0, 102.0, 103.0), 80.0))
    }

    @Test
    fun a_count_without_a_sample_at_the_roads_end_fits() {
        // 0, 30, 60, 90 on a 100 m road
        val heights = roadHeights(heightsOf(100.0, 101.0, 102.0, 103.0), 100.0)!!
        assertEquals(listOf(0f, 30f, 60f, 90f), heights.map { it.first })
    }

    @Test
    fun far_too_few_samples_for_the_road_are_no_heights() {
        // Three samples span 60 m of a 300 m road
        assertNull(roadHeights(heightsOf(100.0, 101.0, 102.0), 300.0))
    }

    @Test
    fun far_too_many_samples_for_the_road_are_no_heights() {
        // Ten samples span 270 m of a 60 m road
        assertNull(roadHeights(List(10) { reported(100.0) }, 60.0))
    }

    @Test
    fun one_wild_junction_is_dropped_with_the_ramp_to_it() {
        // True heights 100 m flat, one sample at 8200 m with a triangle ramp of two samples each
        // side.
        val samples = MutableList(60) { 100.0 }
        listOf(8200.0, 5500.0, 2800.0).forEachIndexed { i, wild ->
            samples[30 + i] = wild
            samples[30 - i] = wild
        }
        val heights = roadHeights(samples.map(::reported), 59 * 30.0)!!
        assertTrue(heights.isNotEmpty())
        heights.forEach { assertEquals(100f, it.second, 1e-3f) }
        heights
            .zipWithNext()
            .filter { (a, b) -> b.first - a.first <= 30f }
            .forEach { (a, b) ->
                assertTrue(abs(b.second - a.second) <= 0.5f * (b.first - a.first))
            }
    }

    @Test
    fun a_steep_but_real_ramp_is_kept() {
        // 20%: 6 m per 30 m, nothing dropped
        val heights = roadHeights(heightsOf(0.0, 6.0, 12.0, 18.0, 24.0), 120.0)!!
        assertEquals(5, heights.size)
    }

    @Test
    fun a_mostly_wild_profile_has_no_heights() {
        // More than a quarter dropped
        val flat = MutableList(20) { reported(100.0) }
        flat[10] = reported(8200.0)
        assertNotNull(roadHeights(flat, 19 * 30.0))
        flat[4] = reported(8200.0)
        flat[16] = reported(8200.0)
        assertNull(roadHeights(flat, 19 * 30.0))
    }

    @Test
    fun fewer_than_two_surviving_samples_is_no_heights() {
        assertNull(roadHeights(heightsOf(100.0), 0.0))
        assertNull(roadHeights(heightsOf(100.0, 8200.0), 30.0))
    }

    @Test
    fun an_empty_list_is_no_heights() {
        assertNull(roadHeights(emptyList(), 100.0))
    }
}
