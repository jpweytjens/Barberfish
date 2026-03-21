package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.PreviewSizeConfig
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import io.hammerhead.karooext.models.ViewConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HUDConfigSection(
    hudConfig: HUDConfig,
    zoneConfig: ZoneConfig,
    onUpdate: (HUDConfig) -> Unit,
) {
    var selectedSlot by remember { mutableStateOf<Int?>(0) }

    Text(
        "Tap a column to configure it.",
        fontSize = 12.sp,
        color = Color(0xFF1B2D2D),
    )
    HUDPreview(
        hudConfig = hudConfig,
        zoneConfig = zoneConfig,
        selectedSlot = selectedSlot,
        onSlotSelected = { idx -> selectedSlot = if (selectedSlot == idx) null else idx },
    )

    val slot = when (selectedSlot) {
        0 -> hudConfig.leftSlot
        1 -> hudConfig.middleSlot
        2 -> hudConfig.rightSlot
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
                        else -> hudConfig.copy(rightSlot = updated)
                    }
                )
            },
        )
    }
}

@Composable
private fun HUDPreview(
    hudConfig: HUDConfig,
    zoneConfig: ZoneConfig,
    selectedSlot: Int?,
    onSlotSelected: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .height(80.dp)
    ) {
        listOf(hudConfig.leftSlot, hudConfig.middleSlot, hudConfig.rightSlot)
            .forEachIndexed { idx, slotCfg ->
                HUDPreviewCell(
                    field = slotPreviewFieldState(slotCfg, zoneConfig),
                    colorMode = slotCfg.colorMode,
                    selected = selectedSlot == idx,
                    onClick = { onSlotSelected(idx) },
                    modifier = Modifier.weight(1f),
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
) {
    Box(
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
                        Modifier.border(2.dp, Color(0xFF31E09A), RoundedCornerShape(6.dp))
                    else Modifier
                )
    ) {
        BarberfishPreviewCell(
            field = field,
            alignment = ViewConfig.Alignment.RIGHT,
            colorMode = colorMode,
            modifier = Modifier.fillMaxSize(),
            sizeConfig = PreviewSizeConfig.HUD,
        )
    }
}

private fun slotPreviewFieldState(slot: HUDSlotConfig, zoneConfig: ZoneConfig): FieldState =
    when (slot.field) {
        HUDSlotField.Speed ->
            FieldState(
                "42.1",
                if (slot.speedSmoothing == SpeedSmoothingStream.S0) "Speed"
                else "${slot.speedSmoothing.label} Speed",
                FieldColor.Default,
                R.drawable.ic_col_speed,
            )
        HUDSlotField.HR ->
            FieldState(
                "187",
                "HR",
                FieldColor.Zone(4, 5, zoneConfig.hrPalette, isHr = true),
                R.drawable.ic_col_hr,
            )
        HUDSlotField.Power ->
            FieldState(
                "247",
                if (slot.powerSmoothing == PowerSmoothingStream.S0) "Power"
                else "${slot.powerSmoothing.label} Power",
                FieldColor.Zone(3, 7, zoneConfig.powerPalette, isHr = false),
                R.drawable.ic_col_power,
            )
        HUDSlotField.Cadence ->
            FieldState(
                "87",
                if (slot.cadenceSmoothing == CadenceSmoothingStream.S0) "Cadence"
                else "${slot.cadenceSmoothing.label} Cad",
                FieldColor.Default,
                R.drawable.ic_cadence,
            )
        HUDSlotField.AvgPower ->
            FieldState("220", "Avg Power", FieldColor.Zone(3, 7, zoneConfig.powerPalette, isHr = false), R.drawable.ic_col_power)
        HUDSlotField.NP ->
            FieldState("247", "NP", FieldColor.Zone(3, 7, zoneConfig.powerPalette, isHr = false), R.drawable.ic_col_power)
        HUDSlotField.Grade ->
            FieldState("6.2%", "Grade", FieldColor.Grade(6.2, zoneConfig.gradePalette), R.drawable.ic_grade)
        is HUDSlotField.AvgSpeed ->
            FieldState(
                "30.0",
                if (slot.field.includePaused) "Avg Speed\nTotal" else "Avg Speed\nMoving",
                FieldColor.Default,
                R.drawable.ic_speed_average,
            )
        is HUDSlotField.Time ->
            FieldState(
                "23'45\"",
                slot.field.kind.label,
                FieldColor.Default,
                slot.field.kind.iconRes,
            )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HUDSlotFieldCard(
    slot: HUDSlotConfig,
    onUpdate: (HUDSlotConfig) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFD5D5D5))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HUDFieldTypeDropdown(slot = slot, onUpdate = onUpdate)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HUDFieldTypeDropdown(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    val fieldLabel =
        when (slot.field) {
            HUDSlotField.Speed -> "Speed"
            HUDSlotField.HR -> "HR"
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
                    "HR" to HUDSlotField.HR,
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
                            if (isSelected) Color(0xFF9E9E9E) else Color.Transparent
                        )
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color(0xFF1B2D2D),
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
    SmoothingSlider(
        options = CadenceSmoothingStream.entries,
        selected = slot.cadenceSmoothing,
        label = { it.label },
        onSelected = { onUpdate(slot.copy(cadenceSmoothing = it)) },
        thumbIcon = R.drawable.ic_cadence,
    )
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
