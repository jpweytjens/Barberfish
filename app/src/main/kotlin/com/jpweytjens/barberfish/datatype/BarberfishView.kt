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
    modifier: GlanceModifier = GlanceModifier,
) {
    if (showLabel) {
        val textAlign = alignment.toTextAlign()

        val bg = field.color.toBackgroundColorProvider()
        val hasZoneBg = colorMode == ZoneColorMode.BACKGROUND && bg != null

        val textColor: ColorProvider
        val cellModifier: GlanceModifier
        when (colorMode) {
            ZoneColorMode.BACKGROUND -> {
                textColor = if (hasZoneBg) ColorProvider(Color.Black) else whiteText
                cellModifier =
                    if (bg != null)
                        modifier.fillMaxHeight().background(bg)
                            .padding(start = 2.dp, end = 2.dp, top = 4.dp)
                    else modifier.fillMaxHeight().padding(start = 2.dp, end = 2.dp, top = 4.dp)
            }
            ZoneColorMode.TEXT -> {
                textColor = field.color.toColorProvider()
                cellModifier =
                    modifier.fillMaxHeight().padding(start = 2.dp, end = 2.dp, top = 4.dp)
            }
        }

        val labelColor = ColorProvider(if (hasZoneBg) Color.Black else Color.White)
        val iconTint = Color(if (hasZoneBg) 0xFF000000.toInt() else 0xFF31E09A.toInt())

        Column(
            modifier = cellModifier,
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.Top,
        ) {
            // Label row: icon pinned left, label fills remaining width aligned per textAlign
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconRes = field.iconRes
                if (iconRes != null) {
                    Box(
                        modifier = GlanceModifier.width(12.dp).height(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(ColorProvider(iconTint)),
                            modifier = GlanceModifier.width(12.dp).height(12.dp),
                        )
                    }
                    Spacer(GlanceModifier.width(2.dp))
                }
                Text(
                    text = field.label.uppercase(),
                    modifier = GlanceModifier.defaultWeight(),
                    style =
                        TextStyle(
                            fontSize = 16.sp,
                            color = labelColor,
                            textAlign = textAlign,
                            fontFamily = FontFamily.Monospace,
                        ),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            val valueSp = dynamicFontSp(field.primary.length, narrow = narrow)
            Text(
                text = field.primary,
                modifier = GlanceModifier.fillMaxWidth(),
                style =
                    TextStyle(
                        fontSize = valueSp,
                        fontWeight = FontWeight.Normal,
                        color = textColor,
                        textAlign = textAlign,
                        fontFamily = FontFamily.Monospace,
                    ),
            )
        }
    } else {
        val color =
            when (colorMode) {
                ZoneColorMode.BACKGROUND -> whiteText
                ZoneColorMode.TEXT -> field.color.toColorProvider()
            }
        val horizontalAlign =
            when (alignment) {
                ViewConfig.Alignment.LEFT -> Alignment.Start
                ViewConfig.Alignment.CENTER -> Alignment.CenterHorizontally
                ViewConfig.Alignment.RIGHT -> Alignment.End
            }
        val contentAlign =
            when (alignment) {
                ViewConfig.Alignment.LEFT -> Alignment.BottomStart
                ViewConfig.Alignment.CENTER -> Alignment.BottomCenter
                ViewConfig.Alignment.RIGHT -> Alignment.BottomEnd
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
                    style =
                        TextStyle(
                            fontSize = valueSp,
                            fontWeight = FontWeight.Normal,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                        ),
                )
            }
        }
    }
}

private fun ViewConfig.Alignment.toHorizontal(): Alignment.Horizontal =
    when (this) {
        ViewConfig.Alignment.LEFT -> Alignment.Start
        ViewConfig.Alignment.CENTER -> Alignment.CenterHorizontally
        ViewConfig.Alignment.RIGHT -> Alignment.End
    }

private fun ViewConfig.Alignment.toTextAlign(): TextAlign =
    when (this) {
        ViewConfig.Alignment.LEFT -> TextAlign.Left
        ViewConfig.Alignment.CENTER -> TextAlign.Center
        ViewConfig.Alignment.RIGHT -> TextAlign.Right
    }
