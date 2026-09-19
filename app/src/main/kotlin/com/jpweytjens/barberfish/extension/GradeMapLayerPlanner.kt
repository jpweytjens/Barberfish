package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.GRADE_MAP_CASING_ID
import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.datatype.shared.gradeMapPieceCasingId
import com.jpweytjens.barberfish.datatype.shared.gradeMapSegmentId
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.delay

/** One group of map effects to send together, and whether to let the map settle afterwards. */
internal data class LayerBatch(val effects: List<MapEffect>, val settleAfter: Boolean)

/**
 * Turns the pieces that should be visible into ordered batches. The map applies two rules and
 * offers nothing else: an id it has not seen goes on top of everything the extension has drawn, and
 * a show for an id it holds updates that layer in place. A batch is not processed in emission
 * order. So a stacking pass hides everything painted, settles, puts every casing down, settles,
 * then shows the fills one depth at a time from the bottom, settling between depths. A plan
 * restacks when an id is new to the map or a painted id's depth changed; otherwise it updates in
 * place, hiding the ids that went away and re-showing the pieces whose geometry or colour changed,
 * with no settle. So a zoom re-cut that moves extents but keeps depths never blanks the band.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine; not thread-safe.
 */
internal class GradeMapLayerPlanner {
    // Fill id to the spec last shown for it; null for an id seeded by assumeStale, whose layout
    // is unknown and whose casing and fill both need hiding.
    private var painted: Map<String, GradeMapPolylineSpec?> = emptyMap()
    private var stacked = false

    fun plan(
        pieces: List<GradeMapPolylineSpec>,
        fillWidth: Int,
        casingWidth: Int,
    ): List<LayerBatch> {
        val structural =
            !stacked ||
                pieces.any { piece -> painted[piece.id]?.let { it.depth == piece.depth } != true }
        val batches =
            if (structural) restack(pieces, fillWidth, casingWidth)
            else updateInPlace(pieces, fillWidth, casingWidth)
        painted = pieces.associateBy { it.id }
        stacked = true
        return batches
    }

    fun clearAll(): List<LayerBatch> {
        val hides = hideAllPainted(includeLegacy = true)
        painted = emptyMap()
        stacked = false
        return listOf(LayerBatch(hides, settleAfter = false))
    }

    /**
     * Seeds the planner as if a previous startMap generation had painted the positional id range `0
     * until span`, with their derived casings and the legacy whole-route casing. The rideapp keeps
     * drawn polylines across extension process death and startMap restarts; the persisted span
     * bounds what could remain, and the first plan hides all of it before stacking.
     */
    fun assumeStale(span: Int) {
        painted = (0 until span).associate { gradeMapSegmentId(it) to null }
        stacked = false
    }

    private fun restack(
        pieces: List<GradeMapPolylineSpec>,
        fillWidth: Int,
        casingWidth: Int,
    ): List<LayerBatch> {
        val batches = mutableListOf<LayerBatch>()
        // The whole-route casing of earlier versions is hidden once per generation, before
        // anything is stacked, so an upgrade mid-ride does not leave it painted.
        val hides = hideAllPainted(includeLegacy = !stacked)
        if (hides.isNotEmpty()) batches += LayerBatch(hides, settleAfter = true)
        if (pieces.isNotEmpty()) {
            batches += LayerBatch(pieces.map { casingShow(it, casingWidth) }, settleAfter = true)
        }
        val byDepth = pieces.groupBy { it.depth }.toSortedMap().values.toList()
        byDepth.forEachIndexed { i, depthPieces ->
            batches +=
                LayerBatch(
                    depthPieces.map { fillShow(it, fillWidth) },
                    settleAfter = i < byDepth.lastIndex,
                )
        }
        return batches
    }

    private fun updateInPlace(
        pieces: List<GradeMapPolylineSpec>,
        fillWidth: Int,
        casingWidth: Int,
    ): List<LayerBatch> {
        val current = pieces.associateBy { it.id }
        val effects = mutableListOf<MapEffect>()
        (painted.keys - current.keys).forEach { id ->
            effects += HidePolyline(id)
            effects += HidePolyline(gradeMapPieceCasingId(id))
        }
        pieces.forEach { piece ->
            val previous = painted[piece.id] ?: return@forEach
            if (previous.casingEncoded != piece.casingEncoded) {
                effects += casingShow(piece, casingWidth)
            }
            if (previous.encoded != piece.encoded || previous.colorArgb != piece.colorArgb) {
                effects += fillShow(piece, fillWidth)
            }
        }
        return if (effects.isEmpty()) emptyList()
        else listOf(LayerBatch(effects, settleAfter = false))
    }

    private fun hideAllPainted(includeLegacy: Boolean): List<MapEffect> {
        val hides = mutableListOf<MapEffect>()
        painted.keys.forEach { id ->
            hides += HidePolyline(id)
            hides += HidePolyline(gradeMapPieceCasingId(id))
        }
        if (includeLegacy) hides += HidePolyline(GRADE_MAP_CASING_ID)
        return hides
    }

    private fun casingShow(piece: GradeMapPolylineSpec, casingWidth: Int) =
        ShowPolyline(
            id = gradeMapPieceCasingId(piece.id),
            encodedPolyline = piece.casingEncoded,
            color = CASING_COLOR,
            width = casingWidth,
        )

    private fun fillShow(piece: GradeMapPolylineSpec, fillWidth: Int) =
        ShowPolyline(
            id = piece.id,
            encodedPolyline = piece.encoded,
            color = piece.colorArgb,
            width = fillWidth,
        )
}

/** Sends [batches] in order, sleeping [settleMs] after each batch that asks for it. */
internal suspend fun emitLayerBatches(
    emitter: Emitter<MapEffect>,
    batches: List<LayerBatch>,
    settleMs: Long = CASING_SETTLE_MS,
) {
    batches.forEach { batch ->
        batch.effects.forEach { emitter.onNext(it) }
        if (batch.settleAfter) delay(settleMs)
    }
}
