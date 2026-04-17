package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt
import android.graphics.Paint
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
        rv.setInt(R.id.field_header, "setBackgroundColor", 0x55FF0000.toInt())
        rv.setInt(R.id.field_value, "setBackgroundColor", 0x5500FF00.toInt())
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
    // XML default is android:lines="2"; only override when 1 line is needed.
    if (labelLines == 1) rv.setInt(R.id.field_label, "setLines", 1)

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

    // Value — gravity="bottom" in child layout, translationY baked into XML.
    // Baseline = actualContainerBottom - baselineMarginPx, independent of cellH.
    // The child is added via addView to value_container with the right translationY layout.
    val headerPadPx = headerHeightPx(sizeConfig.headerFontSize.value, labelLines, density)
    val valueFm = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = fontSp * density
    }.fontMetrics
    val translationYPx = (valueFm.descent - sizeConfig.baselineMarginPx).roundToInt().coerceIn(0, MAX_TRANSLATION_Y)
    val hAlign = alignment.toTextAlignment()

    if (DEBUG_LAYOUT) {
        Log.d("Barberfish", buildString {
            append("VALUE POS: fontSp=$fontSp")
            append(" translationY=$translationYPx descent=${valueFm.descent.roundToInt()}")
            append(" baselineMargin=${sizeConfig.baselineMarginPx.roundToInt()}")
        })
    }

    val valueRv = RemoteViews(context.packageName, translationYLayout(translationYPx))
    valueRv.setTextViewText(R.id.field_value, field.primary)
    valueRv.setTextColor(R.id.field_value, colors.valueText.toArgb())
    valueRv.setTextViewTextSize(R.id.field_value, TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())
    valueRv.setInt(R.id.field_value, "setTextAlignment", hAlign)
    if (maxLines == 2) {
        valueRv.setInt(R.id.field_value, "setMaxLines", 2)
    }
    rv.removeAllViews(R.id.value_container)
    rv.addView(R.id.value_container, valueRv)

    // Stream state overlay: ibm-plex-sans-condensed, white — replaces value_container for
    // SDK non-Streaming states (Searching / NotAvailable / Idle). Font capped at 19sp.
    // Size computed from the longest state text so baselines align across HUD slots.
    if (field.color is FieldColor.StreamState) {
        val (stateFont, stateMaxLines) = fontSizeForCell(
            "Not available", sizeConfig.valueFontSizeBase, cellWidthPx, density,
            wrapThresholdSp = sizeConfig.wrapThresholdSp,
        )
        rv.setViewVisibility(R.id.value_container, View.GONE)
        rv.setViewVisibility(R.id.stream_state_tv, View.VISIBLE)
        rv.setTextViewText(R.id.stream_state_tv, field.primary)
        rv.setTextColor(R.id.stream_state_tv, colors.valueText.toArgb())
        rv.setTextViewTextSize(R.id.stream_state_tv, TypedValue.COMPLEX_UNIT_SP, stateFont.coerceAtMost(19).toFloat())
        rv.setViewPadding(R.id.stream_state_tv, 0, headerPadPx, 0, 0)
        if (stateMaxLines == 2) {
            rv.setInt(R.id.stream_state_tv, "setMaxLines", 2)
        }
    } else {
        rv.setViewVisibility(R.id.value_container, View.VISIBLE)
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

private fun ViewConfig.Alignment.toTextAlignment(): Int =
    when (this) {
        ViewConfig.Alignment.LEFT -> View.TEXT_ALIGNMENT_VIEW_START
        ViewConfig.Alignment.CENTER -> View.TEXT_ALIGNMENT_CENTER
        ViewConfig.Alignment.RIGHT -> View.TEXT_ALIGNMENT_VIEW_END
    }

private const val MAX_TRANSLATION_Y = 32

private val translationYLayouts = intArrayOf(
    R.layout.barberfish_value_ty0,
    R.layout.barberfish_value_ty1,
    R.layout.barberfish_value_ty2,
    R.layout.barberfish_value_ty3,
    R.layout.barberfish_value_ty4,
    R.layout.barberfish_value_ty5,
    R.layout.barberfish_value_ty6,
    R.layout.barberfish_value_ty7,
    R.layout.barberfish_value_ty8,
    R.layout.barberfish_value_ty9,
    R.layout.barberfish_value_ty10,
    R.layout.barberfish_value_ty11,
    R.layout.barberfish_value_ty12,
    R.layout.barberfish_value_ty13,
    R.layout.barberfish_value_ty14,
    R.layout.barberfish_value_ty15,
    R.layout.barberfish_value_ty16,
    R.layout.barberfish_value_ty17,
    R.layout.barberfish_value_ty18,
    R.layout.barberfish_value_ty19,
    R.layout.barberfish_value_ty20,
    R.layout.barberfish_value_ty21,
    R.layout.barberfish_value_ty22,
    R.layout.barberfish_value_ty23,
    R.layout.barberfish_value_ty24,
    R.layout.barberfish_value_ty25,
    R.layout.barberfish_value_ty26,
    R.layout.barberfish_value_ty27,
    R.layout.barberfish_value_ty28,
    R.layout.barberfish_value_ty29,
    R.layout.barberfish_value_ty30,
    R.layout.barberfish_value_ty31,
    R.layout.barberfish_value_ty32,
)

private fun translationYLayout(px: Int): Int = translationYLayouts[px.coerceIn(0, MAX_TRANSLATION_Y)]
