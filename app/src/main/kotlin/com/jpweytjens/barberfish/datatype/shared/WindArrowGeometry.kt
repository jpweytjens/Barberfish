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
    const val HEAD_OFFSET_FRACTION = 0.24f

    fun shaftPx(box: Float): Float = box * SHAFT_FRACTION

    fun strokePx(box: Float): Float = box * STROKE_FRACTION

    /** Head offset on each axis, so the arms run at 45 degrees. */
    fun headOffsetPx(box: Float): Float = box * HEAD_OFFSET_FRACTION

    /** Farthest any ink gets from the centre: half the shaft plus the round cap. */
    fun sweepRadiusPx(box: Float): Float = shaftPx(box) / 2f + strokePx(box) / 2f
}
