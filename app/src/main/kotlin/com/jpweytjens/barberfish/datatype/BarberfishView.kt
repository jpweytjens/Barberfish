package com.jpweytjens.barberfish.datatype

import androidx.compose.runtime.Composable
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
import com.jpweytjens.barberfish.datatype.shared.ColorConfig
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.narrowFontSp
import com.jpweytjens.barberfish.datatype.shared.singleValueFontSp
import com.jpweytjens.barberfish.datatype.shared.toColorConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.ViewConfig

private val ICON_SIZE = 16.dp
private val ICON_LABEL_GAP = 4.dp
private val CELL_PADDING_H = 4.dp
private val CELL_PADDING_TOP = 6.dp
private val LABEL_FONT_SIZE = 14.sp

@Composable
fun BarberfishView(
    field: FieldValue,
    alignment: ViewConfig.Alignment = ViewConfig.Alignment.CENTER,
    colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    narrow: Boolean = false,
    textSize: Int = 0,
    modifier: GlanceModifier = GlanceModifier,
) {
    val colors = field.color.toColorConfig(colorMode)
    val fontSize =
        if (textSize > 0 && !narrow) singleValueFontSp(field.primary, textSize)
        else narrowFontSp(field.primary.length)
    val valueStyle =
        TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            color = colors.valueText,
            textAlign = alignment.toTextAlign(),
            fontFamily = FontFamily.Monospace,
        )
    if (field.label.isNotEmpty()) {
        // Labeled layout (three-column columns): label at top, value at bottom
        Column(
            modifier =
                modifier
                    .fillMaxHeight()
                    .maybeBackground(colors.background)
                    .padding(start = CELL_PADDING_H, end = CELL_PADDING_H, top = CELL_PADDING_TOP),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.Top,
        ) {
            LabelRow(field.label, field.iconRes, colors, alignment.toTextAlign())
            Spacer(GlanceModifier.defaultWeight())
            PrimaryText(
                text = field.primary,
                style = valueStyle,
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    } else {
        // No-label layout (single-value fields with showHeader=true): center value in cell
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .maybeBackground(colors.background)
                    .padding(horizontal = CELL_PADDING_H,),
            contentAlignment =
                Alignment(
                    vertical = Alignment.Vertical.CenterVertically,
                    horizontal = Alignment.Horizontal.CenterHorizontally,
                ),
        ) {
            PrimaryText(
                text = field.primary,
                style = valueStyle,
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LabelRow(label: String, iconRes: Int?, colors: ColorConfig, textAlign: TextAlign) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (iconRes != null) {
            FieldIcon(iconRes, colors)
            Spacer(GlanceModifier.width(ICON_LABEL_GAP))
        }
        Text(
            text = label.uppercase(),
            modifier = GlanceModifier.defaultWeight(),
            // maxLines = 1,
            style =
                TextStyle(
                    fontSize = LABEL_FONT_SIZE,
                    color = colors.labelText,
                    textAlign = textAlign,
                    fontFamily = FontFamily.Monospace,
                ),
        )
    }
}

@Composable
private fun FieldIcon(iconRes: Int, colors: ColorConfig) {
    Box(
        modifier = GlanceModifier.width(ICON_SIZE).height(ICON_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(colors.iconTint)),
            modifier = GlanceModifier.width(ICON_SIZE).height(ICON_SIZE),
        )
    }
}

@Composable
private fun PrimaryText(text: String, style: TextStyle, modifier: GlanceModifier = GlanceModifier) {
    Text(text = text, style = style, modifier = modifier)
}

private fun GlanceModifier.maybeBackground(bg: ColorProvider?): GlanceModifier =
    if (bg != null) background(bg) else this

private fun ViewConfig.Alignment.toTextAlign(): TextAlign =
    when (this) {
        ViewConfig.Alignment.LEFT -> TextAlign.Left
        ViewConfig.Alignment.CENTER -> TextAlign.Center
        ViewConfig.Alignment.RIGHT -> TextAlign.Right
    }
