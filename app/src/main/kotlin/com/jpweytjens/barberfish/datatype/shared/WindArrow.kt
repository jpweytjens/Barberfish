// Suppressed: the file is named for the field concept, like the sibling WindSock.kt/
// WindSockGeometry pairing, not verbatim for this single declaration.
@file:Suppress("MatchingDeclarationName")

package com.jpweytjens.barberfish.datatype.shared

/**
 * The Wind field's arrow, as fractions of the square box it turns in: one stroke for the shaft, two
 * for an open head at 45 degrees, round caps, no fill, fixed length. The number beside it carries
 * strength, so the arrow carries direction only. Proportions from the design mockup; the rotated
 * arrow's sweep circle stays inside the box.
 */
object WindArrowGeometry {
    const val SHAFT_FRACTION = 0.78f
    const val STROKE_FRACTION = 0.11f
    const val HEAD_ARM_FRACTION = 0.24f

    fun shaftPx(box: Float): Float = box * SHAFT_FRACTION

    fun strokePx(box: Float): Float = box * STROKE_FRACTION

    fun headArmPx(box: Float): Float = box * HEAD_ARM_FRACTION

    /** Farthest any ink gets from the centre: half the shaft plus the round cap. */
    fun sweepRadiusPx(box: Float): Float = shaftPx(box) / 2f + strokePx(box) / 2f
}
