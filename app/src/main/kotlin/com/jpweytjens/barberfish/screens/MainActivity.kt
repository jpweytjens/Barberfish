package com.jpweytjens.barberfish.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.PowerStream
import com.jpweytjens.barberfish.extension.ThreeColumnConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.saveAvgSpeedConfig
import com.jpweytjens.barberfish.extension.saveThreeColumnConfig
import com.jpweytjens.barberfish.extension.saveTimeConfig
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamThreeColumnConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.datatype.formatTime
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ConfigScreen()
            }
        }
    }

    @Composable
    private fun ConfigScreen() {
        var threeColConfig by remember { mutableStateOf(ThreeColumnConfig()) }
        var avgTotalConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var avgMovingConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var timeConfig by remember { mutableStateOf(TimeConfig()) }

        LaunchedEffect(Unit) {
            launch { streamThreeColumnConfig().collect { threeColConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = true).collect { avgTotalConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = false).collect { avgMovingConfig = it } }
            launch { streamTimeConfig().collect { timeConfig = it } }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
                    lifecycleScope.launch { saveAvgSpeedConfig(includePaused = true, avgTotalConfig) }
                },
            )

            Text("Avg Speed (moving time)", style = MaterialTheme.typography.titleMedium)
            ThresholdInput(
                value = avgMovingConfig.thresholdKph,
                onValueChange = { value ->
                    avgMovingConfig = AvgSpeedConfig(value)
                    lifecycleScope.launch { saveAvgSpeedConfig(includePaused = false, avgMovingConfig) }
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PowerStreamDropdown(selected: PowerStream, onSelected: (PowerStream) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Power averaging window") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Zone color mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Time display format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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

@Composable
private fun TimeFormatPreview(format: TimeFormat) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatTime(5025L, format),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ZonePreviewCell("SPEED", "42.1", "km/h", zoneColor = null, colorMode)
            ZonePreviewCell("HR", "187", "bpm", zoneColor = Color(0xFFE53935), colorMode)
            ZonePreviewCell("POWER", "247", "W", zoneColor = Color(0xFFFF9800), colorMode)
        }
    }
}

@Composable
private fun ZonePreviewCell(
    label: String,
    value: String,
    unit: String,
    zoneColor: Color?,
    colorMode: ZoneColorMode,
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val textColor: Color
    val cellModifier: Modifier
    when (colorMode) {
        ZoneColorMode.TEXT -> {
            textColor = zoneColor ?: defaultColor
            cellModifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        }
        ZoneColorMode.BACKGROUND -> {
            textColor = Color.White
            cellModifier = if (zoneColor != null)
                Modifier
                    .background(zoneColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            else
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        }
    }
    Column(
        modifier = cellModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 9.sp, color = Color(0xFFAAAAAA))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(unit, fontSize = 10.sp, color = textColor)
    }
}
