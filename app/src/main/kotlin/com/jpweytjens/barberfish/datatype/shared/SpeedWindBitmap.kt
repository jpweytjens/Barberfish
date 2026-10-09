package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import io.hammerhead.karooext.models.ViewConfig

/** Gap between the two rows, as in [renderTwoRowValueBitmap]. */
internal const val SPEED_WIND_ROW_GAP_PX = 4f

/** Share of a row's band a digit fills. Same value as TWO_ROW_DIGIT_FILL in BitmapValue.kt. */
internal const val SPEED_WIND_DIGIT_FILL = 0.86f

/** Where the speed-over-wind stack puts things, in px. */
internal data class SpeedWindGeometry(
    val bandPx: Float,
    val rowGapPx: Float,
    val boxPx: Int,
    val gapPx: Int,
    val textLeftPx: Int,
)

/**
 * The stack splits the value height into two bands like Ride Remaining. The wind arrow keeps the
 * [arrowBoxPx] square of the single-row Wind slot, at the left edge, with the usual gap before the
 * number column, so turning Show speed on or off leaves the arrow where it was.
 */
internal fun speedWindGeometry(
    bitmapHeightPx: Int,
    arrowBoxPx: Int,
    density: Float,
): SpeedWindGeometry {
    val band = ((bitmapHeightPx - SPEED_WIND_ROW_GAP_PX) / 2f).coerceAtLeast(1f)
    val gap = (WIND_ARROW_GAP_DP * density).toInt()
    return SpeedWindGeometry(band, SPEED_WIND_ROW_GAP_PX, arrowBoxPx, gap, arrowBoxPx + gap)
}

/**
 * The Wind slot with Show speed: [speedText] on the top row and [windText] on the bottom row, with
 * the wind arrow rotated by [angleDeg] in an [arrowBoxPx] square left of both rows, as
 * [renderWindArrowValueBitmap] places it. Both numbers share one font, sized so a digit fills
 * [SPEED_WIND_DIGIT_FILL] of a band and shrunk only if the wider row does not fit, and share one
 * edge per [alignment]. A null [angleDeg] (calm) leaves the arrow column empty so nothing moves.
 * Colour stays on the wind number; speed and arrow take the header colour.
 */
// Suppressed: matches the sibling renderers (renderWindArrowValueBitmap, renderTwoRowValueBitmap),
// one parameter per independent input.
@Suppress("LongParameterList")
fun renderSpeedWindValueBitmap(
    speedText: String,
    windText: String,
    angleDeg: Float?,
    bitmapHeightPx: Int,
    arrowBoxPx: Int,
    cellWidthPx: Float,
    speedColor: Int,
    windColor: Int,
    arrowColor: Int,
    alignment: ViewConfig.Alignment,
    context: Context,
): Bitmap {
    val geo =
        speedWindGeometry(bitmapHeightPx, arrowBoxPx, context.resources.displayMetrics.density)
    val width = cellWidthPx.toInt().coerceAtLeast(1)

    fun paintAt(sizePx: Float, color: Int) =
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
    paintAt(100f, speedColor).getTextBounds("0", 0, 1, bounds)
    var fontPx =
        if (bounds.height() > 0) 100f * geo.bandPx * SPEED_WIND_DIGIT_FILL / bounds.height()
        else geo.bandPx
    val column = (width - geo.textLeftPx).coerceAtLeast(1).toFloat()
    val probe = paintAt(fontPx, speedColor)
    val needed = maxOf(probe.measureText(speedText), probe.measureText(windText))
    if (needed > column) fontPx *= column / needed

    val bitmap = createBitmap(width, bitmapHeightPx)
    bitmap.density = Bitmap.DENSITY_NONE
    val canvas = Canvas(bitmap)
    val xPos =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> geo.textLeftPx.toFloat()
            ViewConfig.Alignment.CENTER -> (geo.textLeftPx + width) / 2f
            ViewConfig.Alignment.RIGHT -> width.toFloat()
        }

    fun drawRow(text: String, bandTop: Float, color: Int) {
        val paint = paintAt(fontPx, color)
        paint.getTextBounds(text, 0, text.length, bounds)
        val center = bandTop + geo.bandPx / 2f
        canvas.drawText(text, xPos, center - (bounds.top + bounds.bottom) / 2f, paint)
    }
    val windTop = geo.bandPx + geo.rowGapPx
    drawRow(speedText, 0f, speedColor)
    drawRow(windText, windTop, windColor)

    if (angleDeg != null) {
        // The developer's arrow, rasterised 1:1 into its own box through the public renderer.
        val arrow =
            renderWindArrowValueBitmap(
                angleDeg = angleDeg,
                text = "",
                fontSizePx = 1f,
                bitmapHeightPx = geo.boxPx,
                arrowBoxPx = geo.boxPx,
                // A box-tall bitmap with box-tall "digits" puts the arrow at its top edge; the
                // drawBitmap below centres it on the gap between the rows.
                digitHeightPx = geo.boxPx.toFloat(),
                cellWidthPx = geo.boxPx.toFloat(),
                textColor = arrowColor,
                arrowColor = arrowColor,
                alignment = ViewConfig.Alignment.LEFT,
                context = context,
            )
        canvas.drawBitmap(arrow, 0f, (bitmapHeightPx - geo.boxPx) / 2f, null)
    }
    return bitmap
}

/**
 * The value bitmap for a Wind slot with Show speed, [speedRow] being the field's speed text. Speed
 * and arrow take the header colour, the wind number the value colour: in Fill mode both are the
 * on-fill pick.
 */
@Suppress("LongParameterList")
internal fun speedWindValueBitmap(
    speedRow: String,
    field: FieldState,
    valueText: String,
    bitmapHeightPx: Int,
    arrowBoxPx: Int,
    cellWidthPx: Float,
    colors: ColorConfig,
    alignment: ViewConfig.Alignment,
    context: Context,
): Bitmap =
    renderSpeedWindValueBitmap(
        speedText = speedRow.replace(',', '.'),
        windText = valueText,
        angleDeg = field.windArrowDeg,
        bitmapHeightPx = bitmapHeightPx,
        arrowBoxPx = arrowBoxPx,
        cellWidthPx = cellWidthPx,
        speedColor = colors.headerText.toArgb(),
        windColor = colors.valueText.toArgb(),
        arrowColor = colors.headerText.toArgb(),
        alignment = alignment,
        context = context,
    )
