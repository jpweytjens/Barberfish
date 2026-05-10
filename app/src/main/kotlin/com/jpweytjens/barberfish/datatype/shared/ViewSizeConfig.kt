package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hammerhead.karooext.models.ViewConfig

// Grid spans: 60-unit internal grid; columns = 60 / colSpan, rows = 60 / rowSpan.
private const val ONE_COL = 60
private const val TWO_COLS = 30
private const val THREE_COLS = 20  // HUD 3-col
private const val FOUR_COLS = 15   // HUD 4-col
private const val TWO_ROWS = 30
private const val THREE_ROWS = 20
private const val FOUR_ROWS = 15
private const val FIVE_ROWS = 12

// Per-layout label sp mirrors native rideapp's DataElementConstraints lookup;
// see `docs/architecture.md` § "Value baseline alignment".
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
    val labelMaxLines = if (colSpan != TWO_COLS) 1 else 2
    val wrapThresholdSp = when {
        colSpan == ONE_COL    -> 22
        colSpan == TWO_COLS    -> 18
        colSpan == THREE_COLS   -> 14
        else                    -> 12
    }
    val paddingH = if (colSpan <= THREE_COLS) 2.dp else 4.dp
    // header_ref minHeight = max(icon-row floor 26 dp, label text band).
    // 2-line band uses native's lineSpacingMultiplier=0.6 for narrow cells.
    val labelBandDp = if (labelMaxLines == 1) {
        labelSp * 1.2f
    } else {
        labelSp * 1.2f * (1f + (labelMaxLines - 1) * 0.6f)
    }
    val headerMinHeightDp = maxOf(26, labelBandDp.toInt())
    val valueFontBase = textSizeEff.coerceAtLeast(20)
    val valueBitmapHeightDp = (0.74f * valueFontBase).toInt().coerceAtLeast(16)
    // Mirrors native's DataElementConstraints.dataTranslationY. Applied
    // via XML-baked android:translationY (see BarberfishView.layoutRes).
    val valueTranslationDp = when {
        colSpan == ONE_COL && rowSpan == FIVE_ROWS   -> -3   // 5×1
        else                                          -> 0
    }
    return ViewSizeConfig.STANDARD.copy(
        colSpan = colSpan,
        rowSpan = rowSpan,
        paddingH = paddingH,
        valueFontSizeBase = valueFontBase,
        valueBitmapHeightDp = valueBitmapHeightDp,
        valueTranslationDp = valueTranslationDp,
        headerFontSize = labelSp.sp,
        headerIconSize = labelSp.dp,
        headerIconLabelGap = gapDp.dp,
        headerMinHeightDp = headerMinHeightDp,
        labelMaxLines = labelMaxLines,
        wrapThresholdSp = wrapThresholdSp,
    )
}

data class ViewSizeConfig(
    val colSpan: Int,
    val rowSpan: Int,
    val paddingH: Dp,
    val headerIconSize: Dp,
    val headerIconLabelGap: Dp,
    val headerFontSize: TextUnit,
    val headerMinHeightDp: Int = 26,
    val labelMaxLines: Int,
    val wrapThresholdSp: Int,
    val valueFontSizeBase: Int,
    val valueBitmapHeightDp: Int = 32,
    val valueTranslationDp: Int = 0,
    val cellWidthPxOverride: Float? = null,
) {
    companion object {
        val STANDARD =
            ViewSizeConfig(
                colSpan = TWO_COLS,
                rowSpan = FOUR_ROWS,
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
                rowSpan = FIVE_ROWS,
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
                rowSpan = FIVE_ROWS,
                paddingH = 2.dp,
                headerIconSize = 11.dp,
                headerIconLabelGap = 2.dp,
                headerFontSize = 11.sp,
                labelMaxLines = 1,
                wrapThresholdSp = 12,
                valueFontSizeBase = 32,
            )

        // Config-screen preview: same value/header sizing as on-device.
        val PREVIEW_HUD_THREE = HUD_THREE.copy(labelMaxLines = 2)
        val PREVIEW_HUD_FOUR = HUD_FOUR.copy(labelMaxLines = 2)
    }
}
