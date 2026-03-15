package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.hrZoneColor
import com.jpweytjens.barberfish.datatype.shared.powerZoneColor

@Composable
fun ThreeColumnView(left: FieldValue, center: FieldValue, right: FieldValue) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColumnCell(left,   GlanceModifier.defaultWeight())
        ColumnCell(center, GlanceModifier.defaultWeight())
        ColumnCell(right,  GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ColumnCell(field: FieldValue, modifier: GlanceModifier) {
    val color = field.color.toColorProvider()
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.primary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color),
        )
        if (field.unit.isNotEmpty()) {
            Text(
                text = field.unit,
                style = TextStyle(fontSize = 11.sp, color = color),
            )
        }
    }
}

private fun FieldColor.toColorProvider(): ColorProvider = when (this) {
    is FieldColor.Default   -> ColorProvider(Color.White)
    is FieldColor.Threshold -> ColorProvider(if (above) Color(0xFF81C784) else Color(0xFFE57373))
    is FieldColor.Zone      -> ColorProvider(if (isHr) hrZoneColor(zone, palette) else powerZoneColor(zone, palette))
}
