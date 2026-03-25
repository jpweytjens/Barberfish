package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Returns a font size that fits [text] in a cell designed for [fontSizeBase] sp at [baseChars]
 * weighted characters. Character weights:
 * - digits and other characters: 1.0
 * - `h`, `m`, `s` (time unit letters): 0.5
 * - narrow punctuation (`:`, `.`, `'`, `"`): 0.1
 *
 * Use [baseChars]=4 for narrow (2-col) cells, [baseChars]=6 for wide (1-col) cells. [k] scales the
 * result; tweak to adjust aggressiveness.
 */
fun dynamicFontSp(
    text: String,
    fontSizeBase: Int = 49,
    baseChars: Int = 4,
    k: Float = 1.05f,
): TextUnit {
    val effective =
        text.fold(0f) { acc, c ->
            acc +
                when (c) {
                    '.' -> 0.4f
                    in ":'\""  -> 0.15f
                    in "hms" -> 0.25f
                    else -> 1.0f
                }
        }
    if (effective <= baseChars) return fontSizeBase.sp
    return (fontSizeBase * k * baseChars / effective).toInt().coerceAtMost(fontSizeBase).sp
}
