package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.dynamicFontSp
import com.jpweytjens.barberfish.datatype.shared.toBackgroundColorProvider
import com.jpweytjens.barberfish.datatype.shared.toColorProvider
import com.jpweytjens.barberfish.datatype.shared.whiteText
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

@Composable
fun BarberfishView(
    field: FieldValue,
    alignment: ViewConfig.Alignment = ViewConfig.Alignment.CENTER,
    colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    narrow: Boolean = false,
    showLabel: Boolean = false,
) {
    if (showLabel) {
        val textAlign = alignment.toTextAlign()
        val horizontalAlign = alignment.toHorizontal()
        val showIcon = field.iconRes != null && alignment != ViewConfig.Alignment.CENTER

        val textColor: ColorProvider
        val cellModifier: GlanceModifier
        when (colorMode) {
            ZoneColorMode.BACKGROUND -> {
                textColor = whiteText
                val bg = field.color.toBackgroundColorProvider()
                cellModifier = if (bg != null) GlanceModifier.fillMaxHeight().background(bg) else GlanceModifier.fillMaxHeight()
            }
            ZoneColorMode.TEXT -> {
                textColor = field.color.toColorProvider()
                cellModifier = GlanceModifier.fillMaxHeight()
            }
        }

        Column(
            modifier = cellModifier,
            horizontalAlignment = horizontalAlign,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Label row: icon + CAPS label
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconRes = field.iconRes
                if (showIcon && iconRes != null) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(Color(0xFFAAAAAA))),
                        modifier = GlanceModifier.width(12.dp).height(12.dp),
                    )
                    Spacer(GlanceModifier.width(2.dp))
                }
                Text(
                    text = field.label.uppercase(),
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFFAAAAAA)), textAlign = textAlign, fontFamily = FontFamily.Monospace),
                )
            }
            val valueSp = dynamicFontSp(field.primary.length, narrow = narrow)
            Text(
                text = field.primary,
                style = TextStyle(fontSize = valueSp, fontWeight = FontWeight.Bold, color = textColor, textAlign = textAlign, fontFamily = FontFamily.Monospace),
            )
        }
    } else {
        val color = when (colorMode) {
            ZoneColorMode.BACKGROUND -> whiteText
            ZoneColorMode.TEXT -> field.color.toColorProvider()
        }
        val horizontalAlign = when (alignment) {
            ViewConfig.Alignment.LEFT   -> Alignment.Start
            ViewConfig.Alignment.CENTER -> Alignment.CenterHorizontally
            ViewConfig.Alignment.RIGHT  -> Alignment.End
        }
        val contentAlign = when (alignment) {
            ViewConfig.Alignment.LEFT   -> Alignment.BottomStart
            ViewConfig.Alignment.CENTER -> Alignment.BottomCenter
            ViewConfig.Alignment.RIGHT  -> Alignment.BottomEnd
        }
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = horizontalAlign,
            verticalAlignment = Alignment.Bottom,
        ) {
            val valueSp = dynamicFontSp(field.primary.length)
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(42.dp).padding(end = 4.dp),
                contentAlignment = contentAlign,
            ) {
                Text(
                    text = field.primary,
                    style = TextStyle(fontSize = valueSp, fontWeight = FontWeight.Normal, color = color, fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

private fun ViewConfig.Alignment.toHorizontal(): Alignment.Horizontal = when (this) {
    ViewConfig.Alignment.LEFT   -> Alignment.Start
    ViewConfig.Alignment.CENTER -> Alignment.CenterHorizontally
    ViewConfig.Alignment.RIGHT  -> Alignment.End
}

private fun ViewConfig.Alignment.toTextAlign(): TextAlign = when (this) {
    ViewConfig.Alignment.LEFT   -> TextAlign.Left
    ViewConfig.Alignment.CENTER -> TextAlign.Center
    ViewConfig.Alignment.RIGHT  -> TextAlign.Right
}
