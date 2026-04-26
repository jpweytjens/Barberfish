package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.fontSizeForCell
import com.jpweytjens.barberfish.datatype.shared.headerHeightPx
import com.jpweytjens.barberfish.datatype.shared.toColorConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

private const val DEBUG_LAYOUT = false

fun barberfishFieldRemoteViews(
    field: FieldState,
    alignment: ViewConfig.Alignment,
    colorMode: ZoneColorMode,
    sizeConfig: ViewSizeConfig,
    preview: Boolean,
    context: Context,
): RemoteViews {
    val dm = context.resources.displayMetrics
    val paddingHPx = (sizeConfig.paddingH.value * dm.density).toInt()
    // Always collapse \n to space — maxLines=2 + breakStrategy=simple in XML handles line breaking.
    val displayLabel = field.label.replace("\n", " ")
    val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val colors = field.color.toColorConfig(colorMode, isNightMode)
    val rv =
        makeFieldRemoteViews(
            field,
            displayLabel,
            alignment,
            colors,
            sizeConfig,
            paddingHPx,
            context,
        )

    // Background colour (zone / threshold / grade coloring in BACKGROUND mode)
    rv.setInt(
        R.id.field_root,
        "setBackgroundColor",
        colors.background?.toArgb() ?: android.graphics.Color.TRANSPARENT,
    )

    // Corner radius for config-screen preview (API 31+ / Karoo 3 is API 33)
    if (preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rv.setViewOutlinePreferredRadius(R.id.field_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
        rv.setBoolean(R.id.field_root, "setClipToOutline", true)
    }

    return rv
}

private fun makeFieldRemoteViews(
    field: FieldState,
    displayLabel: String,
    alignment: ViewConfig.Alignment,
    colors: ColorConfig,
    sizeConfig: ViewSizeConfig,
    paddingHPx: Int,
    context: Context,
): RemoteViews {
    val dm = context.resources.displayMetrics
    val density = dm.density
    val labelArgb = colors.headerText.toArgb()
    val layoutRes = alignment.toLayoutRes()
    val cellWidthPx = sizeConfig.cellWidthPxOverride?.let { it - 2 * paddingHPx }
        ?: (dm.widthPixels.toFloat() * sizeConfig.colSpan / 60f - 2 * paddingHPx)
    val numIcons = (if (field.iconRes != null) 1 else 0) + (if (field.secondaryIconRes != null) 1 else 0)
    val iconWidthPx = if (numIcons > 0)
        (numIcons * sizeConfig.headerIconSize.value + sizeConfig.headerIconLabelGap.value) * density
    else 0f
    val labelAvailableWidthPx = cellWidthPx - iconWidthPx
    val (fontSp, maxLines) = fontSizeForCell(
        field.primary, sizeConfig.valueFontSizeBase, cellWidthPx, density,
        wrapThresholdSp = sizeConfig.wrapThresholdSp,
    )

    if (DEBUG_LAYOUT) {
        Log.d(
            "Barberfish",
            "makeFieldRemoteViews: label='$displayLabel' text='${field.primary}'" +
                " fontSp=$fontSp valueFontSizeBase=${sizeConfig.valueFontSizeBase}" +
                " headerFontSp=${sizeConfig.headerFontSize.value} headerIconSizeDp=${sizeConfig.headerIconSize.value}" +
                " alignment=$alignment paddingHPx=$paddingHPx",
        )
    }

    val rv = RemoteViews(context.packageName, layoutRes)

    if (DEBUG_LAYOUT) {
        rv.setInt(R.id.field_header, "setBackgroundColor", 0x55FF0000.toInt())  // red: header
        rv.setInt(R.id.field_value, "setBackgroundColor", 0x5500FF00.toInt())   // green: value
        rv.setViewVisibility(R.id.header_spacer, View.VISIBLE)
        rv.setInt(R.id.header_spacer, "setBackgroundColor", 0x550000FF.toInt()) // blue: spacer
        rv.setInt(R.id.header_spacer, "setMinimumWidth", 500)
        rv.setViewVisibility(R.id.baseline_ref, View.VISIBLE)
        rv.setInt(R.id.baseline_ref, "setBackgroundColor", 0x55FFFF00.toInt())  // yellow: centering box
        rv.setInt(R.id.baseline_ref, "setMinimumWidth", 500)
    }

    rv.setViewPadding(R.id.field_root, paddingHPx, 0, paddingHPx, 0)

    // Label — HUD slots: dynamic sizing from headerFontSize base using DEFAULT typeface.
    // Regular (1/2-col): font and line count stay fixed from sizeConfig.
    val labelFontSp: Float
    val labelLines: Int
    if (sizeConfig.colSpan < 30) {
        val (sp, _) = fontSizeForCell(
            displayLabel,
            sizeConfig.headerFontSize.value.toInt(),
            labelAvailableWidthPx,
            density,
            wrapThresholdSp = sizeConfig.wrapThresholdSp,
            typeface = Typeface.DEFAULT,
        )
        labelFontSp = sp.toFloat()
        labelLines = 2  // all HUD slots always reserve 2-line height for consistent alignment
    } else {
        labelFontSp = sizeConfig.headerFontSize.value
        labelLines = sizeConfig.labelMaxLines
    }
    rv.setTextViewText(R.id.field_label, displayLabel)
    rv.setTextColor(R.id.field_label, labelArgb)
    rv.setTextViewTextSize(R.id.field_label, TypedValue.COMPLEX_UNIT_SP, labelFontSp)
    // XML default is android:lines="1"; override to 2 for HUD slots.
    if (labelLines == 2) rv.setInt(R.id.field_label, "setLines", 2)

    // Icons
    val gapPx = (sizeConfig.headerIconLabelGap.value * density).toInt()
    val iconSizePx = (sizeConfig.headerIconSize.value * density).toInt()
    if (field.iconRes != null) {
        rv.setViewVisibility(R.id.field_icon, View.VISIBLE)
        rv.setImageViewResource(R.id.field_icon, field.iconRes)
        rv.setInt(R.id.field_icon, "setColorFilter", colors.iconTint.toArgb())
        rv.setInt(R.id.field_icon, "setMaxWidth", iconSizePx)
        rv.setInt(R.id.field_icon, "setMaxHeight", iconSizePx)
    } else {
        rv.setViewVisibility(R.id.field_icon, View.GONE)
    }
    if (field.secondaryIconRes != null) {
        rv.setViewVisibility(R.id.field_icon_secondary, View.VISIBLE)
        rv.setImageViewResource(R.id.field_icon_secondary, field.secondaryIconRes)
        rv.setInt(R.id.field_icon_secondary, "setColorFilter", colors.iconTint.toArgb())
        rv.setInt(R.id.field_icon_secondary, "setMaxWidth", iconSizePx)
        rv.setInt(R.id.field_icon_secondary, "setMaxHeight", iconSizePx)
    } else {
        rv.setViewVisibility(R.id.field_icon_secondary, View.GONE)
    }
    // Gap between icon and label: side depends on alignment (left layout has label before icons)
    if (numIcons > 0) {
        if (alignment == ViewConfig.Alignment.LEFT) {
            rv.setViewPadding(R.id.field_label, 0, 0, gapPx, 0)
        } else {
            rv.setViewPadding(R.id.field_label, gapPx, 0, 0, 0)
        }
    } else {
        rv.setViewPadding(R.id.field_label, 0, 0, 0, 0)
    }

    // baseline_ref is inside baseline_box (a nested RelativeLayout that fills from the
    // header_spacer to the cell bottom). layout_centerVertical + gravity=center_vertical
    // + includeFontPadding=true centers the reference text within baseline_box.
    // baselineRefSp is a per-layout lookup (ViewSizeConfig) — tune visually per grid
    // size to match native centering. field_value.alignBaseline locks to this baseline
    // across fontSizeForCell shrinks. The layout engine re-centers on cell resize
    // (key icons, rerouting toast) automatically.
    rv.setTextViewTextSize(
        R.id.baseline_ref,
        TypedValue.COMPLEX_UNIT_SP,
        sizeConfig.baselineRefSp,
    )
    rv.setTextViewText(R.id.field_value, field.primary)
    rv.setTextColor(R.id.field_value, colors.valueText.toArgb())
    rv.setTextViewTextSize(R.id.field_value, TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())
    if (maxLines == 2) {
        rv.setInt(R.id.field_value, "setMaxLines", 2)
    }

    // Stream state overlay: ibm-plex-sans-condensed, white — replaces field_value for
    // SDK non-Streaming states (Searching / NotAvailable / Idle). Font capped at 19sp.
    // Size computed from "Searching…" — the widest single-line stream state (no space
    // to wrap on), so all other stream states fit at the same font size.
    if (field.color is FieldColor.StreamState) {
        val (stateFont, stateMaxLines) = fontSizeForCell(
            "Searching...", sizeConfig.valueFontSizeBase, cellWidthPx, density,
            wrapThresholdSp = sizeConfig.wrapThresholdSp,
        )
        rv.setViewVisibility(R.id.field_value, View.GONE)
        rv.setViewVisibility(R.id.baseline_ref, View.GONE)
        rv.setViewVisibility(R.id.stream_state_tv, View.VISIBLE)
        rv.setTextViewText(R.id.stream_state_tv, field.primary)
        rv.setTextColor(R.id.stream_state_tv, colors.valueText.toArgb())
        rv.setTextViewTextSize(R.id.stream_state_tv, TypedValue.COMPLEX_UNIT_SP, stateFont.coerceAtMost(19).toFloat())
        val actualHeaderPx = headerHeightPx(sizeConfig.headerFontSize.value, labelLines, density)
        rv.setViewPadding(R.id.stream_state_tv, 0, actualHeaderPx, 0, 0)
        if (stateMaxLines == 2) {
            rv.setInt(R.id.stream_state_tv, "setMaxLines", 2)
        }
    } else {
        rv.setViewVisibility(R.id.field_value, View.VISIBLE)
        rv.setViewVisibility(R.id.stream_state_tv, View.GONE)
    }

    return rv
}

private fun ViewConfig.Alignment.toLayoutRes(): Int =
    when (this) {
        ViewConfig.Alignment.LEFT -> R.layout.barberfish_field_left
        ViewConfig.Alignment.CENTER -> R.layout.barberfish_field_center
        ViewConfig.Alignment.RIGHT -> R.layout.barberfish_field
    }

