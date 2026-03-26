package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Returns (fontSizeSp, maxLines).
 *
 * Sizes [text] to fit [cellWidthPx] starting from [fontSizeBaseSp], using exact glyph
 * measurements via [Paint.measureText]. Falls back to 2-line wrapping when the single-line
 * result would drop below [wrapThresholdSp]; in that case the font is sized to fit the longer
 * half after splitting at the word boundary nearest the midpoint.
 *
 * The font measured is [Typeface.MONOSPACE] ("relative" in layout XML is a system alias for it).
 */
fun fontSizeForCell(
    text: String,
    fontSizeBaseSp: Int,
    cellWidthPx: Float,
    density: Float,
    wrapThresholdSp: Int = 20,
): Pair<Int, Int> {
    val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = fontSizeBaseSp * density
    }

    fun measureSp(str: String): Int {
        val w = paint.measureText(str)
        if (w <= cellWidthPx) return fontSizeBaseSp
        return (fontSizeBaseSp * cellWidthPx / w).toInt().coerceAtLeast(1)
    }

    val singleSp = measureSp(text)
    if (singleSp >= wrapThresholdSp) return Pair(singleSp, 1)

    // Try 2-line: split at word boundary nearest midpoint, size to the longer half.
    val longerHalf = longerHalfAfterSplit(text) ?: return Pair(singleSp, 1)
    return Pair(measureSp(longerHalf), 2)
}

/**
 * Splits [text] at the space nearest its midpoint and returns the longer of the two halves,
 * or null if there is no space to split on.
 */
private fun longerHalfAfterSplit(text: String): String? {
    val mid = text.length / 2
    val before = text.lastIndexOf(' ', mid)
    val after = text.indexOf(' ', mid)
    val split = when {
        before < 0 && after < 0 -> return null
        before < 0 -> after
        after < 0 -> before
        else -> if (mid - before <= after - mid) before else after
    }
    val line1 = text.substring(0, split)
    val line2 = text.substring(split + 1)
    return if (line1.length >= line2.length) line1 else line2
}

/**
 * Single-value convenience wrapper for Compose previews where maxLines is not needed.
 */
fun fontSizeSpForPreview(
    text: String,
    fontSizeBaseSp: Int,
    cellWidthPx: Float,
    density: Float,
): TextUnit = fontSizeForCell(text, fontSizeBaseSp, cellWidthPx, density).first.sp
