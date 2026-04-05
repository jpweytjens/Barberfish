package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Returns (fontSizeSp, maxLines).
 *
 * Sizes [text] to fit [cellWidthPx] starting from [fontSizeBaseSp], using exact glyph
 * measurements via [Paint.measureText]. Falls back to 2-line wrapping when the single-line
 * result would drop below [wrapThresholdSp]; in that case the font is sized to fit the longer
 * half after splitting at the word boundary nearest the midpoint.
 *
 * [typeface] defaults to [Typeface.MONOSPACE] for value text; pass [Typeface.DEFAULT] for
 * label text (proportional font). [bold] switches to the bold variant of [typeface] for
 * measurement — use when the rendered text uses [FontWeight.Bold].
 */
fun fontSizeForCell(
    text: String,
    fontSizeBaseSp: Int,
    cellWidthPx: Float,
    density: Float,
    wrapThresholdSp: Int = 20,
    typeface: Typeface = Typeface.MONOSPACE,
    bold: Boolean = false,
): Pair<Int, Int> {
    val paint = Paint().apply {
        this.typeface = if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
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
 * Header height (px) for an ibm-plex-sans-condensed label at [headerFontSizeSp] with
 * [lines] lines (lineSpacingMultiplier=0.7), floored at 22 dp.
 * Used as top padding on the value TextView to clear the header zone.
 */
fun headerHeightPx(headerFontSizeSp: Float, lines: Int, density: Float): Int {
    val paint = Paint().apply {
        typeface = Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL)
        textSize = headerFontSizeSp * density
    }
    val fm = paint.fontMetrics
    val lineHeight = fm.descent - fm.ascent // ascent is negative
    // lineSpacingMultiplier=0.7 only affects inter-line spacing, not single-line height.
    val factor = if (lines == 1) 1.0f else 1.7f // 1 + 0.7 for 2 lines
    val textHeight = (factor * lineHeight).toInt()
    val minHeightPx = (22 * density).toInt()
    return maxOf(textHeight, minHeightPx)
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
