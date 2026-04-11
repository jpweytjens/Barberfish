package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol

/**
 * Tracks previously-emitted chevron symbol IDs so each call to [emit] only sends
 * `HideSymbols` for IDs that are no longer wanted and `ShowSymbols` for the new set.
 * Chevrons are drawn above extension polylines (symbol layers 16/18 vs polyline layer
 * 13) so the gradient fill is always visible beneath them.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine — not
 * thread-safe.
 */
internal class ClimbChevronController {
    private var previousIds: Set<String> = emptySet()

    fun emit(emitter: Emitter<MapEffect>, specs: List<ClimbChevronSpec>) {
        val newIds = specs.mapTo(mutableSetOf()) { it.id }
        val removed = previousIds - newIds
        if (removed.isNotEmpty()) {
            emitter.onNext(HideSymbols(removed.toList()))
        }
        if (specs.isNotEmpty()) {
            val icons = specs.map { spec ->
                Symbol.Icon(
                    id = spec.id,
                    lat = spec.lat,
                    lng = spec.lng,
                    iconRes = R.drawable.ic_climber_chevron,
                    orientation = spec.bearingDeg,
                )
            }
            emitter.onNext(ShowSymbols(icons))
        }
        previousIds = newIds
    }

    fun clearAll(emitter: Emitter<MapEffect>) {
        if (previousIds.isNotEmpty()) {
            emitter.onNext(HideSymbols(previousIds.toList()))
            previousIds = emptySet()
        }
    }
}
