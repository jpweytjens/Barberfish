package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.WIND_SOCK_ID
import com.jpweytjens.barberfish.extension.WindSockController
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindSockControllerTest {

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

    private fun sock(orientation: Float = 90f) =
        Symbol.Icon(WIND_SOCK_ID, 50.0, 4.0, R.drawable.ic_wind_sock_3, orientation)

    @Test
    fun a_symbol_is_shown_and_updated_in_place() {
        val controller = WindSockController()
        val fake = FakeEmitter()
        controller.emit(fake, sock(90f))
        controller.emit(fake, sock(120f))
        assertEquals(2, fake.events.filterIsInstance<ShowSymbols>().size)
        assertTrue(fake.events.filterIsInstance<HideSymbols>().isEmpty())
        val last = fake.events.last() as ShowSymbols
        assertEquals(120f, (last.symbols.single() as Symbol.Icon).orientation, 0.001f)
    }

    @Test
    fun calm_after_shown_hides_once() {
        val controller = WindSockController()
        val fake = FakeEmitter()
        controller.emit(fake, sock())
        controller.emit(fake, null)
        controller.emit(fake, null)
        val hides = fake.events.filterIsInstance<HideSymbols>()
        assertEquals(1, hides.size)
        assertEquals(listOf(WIND_SOCK_ID), hides.single().symbolIds)
    }

    @Test
    fun calm_when_nothing_is_shown_emits_nothing() {
        val controller = WindSockController()
        val fake = FakeEmitter()
        controller.emit(fake, null)
        assertTrue(fake.events.isEmpty())
    }

    @Test
    fun clear_hides_even_when_unsure_what_a_dead_generation_left() {
        val controller = WindSockController()
        val fake = FakeEmitter()
        controller.clear(fake)
        assertEquals(1, fake.events.filterIsInstance<HideSymbols>().size)
    }
}
