package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jpweytjens.barberfish.datatype.shared.FieldValue
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
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = horizontalAlign,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.primary,
            style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color),
        )
        if (field.unit.isNotEmpty()) {
            Text(
                text = field.unit,
                style = TextStyle(fontSize = 13.sp, color = color),
            )
        }
    }
}
