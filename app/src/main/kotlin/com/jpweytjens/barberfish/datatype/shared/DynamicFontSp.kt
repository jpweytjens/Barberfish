package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Font size for single-value fields (showHeader=true, full-width Box layout).
 *
 * Strips narrow punctuation (`:`, `.`, `'`, `"`) before scaling so that "0:00:00" (5 wide glyphs)
 * is treated the same as a 5-character string.
 */
fun singleValueFontSp(text: String, textSize: Int): TextUnit {
    // Narrow punctuation occupies ~50% of a digit's width in monospace; weight accordingly.
    val effective = text.sumOf { if (it in ":.'\""  ) 0.5 else 1.0 }.coerceAtLeast(4.0)
    return (textSize * 4f / effective).toInt().sp
}

/** Font size for narrow multicolumn columns (1/3-width, label+value layout). */
fun narrowFontSp(@Suppress("UNUSED_PARAMETER") length: Int): TextUnit = 36.sp
