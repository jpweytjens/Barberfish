package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.ClimbPolylineSpec
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline

/**
 * Tracks previously-emitted extension polyline IDs so each call to [emit] only sends
 * `HidePolyline` for IDs that are no longer wanted and `ShowPolyline` for the new set.
 *
 * We intentionally do not emit a separate black outline polyline under each coloured fill.
 * The Karoo rideapp's PolylineManager processes `ShowPolyline` events asynchronously and
 * sometimes reorders them across IPC, so an outline emitted before its fill can still land
 * after it and — since the rideapp paints extension polylines in addition order within the
 * layer — end up hiding the fill as a solid black band. The native `ROUTE_LINE` / `CLIMB_LINE`
 * underneath already provides enough contrast.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine — not thread-safe.
 */
internal class ClimbMapController {
    private var previousIds: Set<String> = emptySet()

    fun emit(
        emitter: Emitter<MapEffect>,
        specs: List<ClimbPolylineSpec>,
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
                ),
            )
        }
        previousIds = newIds
    }

    fun clearAll(emitter: Emitter<MapEffect>) {
        previousIds.forEach { emitter.onNext(HidePolyline(it)) }
        previousIds = emptySet()
    }
}
