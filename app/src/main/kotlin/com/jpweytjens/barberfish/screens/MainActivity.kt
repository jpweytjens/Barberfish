package com.jpweytjens.barberfish.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.formatTime
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.karooHrColors
import com.jpweytjens.barberfish.datatype.shared.karooPowerColors
import com.jpweytjens.barberfish.datatype.shared.toColor
import com.jpweytjens.barberfish.datatype.shared.wahooHrColors
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColors
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.PowerStream
import com.jpweytjens.barberfish.extension.ThreeColumnConfig
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.saveAvgSpeedConfig
import com.jpweytjens.barberfish.extension.saveThreeColumnConfig
import com.jpweytjens.barberfish.extension.saveTimeConfig
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamThreeColumnConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ConfigScreen() } }
    }

    @Composable
    private fun ConfigScreen() {
        var threeColConfig by remember { mutableStateOf(ThreeColumnConfig()) }
        var avgTotalConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var avgMovingConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var timeConfig by remember { mutableStateOf(TimeConfig()) }
        var zoneConfig by remember { mutableStateOf(ZoneConfig()) }

        LaunchedEffect(Unit) {
            launch { streamThreeColumnConfig().collect { threeColConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = true).collect { avgTotalConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = false).collect { avgMovingConfig = it } }
            launch { streamTimeConfig().collect { timeConfig = it } }
            launch { streamZoneConfig().collect { zoneConfig = it } }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Three-Column Field", style = MaterialTheme.typography.titleMedium)
            PowerStreamDropdown(
                selected = threeColConfig.powerStream,
                onSelected = { stream ->
                    threeColConfig = threeColConfig.copy(powerStream = stream)
                    lifecycleScope.launch { saveThreeColumnConfig(threeColConfig) }
                },
            )
            ZoneColorModeDropdown(
                selected = threeColConfig.colorMode,
                onSelected = { mode ->
                    threeColConfig = threeColConfig.copy(colorMode = mode)
                    lifecycleScope.launch { saveThreeColumnConfig(threeColConfig) }
                },
            )
            ZoneColorPreview(colorMode = threeColConfig.colorMode)

            Text("Avg Speed (total time)", style = MaterialTheme.typography.titleMedium)
            ThresholdInput(
                value = avgTotalConfig.thresholdKph,
                onValueChange = { value ->
                    avgTotalConfig = AvgSpeedConfig(value)
                    lifecycleScope.launch {
                        saveAvgSpeedConfig(includePaused = true, avgTotalConfig)
                    }
                },
            )

            Text("Avg Speed (moving time)", style = MaterialTheme.typography.titleMedium)
            ThresholdInput(
                value = avgMovingConfig.thresholdKph,
                onValueChange = { value ->
                    avgMovingConfig = AvgSpeedConfig(value)
                    lifecycleScope.launch {
                        saveAvgSpeedConfig(includePaused = false, avgMovingConfig)
                    }
                },
            )

            Text("Time Fields", style = MaterialTheme.typography.titleMedium)
            TimeFormatDropdown(
                selected = timeConfig.format,
                onSelected = { format ->
                    timeConfig = TimeConfig(format)
                    lifecycleScope.launch { saveTimeConfig(timeConfig) }
                },
            )
            TimeFormatPreview(format = timeConfig.format)

            Text("Zone Colors", style = MaterialTheme.typography.titleMedium)
            Text("Power zones", style = MaterialTheme.typography.labelMedium)
            ZonePaletteDropdown(
                label = "Power zone palette",
                selected = zoneConfig.powerPalette,
                onSelected = { palette ->
                    zoneConfig = zoneConfig.copy(powerPalette = palette)
                    lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                },
            )
            ZoneColorBar(
                colors =
                    when (zoneConfig.powerPalette) {
                        ZonePalette.KAROO -> karooPowerColors.map { it }
                        ZonePalette.WAHOO -> wahooPowerColors.map { it }
                    }
            )

            Text("HR zones", style = MaterialTheme.typography.labelMedium)
            ZonePaletteDropdown(
                label = "HR zone palette",
                selected = zoneConfig.hrPalette,
                onSelected = { palette ->
                    zoneConfig = zoneConfig.copy(hrPalette = palette)
                    lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                },
            )
            ZoneColorBar(
                colors =
                    when (zoneConfig.hrPalette) {
                        ZonePalette.KAROO -> karooHrColors.map { it }
                        ZonePalette.WAHOO -> wahooHrColors.map { it }
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PowerStreamDropdown(selected: PowerStream, onSelected: (PowerStream) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Power averaging window") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PowerStream.entries.forEach { stream ->
                DropdownMenuItem(
                    text = { Text(stream.label) },
                    onClick = {
                        onSelected(stream)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneColorModeDropdown(selected: ZoneColorMode, onSelected: (ZoneColorMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Zone color mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ZoneColorMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFormatDropdown(selected: TimeFormat, onSelected: (TimeFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Time display format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.label) },
                    onClick = {
                        onSelected(format)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZonePaletteDropdown(
    label: String,
    selected: ZonePalette,
    onSelected: (ZonePalette) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ZonePalette.entries.forEach { palette ->
                DropdownMenuItem(
                    text = { Text(palette.label) },
                    onClick = {
                        onSelected(palette)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoneColorBar(colors: List<Color>) {
    Row(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        colors.forEach { color ->
            Box(modifier = Modifier.weight(1f).height(24.dp).background(color))
        }
    }
}

@Composable
private fun TimeFormatPreview(format: TimeFormat) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatTime(5025L, format),
            style = MaterialTheme.typography.displaySmall.copy(color = Color.White),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ThresholdInput(value: Double?, onValueChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            onValueChange(input.toDoubleOrNull())
        },
        label = { Text("Target speed (km/h) — leave empty to disable") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ZoneColorPreview(colorMode: ZoneColorMode) {
    val alignment = ViewConfig.Alignment.RIGHT
    val speed =
        FieldValue("42.1", "km/h", "Speed", FieldColor.Default, R.drawable.ic_col_speed)
    val hr =
        FieldValue(
            "187",
            "bpm",
            "HR",
            FieldColor.Zone(4, 5, ZonePalette.KAROO, isHr = true),
            R.drawable.ic_col_hr,
        )
    val power =
        FieldValue(
            "247",
            "W",
            "Power",
            FieldColor.Zone(3, 7, ZonePalette.KAROO, isHr = false),
            R.drawable.ic_col_power,
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .height(80.dp),
    ) {
        BarberfishPreviewCell(speed, alignment, colorMode, Modifier.weight(1f))
        BarberfishPreviewCell(hr, alignment, colorMode, Modifier.weight(1f))
        BarberfishPreviewCell(power, alignment, colorMode, Modifier.weight(1f))
    }
}

@Composable
private fun BarberfishPreviewCell(
    field: FieldValue,
    alignment: ViewConfig.Alignment,
    colorMode: ZoneColorMode,
    modifier: Modifier = Modifier,
) {
    val zoneColor = field.color.toColor()
    val hasZoneBg = colorMode == ZoneColorMode.BACKGROUND && zoneColor != null

    val valueColor =
        when {
            hasZoneBg -> Color.Black
            colorMode == ZoneColorMode.TEXT -> zoneColor ?: Color.White
            else -> Color.White
        }
    val labelColor = if (hasZoneBg) Color.Black else Color.White
    val iconTint = if (hasZoneBg) Color.Black else Color(0xFF31E09A)

    val cellModifier =
        if (hasZoneBg)
            modifier.background(zoneColor!!).padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
        else modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)

    val textAlign =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> TextAlign.Left
            ViewConfig.Alignment.CENTER -> TextAlign.Center
            ViewConfig.Alignment.RIGHT -> TextAlign.Right
        }

    Column(
        modifier = cellModifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            field.iconRes?.let { res ->
                Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(res),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(10.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            Text(
                field.label.uppercase(),
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = labelColor,
                textAlign = textAlign,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            field.primary,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = textAlign,
            fontFamily = FontFamily.Monospace,
        )
    }
}
