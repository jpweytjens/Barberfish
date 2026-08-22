package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.datatype.shared.gradeMapSegmentId
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline

/**
 * Tracks previously-emitted extension polyline IDs so each call to [emit] only sends `HidePolyline`
 * for IDs that are no longer wanted and `ShowPolyline` for the new set.
 *
 * We intentionally do not emit a separate black outline polyline under each coloured fill. The
 * Karoo rideapp's PolylineManager processes `ShowPolyline` events asynchronously and sometimes
 * reorders them across IPC, so an outline emitted before its fill can still land after it and —
 * since the rideapp paints extension polylines in addition order within the layer — end up hiding
 * the fill as a solid black band. The native `ROUTE_LINE` / `CLIMB_LINE` underneath already
 * provides enough contrast.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine — not thread-safe.
 */
internal class GradeMapController {
    private var previousIds: Set<String> = emptySet()

    fun emit(
        emitter: Emitter<MapEffect>,
        specs: List<GradeMapPolylineSpec>,
        fillWidth: Int,
    ) {
        val newIds = specs.mapTo(mutableSetOf()) { it.id }
        (previousIds - newIds).forEach { emitter.onNext(HidePolyline(it)) }
        specs.forEach { spec ->
            emitter.onNext(
                ShowPolyline(
                    id = spec.id,
                    encodedPolyline = spec.encoded,
                    color = spec.colorArgb,
                    width = fillWidth,
                )
            )
        }
        previousIds = newIds
    }

    fun clearAll(emitter: Emitter<MapEffect>) {
        previousIds.forEach { emitter.onNext(HidePolyline(it)) }
        previousIds = emptySet()
    }

    /**
     * Seeds the controller as if a previous startMap generation had already drawn the positional id
     * range `0 until span`. The rideapp keeps drawn symbols across extension process death and
     * startMap restarts, and a fresh controller knows none of them; the persisted span bounds what
     * could remain. Seeding — rather than emitting hides here — lets the first [emit] hide the
     * unclaimed ids inside its own hide batch and re-show the rest, so no early hide can be
     * reordered after the shows by the rideapp's async symbol processing.
     */
    fun assumeStale(span: Int) {
        previousIds = (0 until span).mapTo(mutableSetOf()) { gradeMapSegmentId(it) }
    }
}
