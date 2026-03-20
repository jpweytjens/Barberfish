package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import io.hammerhead.karooext.models.ViewConfig

@Composable
internal fun BarberfishHeader(
    label: String,
    iconRes: Int?,
    colors: ColorConfig,
    alignment: ViewConfig.Alignment,
    config: ViewSizeConfig,
    modifier: GlanceModifier = GlanceModifier,
) {
    val ctx = LocalContext.current
    val labelColorArgb =
        if (colors.background != null) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    val paint =
        Paint().apply {
            textSize =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    config.headerFontSize.value,
                    ctx.resources.displayMetrics,
                )
            typeface = Typeface.DEFAULT
        }
    val capBounds = android.graphics.Rect()
    paint.getTextBounds("A", 0, 1, capBounds)
    // collapse: descent of line1 (empty for all-caps) + space above caps in line2 (ascent −
    // capHeight),
    // then add 1dp back to leave a hair of breathing room between the two lines
    val descentPx = paint.fontMetrics.descent
    val spaceAboveCapsPx =
        capBounds.top - paint.fontMetrics.ascent // both negative, result positive
    val lineSpacingPx = config.headerLineSpacing.value * ctx.resources.displayMetrics.density
    val rv =
        makeLabelRemoteViews(
            label = label,
            labelColorArgb = labelColorArgb,
            fontSizeSp = config.headerFontSize.value,
            line2TranslationY = -(descentPx + spaceAboveCapsPx) + lineSpacingPx,
            gravity = alignment.toGravity(),
            context = ctx,
        )
    val isMultiLine = label.contains("\n")
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (iconRes != null) {
            BarberfishIcon(
                iconRes,
                colors,
                config.headerIconSize,
                topPadding = if (isMultiLine) config.headerIconTopPadding else 0.dp,
            )
            Spacer(GlanceModifier.width(config.headerIconLabelGap))
        }
        AndroidRemoteViews(remoteViews = rv, modifier = GlanceModifier.defaultWeight())
    }
}

private fun makeLabelRemoteViews(
    label: String,
    labelColorArgb: Int,
    fontSizeSp: Float,
    line2TranslationY: Float,
    gravity: Int,
    context: Context,
): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.barberfish_label)
    val lines = label.split("\n")
    rv.setTextViewText(R.id.label_line1, lines[0])
    rv.setTextColor(R.id.label_line1, labelColorArgb)
    rv.setTextViewTextSize(R.id.label_line1, TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
    rv.setInt(R.id.label_line1, "setGravity", gravity)
    if (lines.size >= 2) {
        rv.setViewVisibility(R.id.label_line2, View.VISIBLE)
        rv.setTextViewText(R.id.label_line2, lines[1])
        rv.setTextColor(R.id.label_line2, labelColorArgb)
        rv.setTextViewTextSize(R.id.label_line2, TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        rv.setInt(R.id.label_line2, "setGravity", gravity)
        rv.setFloat(R.id.label_line2, "setTranslationY", line2TranslationY)
    } else {
        rv.setViewVisibility(R.id.label_line2, View.GONE)
    }
    return rv
}

private fun ViewConfig.Alignment.toGravity(): Int =
    when (this) {
        ViewConfig.Alignment.LEFT -> android.view.Gravity.START
        ViewConfig.Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
        ViewConfig.Alignment.RIGHT -> android.view.Gravity.END
    }

@Composable
private fun BarberfishIcon(
    iconRes: Int,
    colors: ColorConfig,
    size: androidx.compose.ui.unit.Dp,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(
        modifier = GlanceModifier.width(size).height(size + topPadding).padding(top = topPadding),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(colors.iconTint)),
            modifier = GlanceModifier.width(size).height(size),
        )
    }
}
