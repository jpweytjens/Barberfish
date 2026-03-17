package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Returns the appropriate primary-value font size for a field.
 *
 * @param length Character count of the value string.
 * @param narrow true for 1/3-width columns (ThreeColumnView), false for full-width fields.
 */
fun dynamicFontSp(length: Int, narrow: Boolean = false): TextUnit =
    if (narrow) {
        if (length >= 4) 36.sp else 36.sp
    } else {
        when {
            length <= 5 -> 48.sp
            length <= 8 -> 38.sp
            else -> 30.sp
        }
    }
