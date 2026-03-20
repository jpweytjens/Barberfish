package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import com.jpweytjens.barberfish.datatype.shared.FieldSizeConfig
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.toColorConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

@Composable
fun BarberfishView(
    field: FieldValue,
    alignment: ViewConfig.Alignment = ViewConfig.Alignment.CENTER,
    colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    sizeConfig: FieldSizeConfig = FieldSizeConfig.STANDARD,
    modifier: GlanceModifier = GlanceModifier,
) {
    val colors = field.color.toColorConfig(colorMode)

    Box(
        modifier = modifier.fillMaxHeight().maybeBackground(colors.background),
        contentAlignment = Alignment.TopStart,
    ) {
        BarberfishValue(
            text = field.primary,
            alignment = alignment,
            colors = colors,
            config = sizeConfig,
            modifier =
                GlanceModifier.fillMaxWidth()
                    .fillMaxHeight()
                    .padding(
                        start = sizeConfig.cellPaddingH,
                        end = sizeConfig.cellPaddingH,
                    ),
        )
        BarberfishHeader(
            label = field.label,
            iconRes = field.iconRes,
            colors = colors,
            alignment = alignment,
            config = sizeConfig,
            modifier =
                GlanceModifier.fillMaxWidth()
                    .padding(
                        start = sizeConfig.cellPaddingH,
                        end = sizeConfig.cellPaddingH,
                        top = if (field.label.contains("\n")) 0.dp else sizeConfig.cellPaddingTop,
                    ),
        )
    }
}

private fun GlanceModifier.maybeBackground(bg: ColorProvider?): GlanceModifier =
    if (bg != null) background(bg) else this
