package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.GRADE_MAP_CASING_ID
import com.jpweytjens.barberfish.datatype.shared.GradeMapPolylineSpec
import com.jpweytjens.barberfish.datatype.shared.gradeMapSegmentId
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.delay

/**
 * Draws the grade band: one black casing polyline along the whole route, and one coloured fill
 * polyline per run on top of it. Tracks previously-emitted fill ids so each call to [emit] only
 * sends `HidePolyline` for ids that are no longer wanted and `ShowPolyline` for the new set.
 *
 * The casing has to sit beneath every fill, and the rideapp gives no control over that beyond two
 * rules it applies itself: a polyline id it has not seen is added above everything the extension
 * has drawn so far, and a `ShowPolyline` for an id it already has updates that polyline in place,
 * keeping its position. It does not process one batch in emission order, so casing and fills sent
 * together can land either way round. The first [emit] of a generation therefore hides any fills
 * already painted, lets the map settle, puts the casing down, settles again, and only then sends
 * the fills. Every later emit updates in place, where order no longer matters.
 *
 * Single-consumer usage from inside the `KarooExtension.startMap` coroutine — not thread-safe.
 */
internal class GradeMapController(
    private val casingId: String = GRADE_MAP_CASING_ID,
    private val settleMs: Long = CASING_SETTLE_MS,
) {
    private var previousIds: Set<String> = emptySet()
    private var casingBeneath = false

    suspend fun emit(
        emitter: Emitter<MapEffect>,
        casingEncoded: String,
        specs: List<GradeMapPolylineSpec>,
        fillWidth: Int,
        casingWidth: Int,
    ) {
        val newIds = specs.mapTo(mutableSetOf()) { it.id }
        val casing =
            ShowPolyline(
                id = casingId,
                encodedPolyline = casingEncoded,
                color = CASING_COLOR,
                width = casingWidth,
            )
        if (casingBeneath) {
            (previousIds - newIds).forEach { emitter.onNext(HidePolyline(it)) }
            emitter.onNext(casing)
        } else {
            // Fills already on the map (a dead generation's, or an install that predates the
            // casing) would keep their place beneath a casing added now. Hide them so the
            // shows below re-add them above it.
            previousIds.forEach { emitter.onNext(HidePolyline(it)) }
            delay(settleMs)
            emitter.onNext(casing)
            delay(settleMs)
            casingBeneath = true
        }
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
        emitter.onNext(HidePolyline(casingId))
        previousIds = emptySet()
        casingBeneath = false
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
        assumeStale((0 until span).mapTo(mutableSetOf()) { gradeMapSegmentId(it) })
    }

    /** As [assumeStale] with a span, for a controller whose fill ids are not positional. */
    fun assumeStale(ids: Set<String>) {
        previousIds = ids
    }
}

// Opaque black, so the band keeps a crisp edge over any map feature it crosses.
internal const val CASING_COLOR = 0xFF000000.toInt()

// How long to let the map settle between the hides, the casing and the fills on a generation's
// first emit, so the casing is added before the fills whatever order the rideapp works through
// a batch in. 500 ms was enough on-device; half of it was not tried.
internal const val CASING_SETTLE_MS = 500L
