package com.jpweytjens.barberfish.datatype.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.LabelSize
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

// Native 2-col (colSpan=30) sizes, selected by the rider's Label Size setting.
// Measured on-device (Karoo 3, density 1.875): Small = 29 px label / ~88 px value,
// Large = 33 px label / 78 px value. See docs/sdk-findings.md and the 2026-06-07 spec.
internal fun twoColLabelSp(large: Boolean): Float = if (large) 17.6f else 15.5f
internal fun twoColValueBase(large: Boolean): Int = if (large) 41 else 47

// Per-layout label sp mirrors the native field-header sizes measured on-device;
// see `docs/sdk-findings.md` § "Native label font sizes".
fun ViewConfig.toViewSizeConfig(
    colSpanOverride: Int? = null,
    textSizeOverride: Int? = null,
    design: DataFieldDesignConfig = DataFieldDesignConfig(),
): ViewSizeConfig {
    val colSpan = colSpanOverride ?: gridSize.first
    val rowSpan = gridSize.second
    val textSizeEff = textSizeOverride ?: textSize
    val labelSp: Float =
        when {
            colSpan == ONE_COL && rowSpan >= FOUR_ROWS -> 19.2f // 36 px
            colSpan == ONE_COL && rowSpan >= FIVE_ROWS -> 17.6f // 33 px
            colSpan == TWO_COLS ->
                twoColLabelSp(design.labelSize == LabelSize.LARGE)
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
    val valueBitmapHeightDp = (VALUE_BITMAP_HEIGHT_RATIO * valueFontBase).toInt().coerceAtLeast(16)
    // Matches the small upward translation observed in native narrow-cell
    // layouts. Applied via XML-baked android:translationY (see
    // BarberfishView.layoutRes).
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
        showIcons = design.showIcons,
    )
}

// Preview-only: apply the design settings to a fixed preview ViewSizeConfig.
// On-device the value font follows ViewConfig.textSize; previews have no SDK textSize,
// so for 2-col preview cells we also set the value base to the native Small/Large value.
fun ViewSizeConfig.withDesign(design: DataFieldDesignConfig): ViewSizeConfig {
    val large = design.labelSize == LabelSize.LARGE
    val labelSp = twoColLabelSp(large)
    val labelBandDp =
        if (labelMaxLines == 1) labelSp * 1.2f
        else labelSp * 1.2f * (1f + (labelMaxLines - 1) * 0.6f)
    return copy(
        showIcons = design.showIcons,
        headerFontSize = labelSp.sp,
        headerIconSize = labelSp.dp,
        headerMinHeightDp = maxOf(26, labelBandDp.toInt()),
        valueFontSizeBase = twoColValueBase(large),
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
    val showIcons: Boolean = true,
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
                valueBitmapHeightDp = 36,
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
