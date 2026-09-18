package com.jpweytjens.barberfish.extension

import androidx.annotation.DrawableRes
import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import com.jpweytjens.barberfish.datatype.shared.gradeMapChevronId
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
    private var lastIconRes: Int? = null

    /** Removed id sets paired with how many more emits they should be re-hidden on. */
    private var recentlyRemoved: List<Pair<Set<String>, Int>> = emptyList()

    fun emit(
        emitter: Emitter<MapEffect>,
        specs: List<ClimbChevronSpec>,
        @DrawableRes iconRes: Int,
    ) {
        val current = specs.associateBy { it.id }
        val removed = previous.keys - current.keys
        val changed =
            current.keys.filterTo(mutableSetOf()) { id ->
                previous[id]?.let { it != current[id] } ?: false
            }
        // A new icon (palette change) re-shows every kept chevron in place, without a hide:
        // ShowSymbols replaces an existing id, and a hide in the same batch would race it.
        val iconChanged = lastIconRes != null && lastIconRes != iconRes
        val restyled = if (iconChanged) current.keys - changed else emptySet()
        val reissued = recentlyRemoved.flatMapTo(mutableSetOf()) { it.first } - current.keys
        // Redrawn stale ids ([assumeStale] sentinels) are re-shown without a preceding hide:
        // ShowSymbols replaces an existing id in place, while a hide in the same batch races
        // the show in the rideapp's async symbol processing and blanks the chevron
        // (observed on-device, 2026-08-22).
        val redrawnStale =
            current.keys.filterTo(mutableSetOf()) { id -> previous[id]?.lat?.isNaN() == true }
        val hideIds = removed + (changed - redrawnStale) + reissued
        if (hideIds.isNotEmpty()) {
            emitter.onNext(HideSymbols(hideIds.toList()))
        }
        val showSpecs = specs.filter { it.id !in previous || it.id in changed || it.id in restyled }
        if (showSpecs.isNotEmpty()) {
            val icons = showSpecs.map { spec ->
                Symbol.Icon(
                    id = spec.id,
                    lat = spec.lat,
                    lng = spec.lng,
                    iconRes = iconRes,
                    orientation = spec.bearingDeg,
                )
            }
            emitter.onNext(ShowSymbols(icons))
        }
        recentlyRemoved =
            recentlyRemoved.mapNotNull { (ids, rounds) ->
                if (rounds > 1) ids to rounds - 1 else null
            } +
                if (removed.isNotEmpty()) listOf(removed to LOST_HIDE_REISSUE_ROUNDS)
                else emptyList()
        previous = current
        lastIconRes = iconRes
    }

    /**
     * Hides every tracked chevron whose [ClimbChevronSpec.distanceM] is behind [progressM]. A
     * hide-only batch: nothing here can be reordered against a show. Hidden ids join
     * [recentlyRemoved] so the next [emit] re-issues the hide if the rideapp dropped it. Stale
     * sentinels carry a NaN distance, which never compares below [progressM]; they stay for the
     * next [emit] to resolve.
     */
    fun hidePassed(emitter: Emitter<MapEffect>, progressM: Double) {
        val passed = previous.filterValues { it.distanceM < progressM }.keys
        if (passed.isEmpty()) return
        emitter.onNext(HideSymbols(passed.toList()))
        previous = previous - passed
        recentlyRemoved = recentlyRemoved + listOf(passed to LOST_HIDE_REISSUE_ROUNDS)
    }

    fun clearAll(emitter: Emitter<MapEffect>) {
        val ids = previous.keys + recentlyRemoved.flatMap { it.first }
        if (ids.isNotEmpty()) {
            emitter.onNext(HideSymbols(ids.toList()))
        }
        previous = emptyMap()
        recentlyRemoved = emptyList()
    }

    /**
     * Seeds the controller as if a previous startMap generation had already drawn the positional id
     * range `0 until span`. The rideapp keeps drawn symbols across extension process death and
     * startMap restarts, and a fresh controller knows none of them; the persisted span bounds what
     * could remain. Seeding — rather than emitting hides here — lets the first [emit] fold the
     * stale ids into its own diff: unclaimed ids take the removed path (hidden, then reissued),
     * redrawn ids are re-shown in place with no preceding hide. No early hide exists for the
     * rideapp's async symbol processing to reorder after the shows.
     *
     * The sentinel's NaN coordinates mark the id as stale in [emit] and compare unequal to every
     * real spec, so a redrawn id always lands in the show set.
     */
    fun assumeStale(span: Int) {
        previous =
            (0 until span).associate { index ->
                val id = gradeMapChevronId(index)
                id to ClimbChevronSpec(id, Double.NaN, Double.NaN, 0f, 0, Double.NaN)
            }
    }

    private companion object {
        const val LOST_HIDE_REISSUE_ROUNDS = 3
    }
}
