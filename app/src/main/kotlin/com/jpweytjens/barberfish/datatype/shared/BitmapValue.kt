package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import io.hammerhead.karooext.models.ViewConfig

private const val MIN_BITMAP_HEIGHT_PX = 30
private const val LETTER_SPACING = -0.04f

/** Prefix glued to the climb value in the stacked Ride Remaining field; rendered as the ascent
 *  arrow icon (see [renderTwoRowValueBitmap]). */
const val ASCENT_MARKER = "↗ "

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
 * Each row may carry a leading icon ([row1Icon] / [row2Icon], pre-tinted) drawn inline before its
 * number as [icon][gap][number] — e.g. the route glyph before the distance and the ascent arrow
 * before the climb in Ride Remaining. A row whose text starts with [marker] has the glyph stripped
 * (the icon replaces it); a row with an icon but no marker keeps its full number. Rows without an
 * icon draw their whole string aligned to the field edge. Both rows are expected to be non-empty.
 */
fun renderTwoRowValueBitmap(
    row1: String,
    row2: String,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
    rowGapPx: Float = 4f,
    marker: String = ASCENT_MARKER,
    row1Icon: Bitmap? = null,
    row2Icon: Bitmap? = null,
): Bitmap {
    val width = cellWidthPx.toInt().coerceAtLeast(1)
    val bandPx = ((bitmapHeightPx - rowGapPx) / 2f).coerceAtLeast(1f)

    fun paintAt(sizePx: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("relative", Typeface.NORMAL)
            textSize = sizePx
            this.color = color
            letterSpacing = LETTER_SPACING
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
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    val iconGapPx = fontPx * 0.12f

    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    // Draw a row vertically centered within the band starting at [bandTop]. With an [icon] it is
    // drawn as [icon][gap][number] (the [marker] glyph stripped if present); otherwise the whole
    // string is drawn aligned to the field edge.
    fun drawRow(text: String, bandTop: Float, icon: Bitmap?) {
        val center = bandTop + bandPx / 2f
        if (icon == null) {
            paint.textAlign =
                when (alignment) {
                    ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
                    ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
                    ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
                }
            val xPos =
                when (alignment) {
                    ViewConfig.Alignment.LEFT -> 0f
                    ViewConfig.Alignment.CENTER -> width / 2f
                    ViewConfig.Alignment.RIGHT -> width.toFloat()
                }
            paint.getTextBounds(text, 0, text.length, bounds)
            canvas.drawText(text, xPos, center - (bounds.top + bounds.bottom) / 2f, paint)
            return
        }
        val number = if (text.startsWith(marker)) text.removePrefix(marker) else text
        paint.textAlign = Paint.Align.LEFT
        paint.getTextBounds(number, 0, number.length, bounds)
        val numW = paint.measureText(number)
        val iconSize = bounds.height() * 1.3f
        val unitW = iconSize + iconGapPx + numW
        val unitLeft =
            when (alignment) {
                ViewConfig.Alignment.LEFT -> 0f
                ViewConfig.Alignment.CENTER -> (width - unitW) / 2f
                ViewConfig.Alignment.RIGHT -> width - unitW
            }
        val iconTop = center - iconSize / 2f
        canvas.drawBitmap(
            icon,
            null,
            RectF(unitLeft, iconTop, unitLeft + iconSize, iconTop + iconSize),
            iconPaint,
        )
        canvas.drawText(
            number,
            unitLeft + iconSize + iconGapPx,
            center - (bounds.top + bounds.bottom) / 2f,
            paint,
        )
    }
    drawRow(row1, 0f, row1Icon)
    drawRow(row2, bandPx + rowGapPx, row2Icon)
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

// Native dataHeaderTextStyle sets no letterSpacing and lineSpacingMultiplier=0.7.
private const val HEADER_LINE_SPACING_MULT = 0.7f
// Vertical draw offset (px) inside the header bitmap. Measurement-tuning knob;
// keep 0 unless on-device parity needs a uniform residual trimmed.
private const val HEADER_DRAW_OFFSET_PX = 0

/**
 * Render an all-caps header [text] into an `ARGB_8888` bitmap whose height reserves
 * [maxLines] lines (native `dataHeaderTextStyle` uses `lines=2`), matching native's
 * `headerTextView` content box. Text wraps/ellipsizes to [availableWidthPx] and is
 * drawn with [alignment]; a 1-line label in a 2-line reservation is centered
 * vertically, as native does (and as the gravity=center_vertical TextView this
 * replaced did). `density = DENSITY_NONE` so RemoteViews renders 1:1.
 */
fun renderHeaderBitmap(
    text: String,
    fontSizePx: Float,
    maxLines: Int,
    availableWidthPx: Int,
    color: Int,
    alignment: ViewConfig.Alignment,
): Bitmap {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL)
        textSize = fontSizePx
        this.color = color
    }
    val width = availableWidthPx.coerceAtLeast(1)
    val upper = text.uppercase()
    val align = when (alignment) {
        ViewConfig.Alignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ViewConfig.Alignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        ViewConfig.Alignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }

    val layout = StaticLayout.Builder.obtain(upper, 0, upper.length, paint, width)
        .setAlignment(align)
        .setLineSpacing(0f, HEADER_LINE_SPACING_MULT)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    // Reserve the full maxLines block height regardless of actual line count, so
    // headers across a page share a height (native lines=2). Use a forced-N-line
    // reference layout with the same paint/spacing for an exact reservation.
    val refText = (0 until maxLines).joinToString("\n") { "M" }
    val refLayout = StaticLayout.Builder.obtain(refText, 0, refText.length, paint, width)
        .setLineSpacing(0f, HEADER_LINE_SPACING_MULT)
        .setIncludePad(false)
        .build()
    val reservedHeight = maxOf(refLayout.height, layout.height).coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, reservedHeight, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)
    // Center the text block the way native's TextView does (lines=maxLines,
    // lineSpacingMultiplier, gravity=center_vertical): its band is
    // maxLines * mult * lineHeight — shorter than the StaticLayout reservation,
    // whose last line gets no spacing extra. Centering in the reservation put
    // 1-line labels 5 px below native (measure_alignment.py, 5x2 page).
    val singleLineHeight =
        refLayout.height / (1f + HEADER_LINE_SPACING_MULT * (maxLines - 1))
    val textViewBandHeight = maxLines * HEADER_LINE_SPACING_MULT * singleLineHeight
    val drawOffset = ((textViewBandHeight - layout.height) / 2f).coerceAtLeast(0f)
    canvas.translate(0f, drawOffset + HEADER_DRAW_OFFSET_PX)
    layout.draw(canvas)
    return bitmap
}
