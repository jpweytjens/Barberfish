package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.extension.GradeMapController
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapControllerTest {

    private class FakeEmitter : Emitter<MapEffect> {
        val events = mutableListOf<MapEffect>()

        override fun onNext(t: MapEffect) {
            events += t
        }

        override fun onError(t: Throwable) {}

        override fun onComplete() {}

        override fun setCancellable(cancellable: () -> Unit) {}

        override fun cancel() {}
    }

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val fillW = 18
    private val casingW = 21
    private val casingId = "barberfish-casing"
    private val route = "route"

    private fun controller() = GradeMapController(settleMs = 0)

    private fun GradeMapController.emit(fake: FakeEmitter, specs: List<GradeMapPolylineSpec>) =
        runBlocking {
            emit(fake, route, specs, fillW, casingW)
        }

    private fun FakeEmitter.showIds() = events.filterIsInstance<ShowPolyline>().map { it.id }

    private fun FakeEmitter.hideIds() = events.filterIsInstance<HidePolyline>().map { it.id }

    @Test
    fun first_emit_puts_the_casing_down_before_the_fills() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(GradeMapPolylineSpec("a", "xyz", red)))
        assertEquals(listOf(casingId, "a"), fake.showIds())
        val casing = fake.events[0] as ShowPolyline
        assertEquals(route, casing.encodedPolyline)
        assertEquals(casingW, casing.width)
        assertEquals(0xFF000000.toInt(), casing.color)
        val fill = fake.events[1] as ShowPolyline
        assertEquals("xyz", fill.encodedPolyline)
        assertEquals(red, fill.color)
        assertEquals(fillW, fill.width)
    }

    @Test
    fun fills_are_shown_in_order() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.emit(
            fake,
            listOf(
                GradeMapPolylineSpec("a", "xyz", red),
                GradeMapPolylineSpec("b", "pqr", green),
            ),
        )
        assertEquals(listOf(casingId, "a", "b"), fake.showIds())
    }

    @Test
    fun later_emits_hide_removed_fills_and_update_the_casing_in_place() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.emit(
            fake,
            listOf(
                GradeMapPolylineSpec("a", "xyz", red),
                GradeMapPolylineSpec("b", "pqr", green),
            ),
        )
        fake.events.clear()
        controller.emit(
            fake,
            listOf(
                GradeMapPolylineSpec("a", "xyz", red),
                GradeMapPolylineSpec("c", "stu", red),
            ),
        )
        assertEquals(listOf("b"), fake.hideIds())
        assertEquals(listOf(casingId, "a", "c"), fake.showIds())
    }

    @Test
    fun assumeStale_first_emit_hides_the_whole_range_before_the_casing() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.assumeStale(2)
        controller.emit(fake, listOf(GradeMapPolylineSpec("barberfish-seg-0", "xyz", red)))
        // Every stale fill goes, claimed or not: a stale fill re-shown in place would keep
        // its position beneath the casing added after it. The claimed id comes back as a
        // fresh show above the casing.
        assertEquals(listOf("barberfish-seg-0", "barberfish-seg-1"), fake.hideIds().sorted())
        assertEquals(listOf(casingId, "barberfish-seg-0"), fake.showIds())
        assertTrue(
            fake.events.indexOfLast { it is HidePolyline } <
                fake.events.indexOfFirst { it is ShowPolyline }
        )
    }

    @Test
    fun assumeStale_zero_span_first_emit_is_just_the_casing() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.assumeStale(0)
        controller.emit(fake, emptyList())
        assertEquals(listOf(casingId), fake.showIds())
        assertTrue(fake.hideIds().isEmpty())
    }

    @Test
    fun assumeStale_clearAll_hides_the_range_and_the_casing() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.assumeStale(2)
        controller.clearAll(fake)
        assertEquals(
            setOf("barberfish-seg-0", "barberfish-seg-1", casingId),
            fake.hideIds().toSet(),
        )
    }

    @Test
    fun clearAll_then_emit_starts_a_fresh_generation() {
        val controller = controller()
        val fake = FakeEmitter()
        controller.emit(
            fake,
            listOf(
                GradeMapPolylineSpec("a", "xyz", red),
                GradeMapPolylineSpec("b", "pqr", green),
            ),
        )
        fake.events.clear()
        controller.clearAll(fake)
        assertEquals(setOf("a", "b", casingId), fake.hideIds().toSet())

        fake.events.clear()
        controller.emit(fake, listOf(GradeMapPolylineSpec("a", "xyz", red)))
        // Nothing is known to be painted, so no hides; the casing goes down first again.
        assertTrue(fake.hideIds().isEmpty())
        assertEquals(listOf(casingId, "a"), fake.showIds())
    }

    @Test
    fun casing_id_and_stale_ids_are_configurable() {
        val controller = GradeMapController(casingId = "other-casing", settleMs = 0)
        val fake = FakeEmitter()
        controller.assumeStale(setOf("other-fill"))
        runBlocking {
            controller.emit(
                fake,
                route,
                listOf(GradeMapPolylineSpec("other-fill", "xyz", red)),
                fillW,
                casingW,
            )
        }
        assertEquals(listOf("other-fill"), fake.hideIds())
        assertEquals(listOf("other-casing", "other-fill"), fake.showIds())
        fake.events.clear()
        controller.clearAll(fake)
        assertEquals(setOf("other-fill", "other-casing"), fake.hideIds().toSet())
    }
}
