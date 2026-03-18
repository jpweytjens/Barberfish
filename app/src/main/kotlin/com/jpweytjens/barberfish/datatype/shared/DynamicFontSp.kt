package com.jpweytjens.barberfish.datatype.shared

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Returns a font size that fits [text] in a cell designed for [textSize] sp at 4 characters. Narrow
 * punctuation (`:`, `.`, `'`, `"`) counts as 0.5 glyphs. Default base of 36 sp is used for narrow
 * (three-column) columns.
 */
fun dynamicFontSp(text: String, narrow: Boolean = false): TextUnit {
    val defaultTextSize = if (narrow) 36 else 49

    // Narrow punctuation occupies less than a digit's width in monospace; weight accordingly.
    val effective = text.sumOf { if (it in ":.'\"") 0.10 else 1.0 }.coerceAtLeast(4.0)
    return (defaultTextSize * 4f / effective).toInt().sp
}

fun dynamicTopPadding(text: String, textSize: Int = 49, context: Context): Dp {
    val metrics = context.resources.displayMetrics

    fun fontHeightPx(sp: Float): Float {
        val paint =
            Paint().apply {
                this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
                typeface = Typeface.MONOSPACE
            }
        return paint.fontMetrics.descent - paint.fontMetrics.top
    }

    val refHeight = fontHeightPx(textSize.toFloat())
    val ourHeight = fontHeightPx(dynamicFontSp(text).value)

    // Magic 80% is to ensure smaller fonts sizes share the same bottom alignment
    // as larger fonts, by adding fontSize dependent padding above the text.
    // TODO: find a more principled way to calculate this padding, e.g. based on font metrics
    return ((refHeight - ourHeight) / metrics.density * 0.80f).coerceAtLeast(0f).dp
}
