package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
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
        modifier = GlanceModifier.fillMaxSize().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarberfishView(
            left,
            alignment,
            colorMode,
            narrow = true,
            showLabel = true,
            modifier = GlanceModifier.defaultWeight(),
        )
        BarberfishView(
            center,
            alignment,
            colorMode,
            narrow = true,
            showLabel = true,
            modifier = GlanceModifier.defaultWeight(),
        )
        BarberfishView(
            right,
            alignment,
            colorMode,
            narrow = true,
            showLabel = true,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}
