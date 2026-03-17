package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.primaryFontSp
import com.jpweytjens.barberfish.datatype.shared.toColorProvider
import io.hammerhead.karooext.models.ViewConfig

@Composable
fun SingleValueView(
    field: FieldValue,
    alignment: ViewConfig.Alignment = ViewConfig.Alignment.CENTER,
) {
    val color = field.color.toColorProvider()
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
        val valueSp = primaryFontSp(field.primary.length)
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(42.dp).padding(end= 4.dp),
            contentAlignment = contentAlign,
        ) {
            Text(
                text = field.primary,
                style = TextStyle(fontSize = valueSp, fontWeight = FontWeight.Normal, color = color, fontFamily = FontFamily.Monospace),
            )
        }
    }
}
