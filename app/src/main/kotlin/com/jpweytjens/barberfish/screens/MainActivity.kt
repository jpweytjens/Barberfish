package com.jpweytjens.barberfish.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.formatTime
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.intervalsHrColors
import com.jpweytjens.barberfish.datatype.shared.intervalsPowerColors
import com.jpweytjens.barberfish.datatype.shared.karooHrColors
import com.jpweytjens.barberfish.datatype.shared.karooPowerColors
import com.jpweytjens.barberfish.datatype.shared.toColor
import com.jpweytjens.barberfish.datatype.shared.wahooHrColors
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColors
import com.jpweytjens.barberfish.datatype.shared.zwiftHrColors
import com.jpweytjens.barberfish.datatype.shared.zwiftPowerColors
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.saveAvgSpeedConfig
import com.jpweytjens.barberfish.extension.saveHRFieldConfig
import com.jpweytjens.barberfish.extension.saveHUDConfig
import com.jpweytjens.barberfish.extension.savePowerFieldConfig
import com.jpweytjens.barberfish.extension.saveSpeedFieldConfig
import com.jpweytjens.barberfish.extension.saveTimeConfig
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamSpeedFieldConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.title =
            android.text.SpannableString(getString(R.string.extension_name)).also {
                it.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK),
                    0,
                    it.length,
                    android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE,
                )
            }
        setContent { MaterialTheme { ConfigScreen() } }
    }

    @Composable
    private fun ConfigScreen() {
        var hudConfig by remember { mutableStateOf(HUDConfig()) }
        var powerFieldConfig by remember { mutableStateOf(PowerFieldConfig()) }
        var hrFieldConfig by remember { mutableStateOf(HRFieldConfig()) }
        var speedFieldConfig by remember { mutableStateOf(SpeedFieldConfig()) }
        var avgTotalConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var avgMovingConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var timeConfig by remember { mutableStateOf(TimeConfig()) }
        var zoneConfig by remember { mutableStateOf(ZoneConfig()) }

        var fieldsExpanded by remember { mutableStateOf(false) }
        var thresholdsExpanded by remember { mutableStateOf(false) }
        var hudExpanded by remember { mutableStateOf(false) }
        var globalExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch { streamHUDConfig().collect { hudConfig = it } }
            launch { streamPowerFieldConfig().collect { powerFieldConfig = it } }
            launch { streamHRFieldConfig().collect { hrFieldConfig = it } }
            launch { streamSpeedFieldConfig().collect { speedFieldConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = true).collect { avgTotalConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = false).collect { avgMovingConfig = it } }
            launch { streamTimeConfig().collect { timeConfig = it } }
            launch { streamZoneConfig().collect { zoneConfig = it } }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.fillMaxSize().padding(6.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CollapsibleSection(
                    title = "Data fields",
                    description = "Configure the standalone data fields.",
                    expanded = fieldsExpanded,
                    onToggle = { fieldsExpanded = !fieldsExpanded },
                ) {
                    val powerLabel =
                        if (powerFieldConfig.smoothing == PowerSmoothingStream.S0) "Power"
                        else "${powerFieldConfig.smoothing.label} Power"
                    FieldCard(
                        title = "POWER",
                        description = "Power output in W.",
                        previewFields =
                            listOf(
                                    180 to 2,
                                    240 to 3,
                                    320 to 5,
                                    400 to 6,
                                    807 to 6,
                                    1203 to 7,
                                    254 to 3,
                                    120 to 1,
                                )
                                .map { (watts, zone) ->
                                    FieldValue(
                                        watts.toString(),
                                        "W",
                                        label = powerLabel,
                                        color =
                                            if (powerFieldConfig.colorMode == ZoneColorMode.NONE)
                                                FieldColor.Default
                                            else
                                                FieldColor.Zone(
                                                    zone,
                                                    7,
                                                    zoneConfig.powerPalette,
                                                    isHr = false,
                                                ),
                                        iconRes = R.drawable.ic_col_power,
                                    )
                                },
                        colorMode = powerFieldConfig.colorMode,
                    ) {
                        Text(
                            "SMOOTHING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
                        SmoothingSlider(
                            options = PowerSmoothingStream.entries,
                            selected = powerFieldConfig.smoothing,
                            label = { it.label },
                            thumbIcon = R.drawable.ic_col_power,
                            onSelected = { stream ->
                                powerFieldConfig = powerFieldConfig.copy(smoothing = stream)
                                lifecycleScope.launch { savePowerFieldConfig(powerFieldConfig) }
                            },
                        )
                        ZoneColorSlider(
                            selected = powerFieldConfig.colorMode,
                            onSelected = { mode ->
                                powerFieldConfig = powerFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { savePowerFieldConfig(powerFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "HEART RATE",
                        description = "Heart rate in bpm.",
                        previewFields =
                            listOf(
                                    98 to 1,
                                    130 to 2,
                                    152 to 3,
                                    165 to 4,
                                    172 to 4,
                                    187 to 5,
                                    145 to 2,
                                )
                                .map { (bpm, zone) ->
                                    FieldValue(
                                        bpm.toString(),
                                        "bpm",
                                        label = "HR",
                                        color =
                                            if (hrFieldConfig.colorMode == ZoneColorMode.NONE)
                                                FieldColor.Default
                                            else
                                                FieldColor.Zone(
                                                    zone,
                                                    5,
                                                    zoneConfig.hrPalette,
                                                    isHr = true,
                                                ),
                                        iconRes = R.drawable.ic_col_hr,
                                    )
                                },
                        colorMode = hrFieldConfig.colorMode,
                    ) {
                        ZoneColorSlider(
                            selected = hrFieldConfig.colorMode,
                            onSelected = { mode ->
                                hrFieldConfig = hrFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRFieldConfig(hrFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "SPEED",
                        description = "Speed in km/h.",
                        previewFields =
                            listOf(
                                FieldValue(
                                    "37.5",
                                    "km/h",
                                    label =
                                        if (speedFieldConfig.smoothing == SpeedSmoothingStream.S0)
                                            "Speed"
                                        else "${speedFieldConfig.smoothing.label} Speed",
                                    color = FieldColor.Default,
                                    iconRes = R.drawable.ic_speed_average,
                                )
                            ),
                        colorMode = ZoneColorMode.NONE,
                    ) {
                        Text(
                            "SMOOTHING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
                        SmoothingSlider(
                            options = SpeedSmoothingStream.entries,
                            selected = speedFieldConfig.smoothing,
                            label = { it.label },
                            thumbIcon = R.drawable.ic_speed_average,
                            onSelected = { stream ->
                                speedFieldConfig = speedFieldConfig.copy(smoothing = stream)
                                lifecycleScope.launch { saveSpeedFieldConfig(speedFieldConfig) }
                            },
                        )
                    }
                } // end Fields

                val avgSpeedFactors = listOf(-1f, -0.5f, 0f, 0.5f, 1f)
                CollapsibleSection(
                    title = "Speed thresholds",
                    description =
                        "Speed thresholds for above/below coloring on average speed fields.",
                    expanded = thresholdsExpanded,
                    onToggle = { thresholdsExpanded = !thresholdsExpanded },
                ) {
                    Text(
                        "Typical ACP randonneuring checkpoint cutoff speeds: 15 km/h minimum, 30 km/h maximum. Set any event-specific speed limit as your threshold.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FieldCard(
                        title = "AVG SPEED (TOTAL)",
                        description = "Average speed including paused time.",
                        previewFields =
                            if (avgTotalConfig.thresholdKph > 0.0)
                                avgSpeedFactors.map { factor ->
                                    val speed =
                                        avgTotalConfig.thresholdKph *
                                            (1.0 + factor * avgTotalConfig.rangePercent / 100.0)
                                    FieldValue(
                                        "%.1f".format(speed),
                                        "km/h",
                                        "Avg Speed",
                                        FieldColor.Threshold(factor),
                                        R.drawable.ic_speed_average,
                                    )
                                }
                            else
                                listOf(
                                    FieldValue(
                                        "30.0",
                                        "km/h",
                                        "Avg Speed",
                                        FieldColor.Default,
                                        R.drawable.ic_speed_average,
                                    )
                                ),
                        colorMode = ZoneColorMode.TEXT,
                    ) {
                        Text(
                            "THRESHOLD (KM/H)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
                        ThresholdInput(
                            value = avgTotalConfig.thresholdKph,
                            onValueChange = { value ->
                                avgTotalConfig = avgTotalConfig.copy(thresholdKph = value)
                                lifecycleScope.launch {
                                    saveAvgSpeedConfig(includePaused = true, avgTotalConfig)
                                }
                            },
                        )
                        Text(
                            "MAX DEVIATION (%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
                        RangeInput(
                            value = avgTotalConfig.rangePercent,
                            onValueChange = { value ->
                                avgTotalConfig = avgTotalConfig.copy(rangePercent = value)
                                lifecycleScope.launch {
                                    saveAvgSpeedConfig(includePaused = true, avgTotalConfig)
                                }
                            },
                        )
                    }

                    FieldCard(
                        title = "AVG SPEED (MOVING)",
                        description = "Average speed excluding paused time.",
                        previewFields =
                            if (avgMovingConfig.thresholdKph > 0.0)
                                avgSpeedFactors.map { factor ->
                                    val speed =
                                        avgMovingConfig.thresholdKph *
                                            (1.0 + factor * avgMovingConfig.rangePercent / 100.0)
                                    FieldValue(
                                        "%.1f".format(speed),
                                        "km/h",
                                        "Avg Speed",
                                        FieldColor.Threshold(factor),
                                        R.drawable.ic_speed_average,
                                    )
                                }
                            else
                                listOf(
                                    FieldValue(
                                        "30.0",
                                        "km/h",
                                        "Avg Speed",
                                        FieldColor.Default,
                                        R.drawable.ic_speed_average,
                                    )
                                ),
                        colorMode = ZoneColorMode.TEXT,
                    ) {
                        Text(
                            "THRESHOLD (KM/H)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
                        ThresholdInput(
                            value = avgMovingConfig.thresholdKph,
                            onValueChange = { value ->
                                avgMovingConfig = avgMovingConfig.copy(thresholdKph = value)
                                lifecycleScope.launch {
                                    saveAvgSpeedConfig(includePaused = false, avgMovingConfig)
                                }
                            },
                        )
                        Text(
                            "MAX DEVIATION (%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
                        RangeInput(
                            value = avgMovingConfig.rangePercent,
                            onValueChange = { value ->
                                avgMovingConfig = avgMovingConfig.copy(rangePercent = value)
                                lifecycleScope.launch {
                                    saveAvgSpeedConfig(includePaused = false, avgMovingConfig)
                                }
                            },
                        )
                    }
                } // end Speed Thresholds

                CollapsibleSection(
                    title = "HUD",
                    description = "Configure the data fields in the heads-up display (HUD).",
                    expanded = hudExpanded,
                    onToggle = { hudExpanded = !hudExpanded },
                ) {
                    PowerStreamDropdown(
                        selected = hudConfig.powerStream,
                        onSelected = { stream ->
                            hudConfig = hudConfig.copy(powerStream = stream)
                            lifecycleScope.launch { saveHUDConfig(hudConfig) }
                        },
                    )
                    ZoneColorModeDropdown(
                        selected = hudConfig.colorMode,
                        onSelected = { mode ->
                            hudConfig = hudConfig.copy(colorMode = mode)
                            lifecycleScope.launch { saveHUDConfig(hudConfig) }
                        },
                    )
                    ZoneColorPreview(
                        colorMode = hudConfig.colorMode,
                        powerStream = hudConfig.powerStream,
                        zoneConfig = zoneConfig,
                    )
                } // end HUD

                CollapsibleSection(
                    title = "Global",
                    description = "Zone color palettes and time format shared across all fields.",
                    expanded = globalExpanded,
                    onToggle = { globalExpanded = !globalExpanded },
                ) {
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
                                ZonePalette.INTERVALS -> intervalsPowerColors.map { it }
                                ZonePalette.ZWIFT -> zwiftPowerColors.map { it }
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
                                ZonePalette.INTERVALS -> intervalsHrColors.map { it }
                                ZonePalette.ZWIFT -> zwiftHrColors.map { it }
                            }
                    )
                } // end Global
                Spacer(modifier = Modifier.height(72.dp))
            }
            Box(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(bottom = 16.dp)
                        .offset(x = (-8).dp)
                        .size(width = 54.dp, height = 50.dp)
                        .clip(RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
                        .background(Color(0xFFA0B4BE))
                        .clickable { finish() },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", fontSize = 20.sp, color = Color.Black)
            }
        } // end Box
    }
}

@Composable
private fun <T> SmoothingSlider(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    thumbIcon: Int? = null,
) {
    val density = LocalDensity.current
    val thumbSizeDp = 40.dp
    val dotSizeDp = 10.dp
    val trackHeightDp = 18.dp // slightly taller than dotSizeDp
    val grey = Color(0xFF9E9E9E)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxWidth().height(thumbSizeDp).pointerInput(options, onSelected) {
                    val slotWidthPx = size.width.toFloat() / options.size
                    fun idxAt(x: Float) = ((x / slotWidthPx).toInt()).coerceIn(0, options.size - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onSelected(options[idxAt(down.position.x)])
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            onSelected(options[idxAt(change.position.x)])
                            event = awaitPointerEvent()
                        }
                    }
                }
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val slotWidthPx = totalWidthPx / options.size
            val thumbSizePx = with(density) { thumbSizeDp.toPx() }
            val dotSizePx = with(density) { dotSizeDp.toPx() }
            val selectedIdx = options.indexOf(selected).coerceAtLeast(0)
            val thumbCenterX = (selectedIdx + 0.5f) * slotWidthPx

            // White pill track
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(trackHeightDp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
            )
            // Dots at each stop position
            options.forEachIndexed { index, _ ->
                val cx = (index + 0.5f) * slotWidthPx
                Box(
                    modifier =
                        Modifier.size(dotSizeDp)
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((cx - dotSizePx / 2).toInt(), 0) }
                            .clip(CircleShape)
                            .background(grey)
                )
            }
            // Thumb on top
            Box(
                modifier =
                    Modifier.size(thumbSizeDp)
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((thumbCenterX - thumbSizePx / 2).toInt(), 0) }
                        .clip(CircleShape)
                        .background(grey),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbIcon != null) {
                    Icon(
                        painter = painterResource(thumbIcon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // Labels — equal-weight slots aligned with dots
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                Text(
                    text = label(option),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color =
                        if (option == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PowerStreamDropdown(
    selected: PowerSmoothingStream,
    onSelected: (PowerSmoothingStream) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Power smoothing") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PowerSmoothingStream.entries.forEach { stream ->
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

@Composable
private fun CollapsibleSection(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                .background(Color.White)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .pointerInput(onToggle) { detectTapGestures(onTap = { onToggle() }) }
                    .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val rotation by
                animateFloatAsState(
                    if (expanded) 0f else 90f,
                    label = "chevron",
                    animationSpec = tween(200),
                )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(24.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)),
        ) {
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun FieldCard(
    title: String,
    description: String,
    previewFields: List<FieldValue>,
    colorMode: ZoneColorMode,
    controls: @Composable ColumnScope.() -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewFields) {
        index = 0
        while (true) {
            delay(Delay.PREVIEW.time)
            index = (index + 1) % previewFields.size
        }
    }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFDDDDDD)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2D2D))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    description,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = Color(0xFF1B2D2D),
                )
                Box(
                    modifier =
                        Modifier.width(120.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                ) {
                    BarberfishPreviewCell(
                        previewFields[index],
                        ViewConfig.Alignment.RIGHT,
                        colorMode,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFD5D5D5)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = controls,
        )
    }
}

@Composable
private fun ZoneColorSlider(selected: ZoneColorMode, onSelected: (ZoneColorMode) -> Unit) {
    val options = ZoneColorMode.entries
    val grey = Color(0xFF9E9E9E)
    Text("ZONE COLOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2D2D))
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(3.dp)
                .pointerInput(onSelected) {
                    val slotWidthPx = size.width.toFloat() / options.size
                    fun idxAt(x: Float) = (x / slotWidthPx).toInt().coerceIn(0, options.size - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onSelected(options[idxAt(down.position.x)])
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            onSelected(options[idxAt(change.position.x)])
                            event = awaitPointerEvent()
                        }
                    }
                }
    ) {
        options.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) grey else Color.Transparent)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        when (mode) {
                            ZoneColorMode.NONE -> "None"
                            ZoneColorMode.TEXT -> "Text"
                            ZoneColorMode.BACKGROUND -> "Fill"
                        },
                    fontSize = 10.sp,
                    color = Color(0xFF1B2D2D),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
            label = { Text("Zone color") },
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
private fun ThresholdInput(value: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == 0.0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            onValueChange(input.toDoubleOrNull() ?: 0.0)
        },
        placeholder = {
            Text(
                "Threshold (km/h)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RangeInput(value: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toDoubleOrNull()?.let { onValueChange(it) }
        },
        placeholder = {
            Text("Range (%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ZoneColorPreview(
    colorMode: ZoneColorMode,
    powerStream: PowerSmoothingStream,
    zoneConfig: ZoneConfig,
) {
    val alignment = ViewConfig.Alignment.RIGHT
    val speed = FieldValue("42.1", "km/h", "Speed", FieldColor.Default, R.drawable.ic_col_speed)
    val hr =
        FieldValue(
            "187",
            "bpm",
            "HR",
            FieldColor.Zone(4, 5, zoneConfig.hrPalette, isHr = true),
            R.drawable.ic_col_hr,
        )
    val power =
        FieldValue(
            "247",
            "W",
            powerStream.label,
            FieldColor.Zone(3, 7, zoneConfig.powerPalette, isHr = false),
            R.drawable.ic_col_power,
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .height(80.dp)
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
            modifier
                .background(zoneColor!!)
                .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
        else modifier.padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)

    val textAlign =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> TextAlign.Left
            ViewConfig.Alignment.CENTER -> TextAlign.Center
            ViewConfig.Alignment.RIGHT -> TextAlign.Right
        }

    Column(modifier = cellModifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            field.iconRes?.let { res ->
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(res),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                field.label.uppercase(),
                modifier = Modifier.weight(2f),
                fontSize = 16.sp,
                color = labelColor,
                textAlign = textAlign,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
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
