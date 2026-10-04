package com.jpweytjens.barberfish.extension

import kotlin.math.floor

/**
 * Holds the zoom level the chevron spacing and the polyline simplification are computed from,
 * refreshing it only when the integer zoom band changes.
 *
 * Freezing the zoom per band keeps chevrons geo-pinned while you pinch, so their on-screen density
 * shrinks as you zoom in (and grows as you zoom out) within the band, rather than the whole overlay
 * re-laying out. Since the frozen value only moves on a band crossing, a pinch gesture costs at
 * most one overlay rebuild instead of one per zoom step. It also reads closer to the native map's
 * own arrow rhythm. The same frozen value feeds the polyline path's metres-per-pixel, so both move
 * together on a band crossing.
 *
 * The first value is treated as provisional so the flow's seeded default cannot lock in a whole
 * band: the first real zoom replaces it regardless of band.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine, called once per zoom
 * emission. Not thread-safe, and it must not be called from a `combine` transform, where an
 * unrelated emission would spend the provisional slot.
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
