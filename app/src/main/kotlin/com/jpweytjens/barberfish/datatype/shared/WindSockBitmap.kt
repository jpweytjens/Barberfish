package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.models.ViewConfig

/** Gap between the sock box and the number, in dp. */
internal const val WIND_SOCK_GAP_DP = 4f

/** The sock's sweep circle has the diameter of the value's cap height: the box is that square. */
internal fun windSockBoxPx(bitmapHeightPx: Int): Int = bitmapHeightPx

// The longest sock spans this fraction of the box, leaving room for the mouth's corners when the
// sock lies diagonally.
private const val SOCK_SPAN = 0.9f

/**
 * One value bitmap: the sock on the left, rotated by [WindSockGlyph.angleDeg] about its own
 * midpoint, and [text] on the right with its baseline on the bitmap's bottom edge, as
 * [renderValueBitmap] does. The bitmap always spans the full cell width, so the sock's midpoint
 * sits at a fixed x whatever the number's width. The number's font size is decided by the caller,
 * which hands [fontSizeForCell] the width left after the sock box and gap.
 */
// Suppressed: matches the sibling renderers in BitmapValue.kt (renderTwoRowValueBitmap,
// renderHeaderBitmap) — one parameter per independent input, no grouping type would earn its keep.
@Suppress("LongParameterList")
fun renderWindSockValueBitmap(
    sock: WindSockGlyph,
    text: String,
    fontSizePx: Float,
    bitmapHeightPx: Int,
    cellWidthPx: Float,
    color: Int,
    alignment: ViewConfig.Alignment,
    context: Context,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val box = windSockBoxPx(bitmapHeightPx)
    val gap = (WIND_SOCK_GAP_DP * density).toInt()
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
    val width = cellWidthPx.toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, bitmapHeightPx, Bitmap.Config.ARGB_8888)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)

    if (sock.bands > 0) {
        // The drawable is a square with the mouth at its centre and the sock extending upward
        // over lengthDp(bands) of ICON_SIZE_DP. Scale it so the longest sock spans SOCK_SPAN of
        // the box, then place it so the sock's midpoint sits at the box centre and rotate there.
        val drawable = ContextCompat.getDrawable(context, windSockDrawable(sock.bands))
        if (drawable != null) {
            val maxLenPx = box * SOCK_SPAN
            val iconPx =
                maxLenPx * WindSockGeometry.ICON_SIZE_DP /
                    WindSockGeometry.lengthDp(WindSockGeometry.MAX_BANDS)
            val sockLenPx =
                iconPx * WindSockGeometry.lengthDp(sock.bands) / WindSockGeometry.ICON_SIZE_DP
            val cx = box / 2f
            val cy = bitmapHeightPx / 2f
            val iconCx = cx
            val iconCy = cy + sockLenPx / 2f
            drawable.setBounds(
                (iconCx - iconPx / 2f).toInt(),
                (iconCy - iconPx / 2f).toInt(),
                (iconCx + iconPx / 2f).toInt(),
                (iconCy + iconPx / 2f).toInt(),
            )
            canvas.save()
            canvas.rotate(sock.angleDeg, cx, cy)
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val baselineY = (bitmapHeightPx - bounds.bottom).toFloat()
    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> (box + gap).toFloat()
            ViewConfig.Alignment.CENTER -> (box + gap + width) / 2f
            ViewConfig.Alignment.RIGHT -> width.toFloat()
        }
    canvas.drawText(text, xPos, baselineY, paint)
    return bitmap
}
