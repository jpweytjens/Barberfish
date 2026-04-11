package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ClimbPolylineSpec
import com.jpweytjens.barberfish.extension.ClimbMapController
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbMapControllerTest {

    private class FakeEmitter : Emitter<MapEffect> {
        val events = mutableListOf<MapEffect>()
        override fun onNext(t: MapEffect) { events += t }
        override fun onError(t: Throwable) {}
        override fun onComplete() {}
        override fun setCancellable(cancellable: () -> Unit) {}
        override fun cancel() {}
    }

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val fillW = 8

    @Test fun emit_single_spec_outputs_one_show_polyline() {
        val controller = ClimbMapController()
        val fake = FakeEmitter()
        controller.emit(
            emitter = fake,
            specs = listOf(ClimbPolylineSpec("a", "xyz", red)),
            fillWidth = fillW,
        )
        assertEquals(1, fake.events.size)
        val fill = fake.events[0] as ShowPolyline
        assertEquals("a", fill.id)
        assertEquals("xyz", fill.encodedPolyline)
        assertEquals(red, fill.color)
        assertEquals(fillW, fill.width)
    }

    @Test fun emit_two_specs_outputs_in_order() {
        val controller = ClimbMapController()
        val fake = FakeEmitter()
        controller.emit(
            emitter = fake,
            specs = listOf(
                ClimbPolylineSpec("a", "xyz", red),
                ClimbPolylineSpec("b", "pqr", green),
            ),
            fillWidth = fillW,
        )
        val ids = fake.events.map { (it as ShowPolyline).id }
        assertEquals(listOf("a", "b"), ids)
    }

    @Test fun second_emit_hides_removed_specs() {
        val controller = ClimbMapController()
        val fake = FakeEmitter()
        controller.emit(
            emitter = fake,
            specs = listOf(
                ClimbPolylineSpec("a", "xyz", red),
                ClimbPolylineSpec("b", "pqr", green),
            ),
            fillWidth = fillW,
        )
        fake.events.clear()
        controller.emit(
            emitter = fake,
            specs = listOf(
                ClimbPolylineSpec("a", "xyz", red),
                ClimbPolylineSpec("c", "stu", red),
            ),
            fillWidth = fillW,
        )
        val hides = fake.events.filterIsInstance<HidePolyline>().map { it.id }
        assertEquals(listOf("b"), hides)
        val shows = fake.events.filterIsInstance<ShowPolyline>().map { it.id }
        assertTrue(shows.containsAll(listOf("a", "c")))
    }

    @Test fun clearAll_hides_all_previous_ids_then_empty_emit_is_noop() {
        val controller = ClimbMapController()
        val fake = FakeEmitter()
        controller.emit(
            emitter = fake,
            specs = listOf(
                ClimbPolylineSpec("a", "xyz", red),
                ClimbPolylineSpec("b", "pqr", green),
            ),
            fillWidth = fillW,
        )
        fake.events.clear()
        controller.clearAll(fake)
        val hidden = fake.events.filterIsInstance<HidePolyline>().map { it.id }.toSet()
        assertEquals(setOf("a", "b"), hidden)

        fake.events.clear()
        controller.emit(fake, specs = emptyList(), fillWidth = fillW)
        assertTrue(fake.events.isEmpty())
    }
}
