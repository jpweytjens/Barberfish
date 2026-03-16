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
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

@Composable
fun ThreeColumnView(
    left: FieldValue,
    center: FieldValue,
    right: FieldValue,
    alignment: ViewConfig.Alignment = ViewConfig.Alignment.RIGHT,
    colorMode: ZoneColorMode = ZoneColorMode.TEXT,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColumnCell(left,   GlanceModifier.defaultWeight(), alignment, colorMode)
        ColumnCell(center, GlanceModifier.defaultWeight(), alignment, colorMode)
        ColumnCell(right,  GlanceModifier.defaultWeight(), alignment, colorMode)
    }
}

@Composable
private fun ColumnCell(
    field: FieldValue,
    modifier: GlanceModifier,
    alignment: ViewConfig.Alignment,
    colorMode: ZoneColorMode,
) {
    val textAlign = alignment.toTextAlign()
    val horizontalAlign = alignment.toHorizontal()
    val showIcon = field.iconRes != null && alignment != ViewConfig.Alignment.CENTER

    val textColor: ColorProvider
    val cellModifier: GlanceModifier
    when (colorMode) {
        ZoneColorMode.BACKGROUND -> {
            textColor = whiteText
            val bg = field.color.toBackgroundColorProvider()
            cellModifier = if (bg != null) modifier.fillMaxHeight().background(bg) else modifier.fillMaxHeight()
        }
        ZoneColorMode.TEXT -> {
            textColor = field.color.toColorProvider()
            cellModifier = modifier.fillMaxHeight()
        }
    }

    Column(
        modifier = cellModifier,
        horizontalAlignment = horizontalAlign,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label row: icon + CAPS label
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Image(
                    provider = ImageProvider(field.iconRes!!),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ColorProvider(Color(0xFFAAAAAA))),
                    modifier = GlanceModifier.width(12.dp).height(12.dp),
                )
                Spacer(GlanceModifier.width(2.dp))
            }
            Text(
                text = field.label.uppercase(),
                style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFFAAAAAA)), textAlign = textAlign),
            )
        }
        val valueSp = primaryFontSp(field.primary.length, narrow = true)
        Text(
            text = field.primary,
            style = TextStyle(fontSize = valueSp, fontWeight = FontWeight.Bold, color = textColor, textAlign = textAlign),
        )
        // Unit
        if (field.unit.isNotEmpty()) {
            Text(
                text = field.unit,
                style = TextStyle(fontSize = 11.sp, color = textColor, textAlign = textAlign),
            )
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
