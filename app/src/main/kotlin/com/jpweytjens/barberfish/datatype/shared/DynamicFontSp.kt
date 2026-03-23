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
 * Returns a font size that fits [text] in a cell designed for [fontSizeBase] sp at [baseChars]
 * characters. Narrow punctuation (`:`, `.`, `'`, `"`) is excluded from the character count. Use
 * [baseChars]=4 for narrow (2-col) cells, [baseChars]=7 for wide (1-col) cells. [k] scales the
 * result; tweak below 1f to reduce aggressiveness.
 */
fun dynamicFontSp(
    text: String,
    fontSizeBase: Int = 49,
    baseChars: Int = 4,
    k: Float = 1.25f,
): TextUnit {
    val effective = text.count { it !in ":.'\"" }
    if (effective <= baseChars) return fontSizeBase.sp
    return (fontSizeBase * k * baseChars / effective).toInt().coerceAtMost(fontSizeBase).sp
}

/**
 * Returns the implicit vertical gap between two adjacent Text composables at [upperSp] and
 * [lowerSp]: the sum of the descender of the upper font and the ascender of the lower font. Apply
 * as .padding(top = -fontBoundaryGap(...)) on the lower Box to collapse that gap.
 *
 * [typeface] should match the lower font's family (Typeface.MONOSPACE for BarberfishValue,
 * Typeface.DEFAULT for SansSerif label-to-label gaps).
 *
 * Note: requires a Context — not unit-testable with ./gradlew test. Verify on-device.
 */
fun fontBoundaryGap(
    upperSp: TextUnit,
    lowerSp: TextUnit,
    context: Context,
    typeface: Typeface = Typeface.DEFAULT,
): Dp {
    val displayMetrics = context.resources.displayMetrics
    fun fontMetrics(sp: Float) =
        Paint()
            .apply {
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics)
                this.typeface = typeface
            }
            .fontMetrics
    val upper = fontMetrics(upperSp.value)
    val lower = fontMetrics(lowerSp.value)
    val gapPx = upper.descent + (-lower.ascent)
    return (gapPx / displayMetrics.density).dp
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
