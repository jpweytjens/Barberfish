package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import io.hammerhead.karooext.models.ViewConfig

/** Gap between the arrow box and the number, in dp. */
internal const val WIND_ARROW_GAP_DP = 4f

/** The arrow's sweep circle has the diameter of the value's cap height: the box is that square. */
internal fun windArrowBoxPx(bitmapHeightPx: Int): Int = bitmapHeightPx

/**
 * One value bitmap: the arrow on the left, rotated by [angleDeg] about the box centre, and [text]
 * on the right with its baseline on the bitmap's bottom edge, as [renderValueBitmap] does. The
 * bitmap always spans the full cell width, so the arrow sits at a fixed x whatever the number's
 * width. The number's font size is decided by the caller, which hands [fontSizeForCell] the width
 * left after the arrow box and gap. [arrowColor] is the cell's header text colour, never the zone
 * colour: colour stays on the number.
 */
// Suppressed: matches the sibling renderers in BitmapValue.kt (renderTwoRowValueBitmap,
// renderHeaderBitmap) — one parameter per independent input, no grouping type would earn its keep.
@Suppress("LongParameterList")
fun renderWindArrowValueBitmap(
    angleDeg: Float,
    text: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    textColor: Int,
    arrowColor: Int,
    alignment: ViewConfig.Alignment,
    context: Context,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val box = windArrowBoxPx(bitmapHeightPx)
    val gap = (WIND_ARROW_GAP_DP * density).toInt()
    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("relative", Typeface.NORMAL)
            textSize = fontSizePx
            color = textColor
            letterSpacing = LETTER_SPACING
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

    drawWindArrow(canvas, angleDeg, box.toFloat(), arrowColor)

    val bounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()
    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> (box + gap).toFloat()
            ViewConfig.Alignment.CENTER -> (box + gap + width) / 2f
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
    val arm = WindArrowGeometry.headArmPx(box)
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
