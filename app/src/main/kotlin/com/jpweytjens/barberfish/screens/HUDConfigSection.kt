package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldState
import androidx.compose.ui.platform.LocalContext
import com.jpweytjens.barberfish.datatype.barberfishFieldRemoteViews
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.datatype.shared.gradeThreshold
import com.jpweytjens.barberfish.datatype.shared.previewElevationFixture
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.datatype.shared.Grey100
import com.jpweytjens.barberfish.datatype.shared.Grey200
import com.jpweytjens.barberfish.datatype.shared.Grey400
import com.jpweytjens.barberfish.datatype.shared.ICON_TINT_TEAL
import com.jpweytjens.barberfish.datatype.shared.TextDark
import com.jpweytjens.barberfish.extension.ZoneConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HUDConfigSection(
    hudConfig: HUDConfig,
    zoneConfig: ZoneConfig,
    timeCfg: TimeConfig,
    profile: UserProfile,
    onUpdate: (HUDConfig) -> Unit,
) {
    var selectedSlot by remember { mutableStateOf<Int?>(null) }

    ColumnCountToggle(
        columns = hudConfig.columns,
        onSelect = { cols ->
            if (cols != hudConfig.columns) {
                selectedSlot = null
                onUpdate(hudConfig.copy(columns = cols))
            }
        },
    )
    Text(
        "Tap a column to configure it.",
        fontSize = 12.sp,
        color = TextDark,
    )
    HUDPreview(
        hudConfig = hudConfig,
        zoneConfig = zoneConfig,
        timeCfg = timeCfg,
        profile = profile,
        selectedSlot = selectedSlot,
        onSlotSelected = { idx -> selectedSlot = if (selectedSlot == idx) null else idx },
    )

    val slot = when (selectedSlot) {
        0 -> hudConfig.leftSlot
        1 -> hudConfig.middleSlot
        2 -> hudConfig.rightSlot
        3 -> if (hudConfig.columns == 4) hudConfig.fourthSlot else null
        else -> null
    }
    if (slot != null) {
        HUDSlotFieldCard(
            slot = slot,
            onUpdate = { updated ->
                onUpdate(
                    when (selectedSlot) {
                        0 -> hudConfig.copy(leftSlot = updated)
                        1 -> hudConfig.copy(middleSlot = updated)
                        2 -> hudConfig.copy(rightSlot = updated)
                        else -> hudConfig.copy(fourthSlot = updated)
                    }
                )
            },
        )
    }
    SparklineCard(
        config = hudConfig.sparkline,
        palette = zoneConfig.gradePalette,
        onUpdate = { onUpdate(hudConfig.copy(sparkline = it)) },
    )
}

@Composable
private fun HUDPreview(
    hudConfig: HUDConfig,
    zoneConfig: ZoneConfig,
    timeCfg: TimeConfig,
    profile: UserProfile,
    selectedSlot: Int?,
    onSlotSelected: (Int) -> Unit,
) {
    val states = remember(hudConfig, zoneConfig, timeCfg, profile) {
        HUDField.previewStates(hudConfig, timeCfg, profile, zoneConfig)
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(states) {
        index = 0
        while (true) {
            delay(Delay.PREVIEW.time)
            index = (index + 1) % states.size
        }
    }
    val current = states[index]

    val density = LocalDensity.current.density
    val screenWidthPx = (LocalConfiguration.current.screenWidthDp * density).toInt()
    val sparklineBitmap = remember(hudConfig.sparkline, zoneConfig, screenWidthPx) {
        if (!hudConfig.sparkline.enabled) null
        else renderElevationSparkline(
            elevationPoints = previewElevationFixture(),
            positionM       = 2_500f,
            widthPx         = screenWidthPx,
            heightPx        = (28f * density).toInt(),
            density         = density,
            palette         = zoneConfig.gradePalette,
            readable        = zoneConfig.readableColors,
            lookaheadM      = hudConfig.sparkline.lookaheadKm * 1_000f,
            skipBands       = hudConfig.sparkline.skipBands,
        ).first
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        Row(Modifier.fillMaxWidth().height(80.dp)) {
            buildList {
                add(Triple(0, current.leftSlot, current.leftColorMode))
                add(Triple(1, current.middleSlot, current.middleColorMode))
                add(Triple(2, current.rightSlot, current.rightColorMode))
                if (hudConfig.columns == 4) add(Triple(3, current.fourthSlot, current.fourthColorMode))
            }.forEach { (idx, field, colorMode) ->
                HUDPreviewCell(
                    field = field,
                    colorMode = colorMode,
                    selected = selectedSlot == idx,
                    onClick = { onSlotSelected(idx) },
                    modifier = Modifier.weight(1f),
                    columns = hudConfig.columns,
                    sparklineEnabled = hudConfig.sparkline.enabled,
                )
            }
        }
        if (sparklineBitmap != null) {
            Image(
                bitmap = sparklineBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

@Composable
private fun HUDPreviewCell(
    field: FieldState,
    colorMode: ZoneColorMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    sparklineEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val baseConfig = if (columns == 4) ViewSizeConfig.PREVIEW_HUD_FOUR
        else ViewSizeConfig.PREVIEW_HUD_THREE
    BoxWithConstraints(
        modifier =
            modifier
                .pointerInput(onClick) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onClick()
                    }
                }
                .then(
                    if (selected)
                        Modifier.border(2.dp, ICON_TINT_TEAL, RoundedCornerShape(6.dp))
                    else Modifier
                )
    ) {
        val density = LocalDensity.current.density
        val widthPx = (maxWidth.value * density).toInt()
        val heightPx = (maxHeight.value * density).toInt()
        val sizeConfig = remember(baseConfig, sparklineEnabled, widthPx) {
            val adjusted = if (!sparklineEnabled)
                baseConfig.copy(valueTranslationY = 37.5f) // 20dp × 1.875 density
            else baseConfig
            adjusted.copy(cellWidthPxOverride = widthPx.toFloat())
        }
        val bitmap = remember(field, colorMode, sizeConfig, heightPx) {
            val rv = barberfishFieldRemoteViews(
                field = field,
                alignment = ViewConfig.Alignment.RIGHT,
                colorMode = colorMode,
                sizeConfig = sizeConfig,
                preview = true,
                context = context,
            )
            remoteViewsToBitmap(rv, widthPx, heightPx, context)
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun ColumnCountToggle(columns: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Grey200)
                .padding(3.dp)
                .pointerInput(columns, onSelect) {
                    val slotWidthPx = size.width.toFloat() / 2
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val idx = (down.position.x / slotWidthPx).toInt().coerceIn(0, 1)
                        onSelect(if (idx == 0) 3 else 4)
                    }
                }
    ) {
        listOf(3 to "3 columns", 4 to "4 columns").forEach { (cols, label) ->
            val isSelected = columns == cols
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Grey400 else Color.Transparent)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextDark,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HUDSlotFieldCard(
    slot: HUDSlotConfig,
    onUpdate: (HUDSlotConfig) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Grey200, RoundedCornerShape(6.dp)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Grey100)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HUDFieldTypeDropdown(slot = slot, onUpdate = onUpdate)
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Grey200)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val f = slot.field) {
                HUDSlotField.Speed -> HUDSpeedCard(slot, onUpdate)
                HUDSlotField.HR -> {}
                HUDSlotField.Power -> HUDPowerCard(slot, onUpdate)
                HUDSlotField.Cadence -> HUDCadenceCard(slot, onUpdate)
                HUDSlotField.AvgPower -> {}
                HUDSlotField.NP -> {}
                HUDSlotField.Grade -> {}
                is HUDSlotField.AvgSpeed -> HUDAvgSpeedCard(slot, f, onUpdate)
                is HUDSlotField.Time -> HUDTimeDropdown(slot, f, onUpdate)
            }
            if (slot.field == HUDSlotField.HR || slot.field == HUDSlotField.Power ||
                slot.field == HUDSlotField.AvgPower || slot.field == HUDSlotField.NP ||
                slot.field == HUDSlotField.Grade) {
                ZoneColorSlider(
                    selected = slot.colorMode,
                    onSelected = { onUpdate(slot.copy(colorMode = it)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HUDFieldTypeDropdown(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    val fieldLabel =
        when (slot.field) {
            HUDSlotField.Speed -> "Speed"
            HUDSlotField.HR -> "Heart rate"
            HUDSlotField.Power -> "Power"
            HUDSlotField.Cadence -> "Cadence"
            HUDSlotField.AvgPower -> "Avg Power"
            HUDSlotField.NP -> "NP"
            HUDSlotField.Grade -> "Grade"
            is HUDSlotField.AvgSpeed -> "Avg Speed"
            is HUDSlotField.Time -> "Time"
        }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = fieldLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Data field") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                    "Speed" to HUDSlotField.Speed,
                    "Heart rate" to HUDSlotField.HR,
                    "Power" to HUDSlotField.Power,
                    "Cadence" to HUDSlotField.Cadence,
                    "Avg Power" to HUDSlotField.AvgPower,
                    "NP" to HUDSlotField.NP,
                    "Grade" to HUDSlotField.Grade,
                    "Avg Speed" to HUDSlotField.AvgSpeed(),
                    "Time" to HUDSlotField.Time(),
                )
                .forEach { (label, field) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onUpdate(slot.copy(field = field))
                            expanded = false
                        },
                    )
                }
        }
    }
}

@Composable
private fun HUDSpeedCard(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    Text("SMOOTHING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
    SmoothingSlider(
        options = SpeedSmoothingStream.entries,
        selected = slot.speedSmoothing,
        label = { it.label },
        onSelected = { onUpdate(slot.copy(speedSmoothing = it)) },
        thumbIcon = R.drawable.ic_col_speed,
    )
}

@Composable
private fun HUDPowerCard(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    Text("SMOOTHING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
    SmoothingSlider(
        options = PowerSmoothingStream.entries,
        selected = slot.powerSmoothing,
        label = { it.label },
        onSelected = { onUpdate(slot.copy(powerSmoothing = it)) },
        thumbIcon = R.drawable.ic_col_power,
    )
}

@Composable
private fun HUDAvgSpeedCard(
    slot: HUDSlotConfig,
    field: HUDSlotField.AvgSpeed,
    onUpdate: (HUDSlotConfig) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(3.dp)
                .pointerInput(field, onUpdate) {
                    val slotWidthPx = size.width.toFloat() / 2
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val idx = (down.position.x / slotWidthPx).toInt().coerceIn(0, 1)
                        val includePaused = idx == 1
                        onUpdate(slot.copy(field = HUDSlotField.AvgSpeed(includePaused)))
                    }
                }
    ) {
        listOf(false to "Moving", true to "Total").forEach { (includePaused, label) ->
            val isSelected = field.includePaused == includePaused
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) Grey400 else Color.Transparent
                        )
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextDark,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    AvgSpeedThresholdControls(
        config = slot.avgSpeedConfig,
        onConfigChange = { onUpdate(slot.copy(avgSpeedConfig = it)) },
    )
}

@Composable
private fun HUDCadenceCard(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    Text("SMOOTHING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
    SmoothingSlider(
        options = CadenceSmoothingStream.entries,
        selected = slot.cadenceSmoothing,
        label = { it.label },
        onSelected = { onUpdate(slot.copy(cadenceSmoothing = it)) },
        thumbIcon = R.drawable.ic_cadence,
    )
}

@Composable
private fun SparklineCard(
    config: SparklineConfig,
    palette: GradePalette,
    onUpdate: (SparklineConfig) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Grey200)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ELEVATION SPARKLINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Text("Shows elevation ahead when a route is loaded.", fontSize = 12.sp, color = TextDark)
        SegmentedRow(
            options = listOf(true to "On", false to "Off"),
            selected = config.enabled,
            onSelect = { onUpdate(config.copy(enabled = it)) },
        )
        if (config.enabled) {
            Text("LOOKAHEAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("Distance shown ahead of your position.", fontSize = 12.sp, color = TextDark)
            SegmentedRow(
                options = listOf(5 to "5 km", 10 to "10 km", 20 to "20 km"),
                selected = config.lookaheadKm,
                onSelect = { onUpdate(config.copy(lookaheadKm = it)) },
            )
            val threshold = gradeThreshold(palette, config.skipBands)
            Text("MINIMUM GRADE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("Grades below this band stay uncolored.", fontSize = 12.sp, color = TextDark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentedRow(
                    options = listOf(0 to "0", 1 to "1", 2 to "2", 3 to "3"),
                    selected = config.skipBands,
                    onSelect = { onUpdate(config.copy(skipBands = it)) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "≥${"%.0f".format(threshold)}%",
                    fontSize = 11.sp,
                    color = TextDark,
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(3.dp)
                .pointerInput(options, onSelect) {
                    val slotWidthPx = size.width.toFloat() / options.size
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val idx =
                            (down.position.x / slotWidthPx).toInt().coerceIn(0, options.lastIndex)
                        onSelect(options[idx].first)
                    }
                }
    ) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Grey400 else Color.Transparent)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextDark,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HUDTimeDropdown(
    slot: HUDSlotConfig,
    field: HUDSlotField.Time,
    onUpdate: (HUDSlotConfig) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = field.kind.label.replace("\n", " "),
            onValueChange = {},
            readOnly = true,
            label = { Text("Time field") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.label.replace("\n", " ")) },
                    onClick = {
                        onUpdate(slot.copy(field = HUDSlotField.Time(kind)))
                        expanded = false
                    },
                )
            }
        }
    }
}
