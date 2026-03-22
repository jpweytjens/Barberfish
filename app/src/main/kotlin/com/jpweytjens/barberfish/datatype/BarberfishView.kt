package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.collapseIfFits
import com.jpweytjens.barberfish.datatype.shared.dynamicFontSp
import com.jpweytjens.barberfish.datatype.shared.toBackgroundColor
import com.jpweytjens.barberfish.datatype.shared.toColorConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

@Composable
fun BarberfishView(
    field: FieldState,
    alignment: ViewConfig.Alignment = ViewConfig.Alignment.CENTER,
    colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    sizeConfig: ViewSizeConfig = ViewSizeConfig.STANDARD,
    cornerRadius: Dp = 0.dp,
    wideLayout: Boolean = false,
    cellWidthPx: Int = 0,
    modifier: GlanceModifier = GlanceModifier,
) {
    val ctx = LocalContext.current
    val colors = field.color.toColorConfig(colorMode)
    val dm = ctx.resources.displayMetrics
    val paddingHPx = (sizeConfig.paddingH.value * dm.density).toInt()
    val displayLabel = when {
        wideLayout -> field.label.replace("\n", " ")
        cellWidthPx > 0 && field.label.contains("\n") -> {
            val iconWidth =
                if (field.iconRes != null)
                    (sizeConfig.headerIconSize.value + sizeConfig.headerIconLabelGap.value) * dm.density
                else 0f
            val availablePx = cellWidthPx - 2f * paddingHPx - iconWidth
            collapseIfFits(field.label, sizeConfig.headerFontSize.value, availablePx, dm)
        }
        else -> field.label
    }
    val rv = makeFieldRemoteViews(field, displayLabel, alignment, colors, sizeConfig, paddingHPx, ctx)
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .maybeBackground(colors.background)
                .maybeCornerRadius(cornerRadius),
        contentAlignment = Alignment.TopStart,
    ) {
        AndroidRemoteViews(remoteViews = rv, modifier = GlanceModifier.fillMaxWidth().fillMaxHeight())
    }
}

fun barberfishFieldRemoteViews(
    field: FieldState,
    alignment: ViewConfig.Alignment,
    colorMode: ZoneColorMode,
    sizeConfig: ViewSizeConfig,
    preview: Boolean,
    wideLayout: Boolean,
    cellWidthPx: Int,
    context: Context,
): RemoteViews {
    val dm = context.resources.displayMetrics
    val paddingHPx = (sizeConfig.paddingH.value * dm.density).toInt()
    val displayLabel = when {
        wideLayout -> field.label.replace("\n", " ")
        cellWidthPx > 0 && field.label.contains("\n") -> {
            val iconWidth =
                if (field.iconRes != null)
                    (sizeConfig.headerIconSize.value + sizeConfig.headerIconLabelGap.value) * dm.density
                else 0f
            val availablePx = cellWidthPx - 2f * paddingHPx - iconWidth
            collapseIfFits(field.label, sizeConfig.headerFontSize.value, availablePx, dm)
        }
        else -> field.label
    }
    val colors = field.color.toColorConfig(colorMode)
    val rv = makeFieldRemoteViews(field, displayLabel, alignment, colors, sizeConfig, paddingHPx, context)

    // Background colour (zone / threshold / grade coloring in BACKGROUND mode)
    val bgColor = if (colorMode == ZoneColorMode.BACKGROUND) field.color.toBackgroundColor() else null
    rv.setInt(R.id.field_root, "setBackgroundColor", bgColor?.toArgb() ?: android.graphics.Color.TRANSPARENT)

    // Corner radius for config-screen preview (API 31+ / Karoo 3 is API 33)
    if (preview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rv.setViewOutlinePreferredRadius(R.id.field_root, 12f, TypedValue.COMPLEX_UNIT_DIP)
        rv.setBoolean(R.id.field_root, "setClipToOutline", true)
    }

    // Native spacing: narrow two-line labels need translationY = -3px
    if (displayLabel.contains("\n")) {
        rv.setFloat(R.id.field_label, "setTranslationY", -3f)
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

    val rv = RemoteViews(context.packageName, R.layout.barberfish_field)

    rv.setViewPadding(R.id.field_root, paddingHPx, 0, paddingHPx, 0)

    // Label (single TextView, maxLines=2, lineSpacingMultiplier=0.6 in XML)
    rv.setTextViewText(R.id.field_label, displayLabel)
    rv.setTextColor(R.id.field_label, labelArgb)
    rv.setTextViewTextSize(R.id.field_label, TypedValue.COMPLEX_UNIT_SP, sizeConfig.headerFontSize.value)
    rv.setInt(R.id.field_label, "setGravity", hGravity)

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

    // Value
    val fontSp = dynamicFontSp(field.primary, sizeConfig.valueFontSizeBase).value
    rv.setTextViewText(R.id.field_value, field.primary)
    rv.setTextColor(R.id.field_value, colors.valueText.toArgb())
    rv.setTextViewTextSize(R.id.field_value, TypedValue.COMPLEX_UNIT_SP, fontSp)
    rv.setInt(R.id.field_value, "setGravity", android.view.Gravity.CENTER_VERTICAL or hGravity)

    return rv
}

private fun ViewConfig.Alignment.toHorizontalGravity(): Int =
    when (this) {
        ViewConfig.Alignment.LEFT -> android.view.Gravity.START
        ViewConfig.Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
        ViewConfig.Alignment.RIGHT -> android.view.Gravity.END
    }

private fun GlanceModifier.maybeBackground(bg: ColorProvider?): GlanceModifier =
    if (bg != null) background(bg) else this

private fun GlanceModifier.maybeCornerRadius(radius: Dp): GlanceModifier =
    if (radius > 0.dp) cornerRadius(radius) else this
