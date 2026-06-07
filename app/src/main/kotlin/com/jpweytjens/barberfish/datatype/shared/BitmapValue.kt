package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import io.hammerhead.karooext.models.ViewConfig

private const val MIN_BITMAP_HEIGHT_PX = 30
private const val LETTER_SPACING = -0.04f

// Bitmap height as a fraction of the value font base sp. Sized to the
// visible cap (~0.7 × textSize) plus a small buffer; tight enough to fit
// 5×1's baseline_box without ImageView fitCenter downscaling.
internal const val VALUE_BITMAP_HEIGHT_RATIO = 0.74f

/**
 * Constant bitmap height per layout: `VALUE_BITMAP_HEIGHT_RATIO × valueFontBaseSp × density`.
 *
 * Independent of `fontSizeForCell` shrinks so the baseline stays stable across content-driven font
 * changes.
 */
fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (VALUE_BITMAP_HEIGHT_RATIO * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}

/**
 * Render two stacked value rows into an `ARGB_8888` bitmap. Paint setup mirrors [renderValueBitmap]
 * exactly (same typeface, letterSpacing, density). Row baselines are pinned to the bottom of each
 * equal half of [bitmapHeightPx].
 *
 * Both rows are expected to be non-empty (callers pass formatted numbers).
 */
fun renderTwoRowValueBitmap(
    row1: String,
    row2: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
    rowGapPx: Float = 4f,
): Bitmap {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("relative", Typeface.NORMAL)
            textSize = fontSizePx
            this.color = color
            letterSpacing = LETTER_SPACING
            textAlign =
                when (alignment) {
                    ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
                    ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
                    ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
                }
        }
    val cellW = cellWidthPx.toInt().coerceAtLeast(1)
    val measuredWidth = maxOf(paint.measureText(row1), paint.measureText(row2))
    val width = measuredWidth.toInt().coerceIn(1, cellW)

    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> 0f
            ViewConfig.Alignment.CENTER -> width / 2f
            ViewConfig.Alignment.RIGHT -> width.toFloat()
        }
    val rowHeight = (bitmapHeightPx - rowGapPx) / 2f

    val bounds = Rect()
    paint.getTextBounds(row1, 0, row1.length, bounds)
    canvas.drawText(row1, xPos, rowHeight - bounds.bottom, paint)

    paint.getTextBounds(row2, 0, row2.length, bounds)
    canvas.drawText(row2, xPos, bitmapHeightPx - bounds.bottom.toFloat(), paint)

    return bitmap
}

/**
 * Render `text` into an `ARGB_8888` bitmap with the baseline pinned to the bitmap's bottom edge
 * (`bounds.bottom` ≈ 0 for digits).
 *
 * `bitmap.density = Bitmap.DENSITY_NONE` so RemoteViews renders at native pixel size with no
 * scaling. Width is clamped to `cellWidthPx`.
 */
fun renderValueBitmap(
    text: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
): Bitmap {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("relative", Typeface.NORMAL)
            textSize = fontSizePx
            this.color = color
            letterSpacing = LETTER_SPACING
            textAlign =
                when (alignment) {
                    ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
                    ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
                    ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
                }
        }
    val measuredWidth = paint.measureText(text)
    val cellW = cellWidthPx.toInt().coerceAtLeast(1)
    val width = measuredWidth.toInt().coerceIn(1, cellW)

    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()

    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> 0f
            ViewConfig.Alignment.CENTER -> width / 2f
            ViewConfig.Alignment.RIGHT -> width.toFloat()
        }
    canvas.drawText(text, xPos, baselineY, paint)
    return bitmap
}
