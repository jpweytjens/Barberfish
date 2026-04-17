package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hammerhead.karooext.models.ViewConfig

// Grid span constants: 60-unit internal grid, divided by span gives column/row count.
private const val ONE_COL = 60      // 1 column  (60/60)
private const val TWO_COLS = 30     // 2 columns (60/30)
private const val THREE_COLS = 20   // 3 columns (60/20, HUD 3-col)
private const val FOUR_COLS = 15    // 4 columns (60/15, HUD 4-col)
private const val THREE_ROWS = 20   // 3 rows    (60/20)
private const val FOUR_ROWS = 15    // 4 rows    (60/15)
private const val FIVE_ROWS = 12    // 5 rows    (60/12)

// Native rideapp (hhv5.d.hha) uses a hardcoded px lookup keyed on (colSpan, rowSpan).
// Converted to sp at Karoo 3 density (1.875 = 300 dpi / 160):
//
//   colSpan  rowSpan  labelPx  labelSp  example          textSize (sp)
//   60       ≥ 15     36 px    19.2 sp  1-col 3/4-row    69 – 96
//   60       ≥ 12     33 px    17.6 sp  1-col 5-row      55
//   30       ≥ 15     33 px    17.6 sp  2-col 4-row      50
//   30       ≥ 12     29 px    15.5 sp  2-col 5-row      47
//   20       ≥ 12     —        12.0 sp  HUD slot (1/3 of 60-wide cell)  [dynamic base]
//   15       ≥ 12     —        11.0 sp  4-col HUD slot (1/4 of 60-wide cell)  [dynamic base]
//
// Icon size equals labelSize in the native app (same px value, width = height).
// Native root cell padding is 0dp. Header wraps content; value fills remaining space, centered.
fun ViewConfig.toViewSizeConfig(
    colSpanOverride: Int? = null,
    textSizeOverride: Int? = null,
): ViewSizeConfig {
    val colSpan = colSpanOverride ?: gridSize.first
    val rowSpan = gridSize.second
    val textSizeEff = textSizeOverride ?: textSize
    val labelSp: Float =
        when {
            colSpan == ONE_COL && rowSpan >= FOUR_ROWS -> 19.2f // 36 px
            colSpan == ONE_COL && rowSpan >= FIVE_ROWS -> 17.6f // 33 px
            colSpan == TWO_COLS && rowSpan >= FOUR_ROWS -> 17.6f // 33 px
            colSpan == TWO_COLS && rowSpan >= FIVE_ROWS -> 15.5f // 29 px
            colSpan == THREE_COLS && rowSpan >= FIVE_ROWS -> 12.0f // HUD slot (1/3)
            colSpan == FOUR_COLS && rowSpan >= FIVE_ROWS -> 11.0f // 4-col HUD slot (1/4)
            else -> 15.5f
        }
    val gapDp = maxOf(2, (labelSp * 0.2f).toInt())
    // Wide (1-col) cells have short labels that fit on one line; colSpan=20 HUD slots also use 1.
    val labelMaxLines = if (colSpan != TWO_COLS) 1 else 2
    // Threshold below which single-line shrinks enough to warrant 2-line wrapping.
    // Lower for narrow HUD slots (small font is acceptable there).
    val wrapThresholdSp = when {
        colSpan == ONE_COL    -> 22
        colSpan == TWO_COLS    -> 18
        colSpan == THREE_COLS   -> 14
        else                    -> 12  // 4-col HUD
    }
    // Minimum distance from cell bottom to value baseline (px).
    // Floor for the centering formula in BarberfishView — the formula scales margin
    // with cellH to approximate native centering, but never goes below this minimum.
    // These match the native baseline position in the smallest expected cells (key icons ON).
    val baselineMarginPx: Float =
        when {
            colSpan == ONE_COL && rowSpan >= FOUR_ROWS  -> 9f  // 1-col 3/4-row
            colSpan == ONE_COL && rowSpan >= FIVE_ROWS  -> 5f  // 1-col 5-row
            colSpan == TWO_COLS && rowSpan >= FOUR_ROWS -> 9f  // 2-col 4-row
            colSpan == TWO_COLS && rowSpan >= FIVE_ROWS -> 9f  // 2-col 5-row
            colSpan == THREE_COLS                       -> 5f  // HUD 3-col
            colSpan == FOUR_COLS                        -> 5f  // HUD 4-col
            else                                        -> 5f
        }
    val paddingH = if (colSpan <= THREE_COLS) 2.dp else 4.dp
    return ViewSizeConfig.STANDARD.copy(
        colSpan = colSpan,
        paddingH = paddingH,
        valueFontSizeBase = textSizeEff.coerceAtLeast(20),
        headerFontSize = labelSp.sp,
        headerIconSize = labelSp.dp,
        headerIconLabelGap = gapDp.dp,
        labelMaxLines = labelMaxLines,
        wrapThresholdSp = wrapThresholdSp,
        baselineMarginPx = baselineMarginPx,
    )
}

data class ViewSizeConfig(
    val colSpan: Int,
    val paddingH: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val labelMaxLines: Int,
    val wrapThresholdSp: Int,
    val valueFontSizeBase: Int,
    val baselineMarginPx: Float = 5f,
    val cellHeightPx: Float? = null,
    val cellWidthPxOverride: Float? = null,
) {
    companion object {
        val STANDARD =
            ViewSizeConfig(
                colSpan = TWO_COLS,
                paddingH = 4.dp,
                headerIconSize = 17.dp,
                headerIconLabelGap = 6.dp,
                headerFontSize = 17.sp,
                labelMaxLines = 2,
                wrapThresholdSp = 18,
                valueFontSizeBase = 49,
            )

        // On-device HUD 3-column slots (colSpan=20)
        val HUD_THREE =
            ViewSizeConfig(
                colSpan = THREE_COLS,
                paddingH = 2.dp,
                headerIconSize = 12.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 12.sp,
                labelMaxLines = 1,
                wrapThresholdSp = 14,
                valueFontSizeBase = 42,
            )

        // On-device HUD 4-column slots (colSpan=15)
        val HUD_FOUR =
            ViewSizeConfig(
                colSpan = FOUR_COLS,
                paddingH = 2.dp,
                headerIconSize = 11.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 11.sp,
                labelMaxLines = 1,
                wrapThresholdSp = 12,
                valueFontSizeBase = 40,
            )

        // Config-screen preview: smaller font sizes to fit the preview composable
        val PREVIEW_HUD_THREE = HUD_THREE.copy(valueFontSizeBase = 28, labelMaxLines = 2)
        val PREVIEW_HUD_FOUR = HUD_FOUR.copy(
            headerFontSize = 9.sp,
            valueFontSizeBase = 20,
            labelMaxLines = 2,
        )
    }
}
