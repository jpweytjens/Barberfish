package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol

/**
 * Tracks previously-emitted chevron specs so each call to [emit] only sends `HideSymbols` for ids
 * that were removed or changed and `ShowSymbols` for ids that are new or changed. Chevrons are
 * drawn above extension polylines (symbol layers 16/18 vs polyline layer 13) so the gradient fill
 * is always visible beneath them.
 *
 * The rideapp sometimes fails to process a single HideSymbols call (IPC reordering), so removed id
 * sets are re-issued as hides on the next [LOST_HIDE_REISSUE_ROUNDS] emits — minus any id that has
 * since come back — instead of hiding every previous id on every rebuild (which blanked all
 * chevrons for a frame between hide and show). Changed ids are hidden then re-shown in the same
 * emit rather than relying on ShowSymbols updating an existing id in place, which is unverified on
 * the rideapp.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine — not thread-safe.
 */
internal class GradeMapChevronController {
    private var previous: Map<String, ClimbChevronSpec> = emptyMap()

    /** Removed id sets paired with how many more emits they should be re-hidden on. */
    private var recentlyRemoved: List<Pair<Set<String>, Int>> = emptyList()

    fun emit(emitter: Emitter<MapEffect>, specs: List<ClimbChevronSpec>) {
        val current = specs.associateBy { it.id }
        val removed = previous.keys - current.keys
        val changed = current.keys.filterTo(mutableSetOf()) { id ->
            previous[id]?.let { it != current[id] } ?: false
        }
        val reissued = recentlyRemoved.flatMapTo(mutableSetOf()) { it.first } - current.keys
        val hideIds = removed + changed + reissued
        if (hideIds.isNotEmpty()) {
            emitter.onNext(HideSymbols(hideIds.toList()))
        }
        val showSpecs = specs.filter { it.id !in previous || it.id in changed }
        if (showSpecs.isNotEmpty()) {
            val icons =
                showSpecs.map { spec ->
                    Symbol.Icon(
                        id = spec.id,
                        lat = spec.lat,
                        lng = spec.lng,
                        iconRes = gradeChevronDrawable(spec.colorArgb),
                        orientation = spec.bearingDeg,
                    )
                }
            emitter.onNext(ShowSymbols(icons))
        }
        recentlyRemoved = recentlyRemoved.mapNotNull { (ids, rounds) ->
            if (rounds > 1) ids to rounds - 1 else null
        } + if (removed.isNotEmpty()) listOf(removed to LOST_HIDE_REISSUE_ROUNDS) else emptyList()
        previous = current
    }

    fun clearAll(emitter: Emitter<MapEffect>) {
        val ids = previous.keys + recentlyRemoved.flatMap { it.first }
        if (ids.isNotEmpty()) {
            emitter.onNext(HideSymbols(ids.toList()))
        }
        previous = emptyMap()
        recentlyRemoved = emptyList()
    }

    private companion object {
        const val LOST_HIDE_REISSUE_ROUNDS = 3
    }
}
