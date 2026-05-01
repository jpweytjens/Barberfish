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
private const val TWO_ROWS = 30     // 2 rows    (60/30)
private const val THREE_ROWS = 20   // 3 rows    (60/20)
private const val FOUR_ROWS = 15    // 4 rows    (60/15)
private const val FIVE_ROWS = 12    // 5 rows    (60/12)

// Empirical font metrics for `fontFamily="relative"` (Karoo monospace) at
// `includeFontPadding=false`, measured at runtime via `Paint.getFontMetrics()`
// — see `FontMetricsProbe.kt`. Captured on Karoo 3:
//
//   ascent  = -1.000 × textSize   (baseline → typographic top)
//   descent = +0.200 × textSize
//   view_h  =  1.200 × textSize   (descent − ascent)
//
// `wrap_content` TextView height equals `descent − ascent` = 1.2 × textSize.
// Baseline within view = `-ascent` = 1.0 × textSize. With layout_centerVertical
// the baseline therefore sits `0.4 × textSize` BELOW the view's centre
// (1.0 − 1.2/2 = 0.4).
private const val VALUE_VIEW_HEIGHT_RATIO = 1.20f
private const val VALUE_DESCENT_RATIO = 0.20f
private const val VALUE_BASELINE_OFFSET_RATIO = 0.40f  // baseline below view-center, normalised to textSize

/**
 * Fallback `baseline_box` height in dp when the live `cellHeightPx` is not
 * available (e.g. preview rendering). Empirical measurements with the key
 * bar enabled. Used when `cellHeightPxOverride` is null in
 * `clipSafeBaselineRefSp`.
 */
private fun fallbackBoxHeightDp(colSpan: Int, rowSpan: Int): Int = when {
    colSpan == ONE_COL && rowSpan >= 60 -> 317   // 1×1
    colSpan == ONE_COL && rowSpan >= 30 -> 150   // 2×1
    colSpan == ONE_COL && rowSpan >= 20 -> 88    // 3×1
    colSpan == ONE_COL && rowSpan >= 15 -> 60    // 4×1
    colSpan == ONE_COL && rowSpan >= 12 -> 45    // 5×1
    colSpan == TWO_COLS && rowSpan >= 15 -> 56   // 2×2 / 3×2 / 4×2
    colSpan == TWO_COLS && rowSpan >= 12 -> 45   // 5×2
    else -> 45
}

// Divisor used to derive the clip-safe cap from `box_h_dp`. Set to the font's
// view-height ratio (1.20) — exactly at the clipping threshold. Android may
// allocate an extra px or two to header_ref's minHeight, so cells with very
// tight box headroom (5×2, 5×1) may end up CLIPPED by 1-2 px even at this
// divisor; that's the practical limit of the layout_centerVertical approach.
private const val CLIP_SAFE_DIVISOR = 1.20f

/**
 * Maximum `baselineRefSp` that keeps `baseline_ref`'s `wrap_content` view
 * INSIDE `baseline_box` (i.e. unclipped). Once `baseline_ref` is clipped to
 * its parent extent, RelativeLayout centering geometry collapses: view_top
 * snaps to 0 and the baseline jumps to `1.0 × textSize` from box top,
 * pushing the value's visible cap-bottom past the cell edge.
 *
 * Constraint: `view_h_ref = 1.2 × ref_sp_px ≤ box_h_px`. Since digits have
 * no descenders, the binding constraint is simply that the ref view fits in
 * the box. `CLIP_SAFE_DIVISOR = 1.2` is exactly the font's view-height ratio.
 *
 * `cellHeightPx` is the live cell height from `ViewConfig.viewSize.second` —
 * propagated by `BarberfishDataType.startView`. When null (e.g. preview
 * rendering with no live SDK config), we fall back to a hardcoded
 * `(colSpan, rowSpan)` lookup measured with the key bar enabled.
 */
private fun clipSafeBaselineRefSp(
    colSpan: Int,
    rowSpan: Int,
    cellHeightPx: Float?,
    headerMinHeightDp: Int,
    density: Float,
): Float {
    val boxHeightDp = if (cellHeightPx != null) {
        (cellHeightPx / density - headerMinHeightDp).coerceAtLeast(20f)
    } else {
        fallbackBoxHeightDp(colSpan, rowSpan).toFloat()
    }
    return (boxHeightDp / CLIP_SAFE_DIVISOR).coerceAtLeast(20f)
}

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
    cellHeightPx: Float? = null,
    density: Float = 1.875f,
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
    val paddingH = if (colSpan <= THREE_COLS) 2.dp else 4.dp
    // Native `dataHeader` style (styles.xml:3646) sets `minHeight=22dp`. The
    // header GROWS naturally past that floor because the label is `lines=2`
    // (pre-allocates two lines of vertical space) — that's what supplies the
    // 5x2-narrow extra room without a per-layout override here.
    val headerMinHeightDp = 22
    val valueFontBase = textSizeEff.coerceAtLeast(20)
    // Bitmap height for field_value rendered as an ARGB image. Constant per
    // layout (= 0.8 × valueFontBase rounded to dp). Independent of the
    // per-render `fontSizeForCell` shrunk size — gives a stable baseline
    // across content-driven font-size changes once Task 4 wires the bitmap
    // path. Value chosen so the visible digit cap (≈ 0.7 × textSize) fits
    // with ~0.1 × textSize of buffer above; baseline = bitmap bottom.
    val valueBitmapHeightDp = (0.8f * valueFontBase).toInt().coerceAtLeast(16)
    // baseline_ref font (sp) per layout. Controls the baseline position via
    // layout_centerVertical inside baseline_box. Larger → baseline lower
    // (value appears lower). Smaller → baseline higher.
    //
    // Seeded from valueFontSizeBase, then capped to a clip-safe ceiling
    // (see `clipSafeBaselineRefSp`). Without the cap, narrow layouts like
    // 1×5 (60, 12) and 2×5 (30, 12) had ref_h > baseline_box height; the
    // RelativeLayout clipped baseline_ref to its parent extent, the
    // centering geometry collapsed, and the value glyph's descent was
    // pushed past the cell bottom (visible bottom-edge clipping).
    // Per-layout `baselineRefSp` target. Lower than `valueFontBase` shrinks
    // baseline_ref → baseline shifts UP (smaller view, more centering slack).
    // Always clamped by `clipSafeBaselineRefSp` so baseline_ref doesn't
    // exceed `baseline_box` and trigger Android's clipping regime where
    // centering math collapses. Values below tuned against the script's
    // measured Δvalue_baseline against native.
    val clipSafe = clipSafeBaselineRefSp(colSpan, rowSpan, cellHeightPx, headerMinHeightDp, density)
    val baselineRefSp: Float = valueFontBase.toFloat().coerceAtMost(clipSafe)

    // Extra paddingTop applied to baseline_ref. In the clipped regime
    // (ref_view_h ≈ box_h), Android pins view_top at 0 and paddingTop adds
    // directly to the baseline position — pushing baseline DOWN beyond what
    // baselineRefSp alone could (since further increasing sp would clip the
    // value glyph too). Tuned against measured Δvalue_baseline.
    val baselineRefPaddingTopDp: Int = when {
        // 2-col 4-row+: BF was 9 px above native, +5 dp paddingTop landed at -1 PASS.
        colSpan == TWO_COLS && rowSpan >= FOUR_ROWS -> 5
        // 5x2: dump cross-check showed BF baseline 12 px above native on
        // actual-value rows (native row 3 baseline=566, BF=554). +4 dp ≈ +7.5 px
        // shift down lands BF at ~561, within ±2 px of native 566.
        colSpan == TWO_COLS && rowSpan == FIVE_ROWS -> 4
        // 1-col layouts: visually BF baseline was above native's. Initial
        // estimates at 8-12 dp produced clipping; halved here. Tune up if
        // baselines still sit too high.
        colSpan == ONE_COL && rowSpan == THREE_ROWS -> 0   // 3x1
        colSpan == ONE_COL && rowSpan == FOUR_ROWS -> 4    // 4x1
        colSpan == ONE_COL && rowSpan == FIVE_ROWS -> 5    // 5x1
        else -> 0
    }
    return ViewSizeConfig.STANDARD.copy(
        colSpan = colSpan,
        rowSpan = rowSpan,
        paddingH = paddingH,
        valueFontSizeBase = valueFontBase,
        baselineRefSp = baselineRefSp,
        baselineRefPaddingTopDp = baselineRefPaddingTopDp,
        valueBitmapHeightDp = valueBitmapHeightDp,
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
    val baselineRefSp: Float = 49f,
    val baselineRefPaddingTopDp: Int = 0,
    val valueBitmapHeightDp: Int = 32,
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
