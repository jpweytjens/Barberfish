package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.fillMaxWidth
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.FieldSizeConfig
import com.jpweytjens.barberfish.datatype.shared.dynamicFontSp
import io.hammerhead.karooext.models.ViewConfig

@Composable
internal fun BarberfishValue(
    text: String,
    alignment: ViewConfig.Alignment,
    colors: ColorConfig,
    config: FieldSizeConfig,
    modifier: GlanceModifier = GlanceModifier,
) {
    val ctx = LocalContext.current
    val rv = makeValueRemoteViews(text, colors, config, alignment, ctx)
    AndroidRemoteViews(remoteViews = rv, modifier = modifier.fillMaxWidth())
}

private fun makeValueRemoteViews(
    text: String,
    colors: ColorConfig,
    config: FieldSizeConfig,
    alignment: ViewConfig.Alignment,
    context: Context,
): RemoteViews {
    val displayMetrics = context.resources.displayMetrics
    val fontSp = dynamicFontSp(text, config.valueFontSizeBase).value
    val descentPx =
        Paint()
            .apply {
                textSize =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSp, displayMetrics)
                typeface = Typeface.MONOSPACE
            }
            .fontMetrics
            .descent
    val bottomPaddingPx = config.valueBottomPadding.value * displayMetrics.density

    val rv = RemoteViews(context.packageName, R.layout.barberfish_value)
    rv.setTextViewText(R.id.value_text, text)
    rv.setTextColor(R.id.value_text, colors.valueText.toArgb())
    rv.setTextViewTextSize(R.id.value_text, TypedValue.COMPLEX_UNIT_SP, fontSp)
    rv.setInt(R.id.value_text, "setGravity", alignment.toGravityBottom())
    rv.setFloat(R.id.value_text, "setTranslationY", descentPx - bottomPaddingPx)
    return rv
}

private fun ViewConfig.Alignment.toGravityBottom(): Int =
    android.view.Gravity.BOTTOM or
        when (this) {
            ViewConfig.Alignment.LEFT -> android.view.Gravity.START
            ViewConfig.Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
            ViewConfig.Alignment.RIGHT -> android.view.Gravity.END
        }
