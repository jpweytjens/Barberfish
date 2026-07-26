package com.jpweytjens.barberfish.extension

import kotlin.math.floor

/**
 * Holds the zoom level the chevron spacing is computed from, refreshing it only when the
 * integer zoom band changes.
 *
 * The rideapp lays out its route markers once per integer band, at whatever fractional zoom
 * it happened to be at when the band changed, and leaves them geo-pinned for the rest of
 * that band. Following that keeps our cadence in step with native's and, since the frozen
 * value only moves on a band crossing, collapses a pinch gesture into at most one overlay
 * rebuild instead of one per zoom step.
 *
 * The first value is treated as provisional so the flow's seeded default cannot lock in a
 * whole band: the first real zoom replaces it regardless of band.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine, called once per
 * zoom emission. Not thread-safe, and it must not be called from a `combine` transform,
 * where an unrelated emission would spend the provisional slot.
 */
internal class ChevronZoomBand {

    private var frozen: Double? = null
    private var provisional = true

    fun effectiveZoom(zoom: Double): Double {
        val current = frozen
        when {
            current == null -> frozen = zoom
            provisional -> {
                frozen = zoom
                provisional = false
            }
            floor(zoom) != floor(current) -> frozen = zoom
        }
        return frozen ?: zoom
    }
}
