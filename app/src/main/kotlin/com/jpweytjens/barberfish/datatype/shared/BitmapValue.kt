package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
 * Independent of `fontSizeForCell` shrinks so the baseline stays stable
 * across content-driven font changes.
 */
fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (VALUE_BITMAP_HEIGHT_RATIO * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}

/**
 * Render `text` into an `ARGB_8888` bitmap with the baseline pinned to the
 * bitmap's bottom edge (`bounds.bottom` ≈ 0 for digits).
 *
 * `bitmap.density = Bitmap.DENSITY_NONE` so RemoteViews renders at native
 * pixel size with no scaling. Width is clamped to `cellWidthPx`.
 */
fun renderValueBitmap(
    text: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("relative", Typeface.NORMAL)
        textSize = fontSizePx
        this.color = color
        letterSpacing = LETTER_SPACING
        textAlign = when (alignment) {
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

    val xPos = when (alignment) {
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
 * drawn with [alignment]; a 1-line label in a 2-line reservation sits on the top
 * line, as native does. `density = DENSITY_NONE` so RemoteViews renders 1:1.
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
    canvas.translate(0f, HEADER_DRAW_OFFSET_PX.toFloat())
    layout.draw(canvas)
    return bitmap
}
