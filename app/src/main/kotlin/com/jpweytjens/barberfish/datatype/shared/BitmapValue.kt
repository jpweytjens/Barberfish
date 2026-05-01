package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import io.hammerhead.karooext.models.ViewConfig

private const val MIN_BITMAP_HEIGHT_PX = 30
private const val LETTER_SPACING = -0.04f

/**
 * Bitmap height for a value cell at the given layout's `valueFontBase` (sp).
 *
 * The visible digit cap of Karoo's `relative` monospace is ≈ 0.7 × textSize.
 * 0.8 × textSize gives just enough room for the cap plus ~0.1 × textSize of
 * buffer above it, with the baseline pinned to the bitmap bottom (digits
 * have no descender so no padding needed there). Choosing a CONSTANT height
 * per layout — independent of the per-render `fontSizeForCell` shrunk size —
 * means the baseline is stable across content-driven font-size changes.
 *
 * Pixel-rounded with a `MIN_BITMAP_HEIGHT_PX` floor for safety.
 */
fun valueBitmapHeightPx(valueFontBaseSp: Int, density: Float): Int {
    val raw = (0.8f * valueFontBaseSp * density).toInt()
    return raw.coerceAtLeast(MIN_BITMAP_HEIGHT_PX)
}

/**
 * Render `text` into an `ARGB_8888` bitmap of constant height `bitmapHeightPx`,
 * drawing text such that the BASELINE lies at the bitmap's bottom edge.
 *
 * For digits (no descenders), bitmap-bottom = baseline = visible glyph
 * bottom, so the bitmap contains exactly the visible cap with no padding
 * below. Above the cap, `bitmapHeightPx − cap_height ≈ 0.1 × fontSizePx`
 * of breathing room (computed in `valueBitmapHeightPx`).
 *
 * `bitmap.density = Bitmap.DENSITY_NONE` matches the sparkline convention,
 * so RemoteViews renders the bitmap at native pixel size with no scaling.
 *
 * Width is the measured text width clamped between 1 and `cellWidthPx`.
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

    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    val xPos = when (alignment) {
        ViewConfig.Alignment.LEFT -> 0f
        ViewConfig.Alignment.CENTER -> width / 2f
        ViewConfig.Alignment.RIGHT -> width.toFloat()
    }
    canvas.drawText(text, xPos, bitmapHeightPx.toFloat(), paint)
    return bitmap
}
