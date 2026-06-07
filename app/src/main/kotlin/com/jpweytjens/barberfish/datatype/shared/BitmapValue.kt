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

// Fraction of a row's band the digits fill, leaving a small vertical margin so the top row
// doesn't clip against the bitmap's top edge and the rows stay readable.
private const val TWO_ROW_DIGIT_FILL = 0.86f

/**
 * Render two stacked value rows into a full-cell-width `ARGB_8888` bitmap. The two rows share the
 * standard single-row value height ([bitmapHeightPx]) split into two equal bands, so the stacked
 * field keeps the same value footprint as the single-row fields. Each row is aligned to the field
 * edge per [alignment] (matching the single-row fields) and vertically centered in its band. The
 * font is sized so a digit fills [TWO_ROW_DIGIT_FILL] of a band, then shrunk to fit the cell width.
 *
 * The climb [marker] travels inline with its number (e.g. "↗ 1240" right-aligned as one unit).
 * Both rows are expected to be non-empty (callers pass formatted numbers).
 */
fun renderTwoRowValueBitmap(
    row1: String,
    row2: String,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
    rowGapPx: Float = 4f,
): Bitmap {
    val width = cellWidthPx.toInt().coerceAtLeast(1)
    val bandPx = ((bitmapHeightPx - rowGapPx) / 2f).coerceAtLeast(1f)

    fun paintAt(sizePx: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("relative", Typeface.NORMAL)
            textSize = sizePx
            this.color = color
            letterSpacing = LETTER_SPACING
            textAlign =
                when (alignment) {
                    ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
                    ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
                    ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
                }
        }

    val bounds = Rect()
    // Height-fit: a digit fills most of one band (margin avoids top/bottom clipping).
    paintAt(100f).getTextBounds("0", 0, 1, bounds)
    var fontPx = if (bounds.height() > 0) 100f * (bandPx * TWO_ROW_DIGIT_FILL) / bounds.height() else bandPx
    // Width-fit: shrink so the wider row fits the cell.
    run {
        val p = paintAt(fontPx)
        val needed = maxOf(p.measureText(row1), p.measureText(row2))
        if (needed > width) fontPx *= width / needed
    }
    val paint = paintAt(fontPx)
    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> 0f
            ViewConfig.Alignment.CENTER -> width / 2f
            ViewConfig.Alignment.RIGHT -> width.toFloat()
        }

    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    // Draw [text] vertically centered within the band starting at [bandTop].
    fun drawRow(text: String, bandTop: Float) {
        paint.getTextBounds(text, 0, text.length, bounds)
        val baseline = bandTop + bandPx / 2f - (bounds.top + bounds.bottom) / 2f
        canvas.drawText(text, xPos, baseline, paint)
    }
    drawRow(row1, 0f)
    drawRow(row2, bandPx + rowGapPx)
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
