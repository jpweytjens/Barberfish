package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import io.hammerhead.karooext.models.ViewConfig

/** Gap between the arrow box and the number, in dp. */
internal const val WIND_ARROW_GAP_DP = 4f

/** The widest headwind reading the arrow leaves full-size room for: a minus and two digits. */
internal const val WIND_REFERENCE_TEXT = "-29"

/**
 * Side of the arrow's square box: what the cell leaves after the gap and [referenceWidthPx] (the
 * width of [WIND_REFERENCE_TEXT] at the full value size), between half and all of the value height.
 * It depends on the cell, not the reading, so the arrow keeps its size while the wind changes.
 */
internal fun windArrowBoxPx(
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    referenceWidthPx: Float,
    gapPx: Int,
): Int =
    (cellWidthPx - gapPx - referenceWidthPx).toInt().coerceIn(bitmapHeightPx / 2, bitmapHeightPx)

/**
 * Top of the [arrowBoxPx] square, so the arrow's pivot sits at the centre of [digitHeightPx]-tall
 * digits standing on the bitmap's bottom edge, kept inside the bitmap.
 */
internal fun windArrowTopPx(bitmapHeightPx: Int, arrowBoxPx: Int, digitHeightPx: Float): Float =
    (bitmapHeightPx - digitHeightPx / 2f - arrowBoxPx / 2f).coerceIn(
        0f,
        (bitmapHeightPx - arrowBoxPx).toFloat(),
    )

/**
 * One value bitmap: the arrow on the left in an [arrowBoxPx] square centred on [digitHeightPx]-tall
 * digits (see [windArrowTopPx]), rotated by [angleDeg] about the box centre, and [text] on the
 * right with its baseline on the bitmap's bottom edge, as [renderValueBitmap] does. The bitmap
 * always spans the full cell width, so the arrow sits at a fixed x whatever the number's width. The
 * number's font size is decided by the caller, which hands [fontSizeForCell] the width left after
 * the arrow box and gap. The caller sizes the box with [windArrowBoxPx], once for both.
 * [arrowColor] is the cell's header text colour, never the zone colour: colour stays on the number.
 */
// Suppressed: matches the sibling renderers in BitmapValue.kt (renderTwoRowValueBitmap,
// renderHeaderBitmap) — one parameter per independent input, no grouping type would earn its keep.
@Suppress("LongParameterList")
fun renderWindArrowValueBitmap(
    angleDeg: Float,
    text: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    arrowBoxPx: Int,
    digitHeightPx: Float,
    cellWidthPx: Float,
    textColor: Int,
    arrowColor: Int,
    alignment: ViewConfig.Alignment,
    context: Context,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val gap = (WIND_ARROW_GAP_DP * density).toInt()
    val textPaint =
        valuePaint(fontSizePx).apply {
            color = textColor
            textAlign =
                when (alignment) {
                    ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
                    ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
                    ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
                }
        }
    val width = cellWidthPx.toInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, bitmapHeightPx)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    canvas.withTranslation(0f, windArrowTopPx(bitmapHeightPx, arrowBoxPx, digitHeightPx)) {
        drawWindArrow(this, angleDeg, arrowBoxPx.toFloat(), arrowColor)
    }

    val bounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()
    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> (arrowBoxPx + gap).toFloat()
            ViewConfig.Alignment.CENTER -> (arrowBoxPx + gap + width) / 2f
            ViewConfig.Alignment.RIGHT -> width.toFloat()
        }
    canvas.drawText(text, xPos, baselineY, textPaint)
    return bitmap
}

/** The arrow pointing up inside the [box]-sided square at the canvas origin, then rotated. */
private fun drawWindArrow(canvas: Canvas, angleDeg: Float, box: Float, color: Int) {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = WindArrowGeometry.strokePx(box)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
    val centre = box / 2f
    val halfShaft = WindArrowGeometry.shaftPx(box) / 2f
    val arm = WindArrowGeometry.headOffsetPx(box)
    val top = centre - halfShaft
    val head =
        Path().apply {
            moveTo(centre - arm, top + arm)
            lineTo(centre, top)
            lineTo(centre + arm, top + arm)
        }
    canvas.withRotation(angleDeg, centre, centre) {
        drawLine(centre, centre + halfShaft, centre, top, paint)
        drawPath(head, paint)
    }
}
