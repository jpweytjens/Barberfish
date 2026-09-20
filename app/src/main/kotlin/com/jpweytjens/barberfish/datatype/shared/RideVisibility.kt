package com.jpweytjens.barberfish.datatype.shared

/** What the map should hold for one drawing piece at the current progress. */
internal sealed interface PieceVisibility {
    data object Shown : PieceVisibility

    data object Hidden : PieceVisibility

    /** Drawn from [fromM] to the piece's end: the rider is on it. */
    data class Trimmed(val fromM: Double) : PieceVisibility
}

/**
 * Which visits of a route are still drawn as the rider progresses. Visits with a later visit to the
 * same ground are retained after being ridden, so the colours the rider just passed do not flip to
 * the return leg's behind them; they hide once out of view or once the next visit starts within a
 * view radius of route distance ahead, which on a return leg is the moment that ground enters the
 * screen. A visit with no successor is not tracked here: its pieces hide as progress passes their
 * ends, and the piece under the rider is trimmed to progress.
 *
 * Hidden state is keyed on [RouteVisit.key], a pure function of the route, so it survives colour
 * and zoom rebuilds and a fresh instance can reconstruct it from progress after a restart. Only
 * ever grows; a new route identity gets a new instance.
 */
internal class RideVisibility(private val index: RouteIndex) {
    private val hidden = mutableSetOf<Int>()

    val hiddenVisits: Set<Int>
        get() = hidden

    var progressM: Double = 0.0
        private set

    /**
     * Applies accepted [progressM] on the app's GPS axis and the rider's last fix. A missing
     * [rider] disables the out-of-view clause only. Returns true when progress moved or a visit was
     * newly hidden.
     */
    fun update(progressM: Double, rider: LatLng?, viewRadiusM: Double): Boolean {
        var changed = progressM > this.progressM
        this.progressM = maxOf(this.progressM, progressM)
        for (visit in index.visits) {
            val nextM = visit.nextVisitM
            if (nextM == null || visit.key in hidden || this.progressM < visit.endM) continue
            val outOfView = rider != null && distanceToBoundsM(rider, visit.bounds) > viewRadiusM
            if (outOfView || this.progressM >= nextM - viewRadiusM) {
                hidden += visit.key
                changed = true
            }
        }
        return changed
    }

    fun visibilityOf(visitKey: Int, pieceStartM: Double, pieceEndM: Double): PieceVisibility {
        val visit = index.visit(visitKey)
        if (visit.nextVisitM != null) {
            return if (visit.key in hidden) PieceVisibility.Hidden else PieceVisibility.Shown
        }
        return when {
            pieceEndM <= progressM -> PieceVisibility.Hidden
            pieceStartM < progressM -> PieceVisibility.Trimmed(progressM)
            else -> PieceVisibility.Shown
        }
    }

    /** The visit of [unit] whose colour and chevrons show now: the earliest not hidden. */
    fun exposedVisit(unit: Int): RouteVisit? =
        index.visitsOfUnit(unit).firstOrNull { it.key !in hidden }

    /**
     * True when a chevron at [distanceM] may be drawn: its visit is the exposed one for its ground,
     * and on a final visit the mark is not yet passed.
     */
    fun chevronDrawable(distanceM: Double): Boolean {
        val visit = index.visitAt(distanceM) ?: return false
        return exposedVisit(visit.unit)?.key == visit.key &&
            (visit.nextVisitM != null || distanceM >= progressM)
    }
}

/**
 * The chevrons to draw now: every drawable candidate in ride order, skipping one within
 * [collisionRadiusM] of a mark already kept. Collision is decided here, at draw time, so a
 * candidate suppressed by a mark that later hides is reconsidered on the next update.
 */
internal fun selectChevrons(
    candidates: List<ClimbChevronSpec>,
    visibility: RideVisibility,
    collisionRadiusM: Double,
): List<ClimbChevronSpec> {
    val kept = mutableListOf<ClimbChevronSpec>()
    for (candidate in candidates) {
        if (!visibility.chevronDrawable(candidate.distanceM)) continue
        val here = LatLng(candidate.lat, candidate.lng)
        val collides =
            collisionRadiusM > 0.0 &&
                kept.any { latLngDistanceM(LatLng(it.lat, it.lng), here) < collisionRadiusM }
        if (!collides) kept += candidate
    }
    return kept
}
