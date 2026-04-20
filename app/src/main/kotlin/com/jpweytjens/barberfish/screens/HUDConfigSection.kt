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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.ETAKind
import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldState
import androidx.compose.ui.platform.LocalContext
import com.jpweytjens.barberfish.datatype.barberfishFieldRemoteViews
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.datatype.shared.gradeThreshold
import com.jpweytjens.barberfish.datatype.shared.ELEVATION_FIXTURES
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.previewElevationFixture
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.datatype.shared.visvalingamWhyatt
import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.CadenceThresholdConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.ElevationZoom
import com.jpweytjens.barberfish.extension.SparklineWarp
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
    sparklineConfig: SparklineConfig,
    zoneConfig: ZoneConfig,
    timeCfg: TimeConfig,
    profile: UserProfile,
    currentRouteElevationPolyline: String?,
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
    if (BuildConfig.DEBUG) {
        // "Current route" is prepended when a route (or destination) is loaded on the device,
        // so VW / warp tuning can be judged against real Strava-density data instead of the
        // synthetic fixtures, which have perfectly collinear climbs and therefore don't
        // exhibit the rainbow-banding problem.
        val fixtures: Map<String, () -> List<Pair<Float, Float>>> =
            remember(currentRouteElevationPolyline) {
                buildMap {
                    val poly = currentRouteElevationPolyline
                    if (!poly.isNullOrBlank()) {
                        put("Current route") { decodeElevationPolyline(poly) }
                    }
                    putAll(ELEVATION_FIXTURES)
                }
            }
        var selectedFixtureName by remember(fixtures) { mutableStateOf(fixtures.keys.first()) }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedFixtureName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Fixture") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                fixtures.keys.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { selectedFixtureName = name; expanded = false },
                    )
                }
            }
        }
        var previewSweepSeconds by remember { mutableIntStateOf(10) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Grey200)
                .padding(12.dp),
        ) {
            SegmentedRow(
                options = listOf(10 to "10s", 30 to "30s", 60 to "60s"),
                selected = previewSweepSeconds,
                onSelect = { previewSweepSeconds = it },
            )
        }
        HUDPreview(
            hudConfig = hudConfig,
            sparklineConfig = sparklineConfig,
            zoneConfig = zoneConfig,
            timeCfg = timeCfg,
            profile = profile,
            selectedSlot = selectedSlot,
            onSlotSelected = { idx -> selectedSlot = if (selectedSlot == idx) null else idx },
            fixturePoints = fixtures[selectedFixtureName]?.invoke() ?: previewElevationFixture(),
            previewSweepSeconds = previewSweepSeconds,
        )
    } else {
        HUDPreview(
            hudConfig = hudConfig,
            sparklineConfig = sparklineConfig,
            zoneConfig = zoneConfig,
            timeCfg = timeCfg,
            profile = profile,
            selectedSlot = selectedSlot,
            onSlotSelected = { idx -> selectedSlot = if (selectedSlot == idx) null else idx },
        )
    }

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
            profile = profile,
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
}

@Composable
private fun HUDPreview(
    hudConfig: HUDConfig,
    sparklineConfig: SparklineConfig,
    zoneConfig: ZoneConfig,
    timeCfg: TimeConfig,
    profile: UserProfile,
    selectedSlot: Int?,
    onSlotSelected: (Int) -> Unit,
    fixturePoints: List<Pair<Float, Float>>? = null,
    previewSweepSeconds: Int = 10,
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
    val current = states[index.coerceIn(states.indices)]

    val density = LocalDensity.current.density
    val isNightMode = isSystemInDarkTheme()
    val sparklineDisplayHeightPx = (32f * density).toInt()
    var boxWidthPx by remember { mutableIntStateOf(0) }

    val elevationPoints = fixturePoints ?: previewElevationFixture()

    // Animate position: sweep from route start to end, then loop
    var positionM by remember { mutableStateOf(elevationPoints.first().first) }
    var lastPositionM by remember { mutableStateOf(elevationPoints.first().first) }
    var displayedRange by remember { mutableStateOf(0f) }
    val routeEndM = remember(elevationPoints) { elevationPoints.last().first }
    // Total seconds to complete one full sweep at 30 fps.
    val speedMPerTick = remember(elevationPoints, previewSweepSeconds) {
        (routeEndM - elevationPoints.first().first) / (previewSweepSeconds * 30f)
    }
    LaunchedEffect(elevationPoints) {
        positionM = elevationPoints.first().first
        lastPositionM = elevationPoints.first().first
        displayedRange = 0f
        while (true) {
            delay(33L) // ~30fps
            positionM += speedMPerTick
            if (positionM > routeEndM) {
                positionM = elevationPoints.first().first
                lastPositionM = elevationPoints.first().first
                displayedRange = 0f
            }
        }
    }

    // VW runs once per (fixture, preset) change — not once per animation frame.
    val simplifiedElevationPoints = remember(elevationPoints, sparklineConfig.simplification) {
        visvalingamWhyatt(elevationPoints, sparklineConfig.simplification.minAreaM2)
    }

    val sparklineBitmap = remember(sparklineConfig, zoneConfig, boxWidthPx, isNightMode, simplifiedElevationPoints, positionM) {
        if (!sparklineConfig.enabled || boxWidthPx <= 0) null
        else {
            val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
            lastPositionM = positionM
            val (bitmap, newRange) = renderElevationSparkline(
                elevationPoints = simplifiedElevationPoints,
                positionM       = positionM,
                widthPx         = boxWidthPx,
                heightPx        = sparklineDisplayHeightPx,
                density         = density,
                palette         = zoneConfig.gradePalette,
                readable        = zoneConfig.readableColors,
                lookaheadM      = sparklineConfig.lookaheadKm * 1_000f,
                skipBands       = sparklineConfig.skipBands,
                displayedRange  = displayedRange,
                distanceDeltaM  = distanceDeltaM,
                isNightMode     = isNightMode,
                minElevRangeM   = sparklineConfig.yZoom.minRangeM,
                logWarpK        = sparklineConfig.warp.k,
                positionFraction = sparklineConfig.warp.positionFraction,
            )
            displayedRange = newRange
            bitmap
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSystemInDarkTheme()) Color.Black else Color.White)
            .onSizeChanged { boxWidthPx = it.width }
    ) {
        Row(Modifier.fillMaxSize()) {
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
                    sparklineEnabled = sparklineConfig.enabled,
                )
            }
        }
        if (sparklineBitmap != null) {
            Image(
                bitmap = sparklineBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter),
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
        val sparklineMarginPx = if (sparklineEnabled) 32f * density else 0f
        val slotHeightPx = heightPx - sparklineMarginPx.toInt()
        val sizeConfig = remember(baseConfig, widthPx, slotHeightPx, sparklineMarginPx) {
            baseConfig.copy(
                cellWidthPxOverride = widthPx.toFloat(),
            )
        }
        val bitmap = remember(field, colorMode, sizeConfig, slotHeightPx) {
            val rv = barberfishFieldRemoteViews(
                field = field,
                alignment = ViewConfig.Alignment.RIGHT,
                colorMode = colorMode,
                sizeConfig = sizeConfig,
                preview = true,
                context = context,
            )
            remoteViewsToBitmap(rv, widthPx, slotHeightPx, context)
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth,
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
    profile: UserProfile,
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
                HUDSlotField.Power -> HUDPowerCard(slot, onUpdate)
                HUDSlotField.AvgPower -> {}
                HUDSlotField.NP -> {}
                HUDSlotField.LapPower -> {}
                HUDSlotField.LastLapPower -> {}
                HUDSlotField.HR -> {}
                HUDSlotField.AvgHR -> {}
                HUDSlotField.LapAvgHR -> {}
                HUDSlotField.LastLapAvgHR -> {}
                HUDSlotField.Speed -> HUDSpeedCard(slot, onUpdate)
                is HUDSlotField.AvgSpeed -> AvgSpeedThresholdControls(
                    config = slot.avgSpeedConfig,
                    profile = profile,
                    onConfigChange = { onUpdate(slot.copy(avgSpeedConfig = it)) },
                )
                HUDSlotField.Cadence -> HUDCadenceCard(slot, onUpdate)
                HUDSlotField.Grade -> {}
                is HUDSlotField.Time -> {}
                is HUDSlotField.ETA -> {}
            }
            if (slot.field == HUDSlotField.Power || slot.field == HUDSlotField.AvgPower ||
                slot.field == HUDSlotField.NP || slot.field == HUDSlotField.LapPower ||
                slot.field == HUDSlotField.LastLapPower || slot.field == HUDSlotField.HR ||
                slot.field == HUDSlotField.AvgHR || slot.field == HUDSlotField.LapAvgHR ||
                slot.field == HUDSlotField.LastLapAvgHR || slot.field == HUDSlotField.Grade ||
                slot.field == HUDSlotField.Cadence) {
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
        when (val f = slot.field) {
            HUDSlotField.Power -> "Power"
            HUDSlotField.AvgPower -> "Avg Power"
            HUDSlotField.NP -> "NP"
            HUDSlotField.LapPower -> "Lap Power"
            HUDSlotField.LastLapPower -> "Last Lap Power"
            HUDSlotField.HR -> "Heart rate"
            HUDSlotField.AvgHR -> "Avg heart rate"
            HUDSlotField.LapAvgHR -> "Lap avg heart rate"
            HUDSlotField.LastLapAvgHR -> "Last lap avg heart rate"
            HUDSlotField.Speed -> "Speed"
            is HUDSlotField.AvgSpeed -> if (f.includePaused) "Avg Speed (Total)" else "Avg Speed (Moving)"
            HUDSlotField.Cadence -> "Cadence"
            HUDSlotField.Grade -> "Grade"
            is HUDSlotField.Time -> f.kind.label.replace("\n", " ")
            is HUDSlotField.ETA -> f.kind.label.replace("\n", " ")
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
            val groups = listOf(
                "Power" to listOf(
                    "Power" to HUDSlotField.Power,
                    "Avg Power" to HUDSlotField.AvgPower,
                    "NP" to HUDSlotField.NP,
                    "Lap Power" to HUDSlotField.LapPower,
                    "Last Lap Power" to HUDSlotField.LastLapPower,
                ),
                "Heart rate" to listOf(
                    "Heart rate" to HUDSlotField.HR,
                    "Avg heart rate" to HUDSlotField.AvgHR,
                    "Lap avg heart rate" to HUDSlotField.LapAvgHR,
                    "Last lap avg heart rate" to HUDSlotField.LastLapAvgHR,
                ),
                "Speed" to listOf(
                    "Speed" to HUDSlotField.Speed,
                    "Avg Speed (Moving)" to HUDSlotField.AvgSpeed(includePaused = false),
                    "Avg Speed (Total)" to HUDSlotField.AvgSpeed(includePaused = true),
                ),
                "Other" to listOf(
                    "Cadence" to HUDSlotField.Cadence,
                    "Grade" to HUDSlotField.Grade,
                ),
                "Duration" to listOf(
                    "Elapsed time" to HUDSlotField.Time(TimeKind.TOTAL),
                    "Moving time" to HUDSlotField.Time(TimeKind.RIDING),
                    "Paused time" to HUDSlotField.Time(TimeKind.PAUSED),
                    "Lap time" to HUDSlotField.Time(TimeKind.LAP),
                    "Last lap time" to HUDSlotField.Time(TimeKind.LAST_LAP),
                ),
                "Navigation" to listOf(
                    "Remaining ride time" to HUDSlotField.ETA(ETAKind.REMAINING_RIDE_TIME),
                    "To destination" to HUDSlotField.ETA(ETAKind.TIME_TO_DESTINATION),
                    "ETA" to HUDSlotField.ETA(ETAKind.TIME_OF_ARRIVAL),
                ),
                "Daylight" to listOf(
                    "Sunrise" to HUDSlotField.Time(TimeKind.TIME_TO_SUNRISE),
                    "Sunset" to HUDSlotField.Time(TimeKind.TIME_TO_SUNSET),
                    "Dawn" to HUDSlotField.Time(TimeKind.TIME_TO_CIVIL_DAWN),
                    "Dusk" to HUDSlotField.Time(TimeKind.TIME_TO_CIVIL_DUSK),
                ),
            )
            groups.forEachIndexed { groupIndex, (groupLabel, fields) ->
                if (groupIndex > 0) HorizontalDivider()
                Text(
                    groupLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Grey400,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                fields.forEach { (label, field) ->
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
private fun HUDCadenceCard(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    Text("SMOOTHING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
    SmoothingSlider(
        options = CadenceSmoothingStream.entries,
        selected = slot.cadenceSmoothing,
        label = { it.label },
        onSelected = { onUpdate(slot.copy(cadenceSmoothing = it)) },
        thumbIcon = R.drawable.ic_cadence,
    )
    CadenceThresholdControls(
        config = slot.cadenceThreshold,
        onConfigChange = { onUpdate(slot.copy(cadenceThreshold = it)) },
    )
}

@Composable
internal fun SparklineCard(
    config: SparklineConfig,
    palette: GradePalette,
    profile: UserProfile,
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
                options = listOf(5, 10, 20).map { km ->
                    val display = ConvertType.DISTANCE.toDisplay(km.toDouble(), profile).toInt()
                    km to "$display ${ConvertType.DISTANCE.unit(profile)}"
                },
                selected = config.lookaheadKm,
                onSelect = { onUpdate(config.copy(lookaheadKm = it)) },
            )
            val threshold = gradeThreshold(palette, config.skipBands)
            Text("MINIMUM GRADE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(
                if (config.skipBands == 0) "Skip the lowest color bands. Bands are set by the gradient palette."
                else "Skip the lowest color bands. Bands are set by the gradient palette. Grades below ≥${"%.0f".format(threshold)}% stay uncolored.",
                fontSize = 12.sp, color = TextDark,
            )
            SegmentedRow(
                options = listOf(0 to "Off", 1 to "1", 2 to "2", 3 to "3"),
                selected = config.skipBands,
                onSelect = { onUpdate(config.copy(skipBands = it)) },
            )
            Text("SIMPLIFICATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("Merges small elevation wiggles into larger same-colour blocks.", fontSize = 12.sp, color = TextDark)
            SegmentedRow(
                options = ElevationSimplification.entries.map { it to it.label },
                selected = config.simplification,
                onSelect = { onUpdate(config.copy(simplification = it)) },
            )
            Text("X-WARP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("Fisheye magnification around the position dot.", fontSize = 12.sp, color = TextDark)
            SegmentedRow(
                options = SparklineWarp.entries.map { it to it.label },
                selected = config.warp,
                onSelect = { onUpdate(config.copy(warp = it)) },
            )
            Text("Y-ZOOM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text("Zoom in on elevation changes. Close amplifies minor bumps, wide smooths them out.", fontSize = 12.sp, color = TextDark)
            SegmentedRow(
                options = ElevationZoom.entries.map { it to it.label },
                selected = config.yZoom,
                onSelect = { onUpdate(config.copy(yZoom = it)) },
            )
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

