package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.WIND_SOCK_ID
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol

/**
 * Owns the one wind sock symbol on the map. A ShowSymbols for an id the map already has updates it
 * in place, so every fix re-sends the symbol; a hide goes out once when the sock is withdrawn
 * (calm, switch off, stream lost). Never hide and show the id in one emission.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine; not thread-safe.
 */
internal class WindSockController(private val id: String = WIND_SOCK_ID) {
    private var shown = false

    fun emit(emitter: Emitter<MapEffect>, symbol: Symbol.Icon?) {
        if (symbol == null) {
            if (shown) {
                emitter.onNext(HideSymbols(listOf(id)))
                shown = false
            }
            return
        }
        emitter.onNext(ShowSymbols(listOf(symbol)))
        shown = true
    }

    /**
     * Hide unconditionally: the map keeps symbols across extension restarts, so a fresh controller
     * cannot know whether a dead generation left the sock painted.
     */
    fun clear(emitter: Emitter<MapEffect>) {
        emitter.onNext(HideSymbols(listOf(id)))
        shown = false
    }
}
