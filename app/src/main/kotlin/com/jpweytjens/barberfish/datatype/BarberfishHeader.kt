package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import io.hammerhead.karooext.models.ViewConfig

internal fun barberfishHeaderRemoteViews(
    label: String,
    colors: ColorConfig,
    alignment: ViewConfig.Alignment,
    config: ViewSizeConfig,
    context: Context,
): RemoteViews {
    val labelColorArgb =
        if (colors.background != null) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    val paint =
        Paint().apply {
            textSize =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    config.headerFontSize.value,
                    context.resources.displayMetrics,
                )
            typeface = Typeface.DEFAULT
        }
    val capBounds = android.graphics.Rect()
    paint.getTextBounds("A", 0, 1, capBounds)
    val descentPx = paint.fontMetrics.descent
    val spaceAboveCapsPx =
        capBounds.top - paint.fontMetrics.ascent
    val lineSpacingPx = config.headerLineSpacing.value * context.resources.displayMetrics.density
    return makeLabelRemoteViews(
        label = label,
        labelColorArgb = labelColorArgb,
        fontSizeSp = config.headerFontSize.value,
        line2TranslationY = -(descentPx + spaceAboveCapsPx) + lineSpacingPx,
        gravity = alignment.toGravity(),
        context = context,
    )
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
