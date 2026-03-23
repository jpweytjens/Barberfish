package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.dynamicFontSp
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
    val colors = field.color.toColorConfig(colorMode)
    val rv = makeFieldRemoteViews(field, displayLabel, alignment, colors, sizeConfig, paddingHPx, context)

    // Background colour (zone / threshold / grade coloring in BACKGROUND mode)
    rv.setInt(R.id.field_root, "setBackgroundColor", colors.background?.toArgb() ?: android.graphics.Color.TRANSPARENT)

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
    val density = context.resources.displayMetrics.density
    val labelArgb =
        if (colors.background != null) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    val hGravity = alignment.toHorizontalGravity()
    val fontSp = dynamicFontSp(field.primary, sizeConfig.valueFontSizeBase).value

    if (DEBUG_LAYOUT) {
        Log.d("Barberfish", "makeFieldRemoteViews: label='$displayLabel' text='${field.primary}'" +
            " fontSp=$fontSp valueFontSizeBase=${sizeConfig.valueFontSizeBase}" +
            " headerFontSp=${sizeConfig.headerFontSize.value} headerIconSizeDp=${sizeConfig.headerIconSize.value}" +
            " hGravity=$hGravity paddingHPx=$paddingHPx")
    }

    val rv = RemoteViews(context.packageName, R.layout.barberfish_field)

    if (DEBUG_LAYOUT) {
        rv.setInt(R.id.field_header, "setBackgroundColor", 0x55FF0000.toInt())
        rv.setInt(R.id.field_value, "setBackgroundColor", 0x5500FF00.toInt())
    }

    rv.setViewPadding(R.id.field_root, paddingHPx, 0, paddingHPx, 0)

    // Label
    rv.setTextViewText(R.id.field_label, displayLabel)
    rv.setTextColor(R.id.field_label, labelArgb)
    rv.setTextViewTextSize(R.id.field_label, TypedValue.COMPLEX_UNIT_SP, sizeConfig.headerFontSize.value)
    // Wide cells: collapse to 1 line (setLines overrides android:lines="2" from XML).
    // Narrow cells: XML lines="2" stays, preserving 2-line reserved height for long labels.
    if (sizeConfig.labelMaxLines == 1) {
        rv.setInt(R.id.field_label, "setLines", 1)
    }
    rv.setInt(R.id.field_label, "setGravity", android.view.Gravity.CENTER_VERTICAL or hGravity)

    // Icon
    if (field.iconRes != null) {
        val gapPx = (sizeConfig.headerIconLabelGap.value * density).toInt()
        rv.setViewVisibility(R.id.field_icon, View.VISIBLE)
        rv.setImageViewResource(R.id.field_icon, field.iconRes)
        rv.setInt(R.id.field_icon, "setColorFilter", colors.iconTint.toArgb())
        rv.setViewPadding(R.id.field_label, gapPx, 0, 0, 0)
        // setViewLayoutWidth/Height requires API 31; Karoo 3 is API 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setViewLayoutWidth(R.id.field_icon, sizeConfig.headerIconSize.value, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(R.id.field_icon, sizeConfig.headerIconSize.value, TypedValue.COMPLEX_UNIT_DIP)
        }
    } else {
        rv.setViewVisibility(R.id.field_icon, View.GONE)
        rv.setViewPadding(R.id.field_label, 0, 0, 0, 0)
    }

    // Value — layout_centerVertical in XML handles vertical centering; only horizontal gravity here.
    rv.setTextViewText(R.id.field_value, field.primary)
    rv.setTextColor(R.id.field_value, colors.valueText.toArgb())
    rv.setTextViewTextSize(R.id.field_value, TypedValue.COMPLEX_UNIT_SP, fontSp)
    rv.setInt(R.id.field_value, "setGravity", android.view.Gravity.CENTER_VERTICAL or hGravity)
    if (sizeConfig.valueTranslationY != 0f) {
        rv.setFloat(R.id.field_value, "setTranslationY", sizeConfig.valueTranslationY)
    }

    return rv
}

private fun ViewConfig.Alignment.toHorizontalGravity(): Int =
    when (this) {
        ViewConfig.Alignment.LEFT -> android.view.Gravity.START
        ViewConfig.Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
        ViewConfig.Alignment.RIGHT -> android.view.Gravity.END
    }
