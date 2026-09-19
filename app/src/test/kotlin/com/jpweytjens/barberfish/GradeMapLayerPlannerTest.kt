package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.extension.GradeMapLayerPlanner
import com.jpweytjens.barberfish.extension.LayerBatch
import com.jpweytjens.barberfish.extension.emitLayerBatches
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapLayerPlannerTest {

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()

    private fun piece(
        index: Int,
        visit: Int,
        depth: Int,
        startM: Double = index * 100.0,
        endM: Double = startM + 100.0,
        encoded: String = "fill$index",
        casing: String = "casing$index",
        color: Int = red,
    ) =
        GradeMapPolylineSpec(
            id = "barberfish-seg-$index",
            encoded = encoded,
            colorArgb = color,
            casingEncoded = casing,
            visitKey = visit,
            startM = startM,
            endM = endM,
            depth = depth,
        )

    // Out-and-back: three outbound pieces on top (depth 2), three return pieces beneath.
    private val outAndBack =
        listOf(
            piece(0, 0, 2),
            piece(1, 1, 2),
            piece(2, 2, 2),
            piece(3, 3, 1),
            piece(4, 4, 1),
            piece(5, 5, 1),
        )

    private fun LayerBatch.showIds() = effects.filterIsInstance<ShowPolyline>().map { it.id }

    private fun LayerBatch.hideIds() = effects.filterIsInstance<HidePolyline>().map { it.id }

    private fun List<LayerBatch>.allShowIds() = flatMap { it.showIds() }

    @Test
    fun first_plan_puts_every_casing_down_then_fills_by_depth_with_settles_between() {
        val batches = GradeMapLayerPlanner().plan(outAndBack, 18, 21)
        assertEquals(4, batches.size)
        val (legacy, casings, depth1, depth2) = batches
        // Nothing of ours is painted, but a previous version's whole-route casing might be.
        assertEquals(listOf("barberfish-casing"), legacy.hideIds())
        assertTrue(legacy.settleAfter)
        assertEquals(outAndBack.map { "${it.id}-casing" }, casings.showIds())
        assertTrue(casings.settleAfter)
        assertEquals(
            listOf("barberfish-seg-3", "barberfish-seg-4", "barberfish-seg-5"),
            depth1.showIds(),
        )
        assertTrue(depth1.settleAfter)
        assertEquals(
            listOf("barberfish-seg-0", "barberfish-seg-1", "barberfish-seg-2"),
            depth2.showIds(),
        )
        assertFalse(depth2.settleAfter)
        val casing = casings.effects[0] as ShowPolyline
        assertEquals("casing0", casing.encodedPolyline)
        assertEquals(21, casing.width)
        assertEquals(0xFF000000.toInt(), casing.color)
        val fill = depth2.effects[0] as ShowPolyline
        assertEquals("fill0", fill.encodedPolyline)
        assertEquals(18, fill.width)
        assertEquals(red, fill.color)
    }

    @Test
    fun five_depths_are_five_rounds() {
        val pieces = (1..5).map { piece(it, it, depth = 6 - it) }
        val batches = GradeMapLayerPlanner().plan(pieces, 18, 21)
        // Legacy hide, casings, then one round per depth.
        assertEquals(7, batches.size)
        assertEquals(
            (1..5).map { listOf("barberfish-seg-${6 - it}") },
            batches.drop(2).map { it.showIds() },
        )
        assertTrue(batches.dropLast(1).all { it.settleAfter })
        assertFalse(batches.last().settleAfter)
    }

    @Test
    fun stale_ids_are_hidden_with_their_casings_and_the_legacy_casing_before_anything_shows() {
        val planner = GradeMapLayerPlanner()
        planner.assumeStale(2)
        val batches = planner.plan(listOf(piece(0, 0, 1)), 18, 21)
        val hides = batches.first()
        assertEquals(
            setOf(
                "barberfish-seg-0",
                "barberfish-seg-0-casing",
                "barberfish-seg-1",
                "barberfish-seg-1-casing",
                "barberfish-casing",
            ),
            hides.hideIds().toSet(),
        )
        assertTrue(hides.showIds().isEmpty())
        assertTrue(hides.settleAfter)
        assertEquals(
            listOf("barberfish-seg-0-casing", "barberfish-seg-0"),
            batches.drop(1).allShowIds(),
        )
    }

    @Test
    fun hiding_a_visit_removes_its_fills_and_casings_in_place_without_a_settle() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack, 18, 21)
        val batches = planner.plan(outAndBack.drop(1), 18, 21)
        assertEquals(1, batches.size)
        assertEquals(listOf("barberfish-seg-0", "barberfish-seg-0-casing"), batches[0].hideIds())
        assertTrue(batches[0].showIds().isEmpty())
        assertFalse(batches[0].settleAfter)
    }

    @Test
    fun a_trim_re_shows_the_piece_and_its_casing_in_place() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack, 18, 21)
        val trimmed = outAndBack.toMutableList()
        trimmed[3] =
            outAndBack[3].copy(encoded = "fill3-from-350", casingEncoded = "casing3-from-350")
        val batches = planner.plan(trimmed, 18, 21)
        assertEquals(1, batches.size)
        assertEquals(listOf("barberfish-seg-3-casing", "barberfish-seg-3"), batches[0].showIds())
        assertTrue(batches[0].hideIds().isEmpty())
        assertFalse(batches[0].settleAfter)
    }

    @Test
    fun a_colour_change_is_an_in_place_update() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack, 18, 21)
        val recoloured = outAndBack.map { it.copy(colorArgb = green) }
        val batches = planner.plan(recoloured, 18, 21)
        assertEquals(1, batches.size)
        assertEquals(outAndBack.map { it.id }, batches[0].showIds())
        assertFalse(batches[0].settleAfter)
    }

    @Test
    fun a_depth_change_restacks_even_with_the_same_ids() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack, 18, 21)
        val moved = outAndBack.toMutableList()
        moved[1] = outAndBack[1].copy(depth = 1)
        val batches = planner.plan(moved, 18, 21)
        assertTrue(batches.size >= 3)
        assertEquals(
            outAndBack.flatMap { listOf(it.id, "${it.id}-casing") }.toSet(),
            batches[0].hideIds().toSet(),
        )
        assertTrue(batches[0].settleAfter)
    }

    @Test
    fun an_extent_change_with_unchanged_depth_updates_in_place() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack, 18, 21)
        val recut = outAndBack.toMutableList()
        recut[1] = outAndBack[1].copy(startM = 90.0, endM = 190.0, encoded = "fill1-recut")
        val batches = planner.plan(recut, 18, 21)
        assertEquals(1, batches.size)
        assertTrue(batches[0].hideIds().isEmpty())
        assertEquals(listOf("barberfish-seg-1"), batches[0].showIds())
        assertFalse(batches[0].settleAfter)
    }

    @Test
    fun a_new_id_restacks() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack.take(5), 18, 21)
        val batches = planner.plan(outAndBack, 18, 21)
        assertTrue(batches[0].hideIds().isNotEmpty())
        assertEquals(outAndBack.map { "${it.id}-casing" }, batches[1].showIds())
    }

    @Test
    fun clearAll_hides_everything_and_the_next_plan_restacks() {
        val planner = GradeMapLayerPlanner()
        planner.plan(outAndBack, 18, 21)
        val cleared = planner.clearAll()
        assertEquals(
            outAndBack.flatMap { listOf(it.id, "${it.id}-casing") }.toSet() + "barberfish-casing",
            cleared.single().hideIds().toSet(),
        )
        val again = planner.plan(outAndBack, 18, 21)
        assertEquals(listOf("barberfish-casing"), again[0].hideIds())
        assertEquals(outAndBack.map { "${it.id}-casing" }, again[1].showIds())
    }

    @Test
    fun the_emitter_plays_batches_in_order() {
        val events = mutableListOf<MapEffect>()
        val emitter =
            object : Emitter<MapEffect> {
                override fun onNext(t: MapEffect) {
                    events += t
                }

                override fun onError(t: Throwable) {}

                override fun onComplete() {}

                override fun setCancellable(cancellable: () -> Unit) {}

                override fun cancel() {}
            }
        val batches = GradeMapLayerPlanner().plan(outAndBack, 18, 21)
        runBlocking { emitLayerBatches(emitter, batches, settleMs = 0) }
        assertEquals(batches.flatMap { it.effects }, events)
    }
}
