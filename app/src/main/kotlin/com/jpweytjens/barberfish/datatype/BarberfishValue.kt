package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.dynamicFontSp
import io.hammerhead.karooext.models.ViewConfig

internal fun barberfishValueRemoteViews(
    text: String,
    alignment: ViewConfig.Alignment,
    colors: ColorConfig,
    config: ViewSizeConfig,
    context: Context,
): RemoteViews {
    val fontSp = dynamicFontSp(text, config.valueFontSizeBase, config.baseChars).value
    val rv = RemoteViews(context.packageName, R.layout.barberfish_value)
    rv.setTextViewText(R.id.value_text, text)
    rv.setTextColor(R.id.value_text, colors.valueText.toArgb())
    rv.setTextViewTextSize(R.id.value_text, TypedValue.COMPLEX_UNIT_SP, fontSp)
    rv.setInt(R.id.value_text, "setGravity", alignment.toGravityCenter())
    return rv
}

private fun ViewConfig.Alignment.toGravityCenter(): Int =
    android.view.Gravity.CENTER_VERTICAL or
        when (this) {
            ViewConfig.Alignment.LEFT -> android.view.Gravity.START
            ViewConfig.Alignment.CENTER -> android.view.Gravity.CENTER_HORIZONTAL
            ViewConfig.Alignment.RIGHT -> android.view.Gravity.END
        }
