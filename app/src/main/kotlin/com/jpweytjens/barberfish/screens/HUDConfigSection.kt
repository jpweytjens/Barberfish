package com.jpweytjens.barberfish.screens

import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.ETAKind
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.applySparklineHeaderChrome
import com.jpweytjens.barberfish.datatype.barberfishFieldRemoteViews
import com.jpweytjens.barberfish.datatype.shared.BarberfishYellow
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.Grey100
import com.jpweytjens.barberfish.datatype.shared.Grey200
import com.jpweytjens.barberfish.datatype.shared.PREVIEW_DELAY_MS
import com.jpweytjens.barberfish.datatype.shared.TextDark
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.colDeRatesClimbsFixture
import com.jpweytjens.barberfish.datatype.shared.colDeRatesElevationFixture
import com.jpweytjens.barberfish.datatype.shared.colDeRatesPoisFixture
import com.jpweytjens.barberfish.datatype.shared.previewElevationFixture
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.datatype.shared.resolveClimbReveal
import com.jpweytjens.barberfish.datatype.shared.rvvClimbsFixture
import com.jpweytjens.barberfish.datatype.shared.rvvPoisFixture
import com.jpweytjens.barberfish.datatype.shared.visvalingamWhyatt
import com.jpweytjens.barberfish.datatype.sparklineHeaderPx
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.ElevationZoom
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.SparklineWarp
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.ZoneDisplayMode
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.delay

private sealed interface HudSelection {
    data class Slot(val index: Int) : HudSelection

    data object Strip : HudSelection
}

// Vertical space reserved inside each HUD preview cell for the sparkline strip below.
// Matches HUD_SPARKLINE_HEIGHT_DP in HUDField (the live overlay strip). The strip itself
// here is rendered at 30.dp — the 4dp difference is an unresolved cosmetic mismatch
// (see audit #24); the reservation matches the live experience so cells size correctly.
private const val HUD_SPARKLINE_CELL_RESERVATION_DP = 34f

// Total height of the HUD preview container (3 or 4 cells side-by-side + sparkline strip).
private val HUD_PREVIEW_HEIGHT = 90.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HUDConfigSection(
    hudConfig: HUDConfig,
    sparklineConfig: SparklineConfig,
    zoneConfig: ZoneConfig,
    timeCfg: TimeConfig,
    profile: UserProfile,
    onUpdate: (HUDConfig) -> Unit,
    onSparklineUpdate: (SparklineConfig) -> Unit,
) {
    var selection by remember { mutableStateOf<HudSelection?>(null) }
    val selectedSlot = (selection as? HudSelection.Slot)?.index
    val stripSelected = selection is HudSelection.Strip

    ControlLabel("NUMBER OF COLUMNS")
    ColumnCountToggle(
        columns = hudConfig.columns,
        onSelect = { cols ->
            if (cols != hudConfig.columns) {
                selection = null
                onUpdate(hudConfig.copy(columns = cols))
            }
        },
    )
    SparklineModeToggle(
        mode = sparklineConfig.hudMode,
        onSelect = { mode ->
            if (mode == SparklineMode.OFF && stripSelected) selection = null
            onSparklineUpdate(sparklineConfig.copy(mode = mode))
        },
    )
    HelperText(
        if (sparklineConfig.hudMode != SparklineMode.OFF)
            "Tap a column or the elevation profile to configure it."
        else "Tap a column to configure it."
    )
    HUDPreview(
        hudConfig = hudConfig,
        sparklineConfig = sparklineConfig,
        zoneConfig = zoneConfig,
        timeCfg = timeCfg,
        profile = profile,
        selectedSlot = selectedSlot,
        onSlotSelected = { idx ->
            selection = if (selectedSlot == idx) null else HudSelection.Slot(idx)
        },
        stripSelected = stripSelected,
        onStripSelected = { selection = if (stripSelected) null else HudSelection.Strip },
    )

    val slot =
        when (selectedSlot) {
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
    if (stripSelected && sparklineConfig.hudMode != SparklineMode.OFF) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Grey200, RoundedCornerShape(6.dp))
                    .background(Grey200)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SparklineOptionsControls(
                config = sparklineConfig,
                zoneConfig = zoneConfig,
                profile = profile,
                onUpdate = onSparklineUpdate,
            )
        }
    }
}

// Matches the data-field header font (ibm-plex-sans-condensed) used in the rendered cells.
private val HudHeaderFontFamily = FontFamily(Font(DeviceFontFamilyName("ibm-plex-sans-condensed")))

@Composable
internal fun SparklinePreview(
    sparklineConfig: SparklineConfig,
    zoneConfig: ZoneConfig,
    previewSweepSeconds: Int = 10,
    onVisibleChange: (Boolean) -> Unit = {},
    spaceReserved: Boolean = true,
) {
    val density = LocalDensity.current.density
    val isNightMode = isSystemInDarkTheme()
    var boxWidthPx by remember { mutableIntStateOf(0) }
    var boxHeightPx by remember { mutableIntStateOf(0) }

    // Climbs mode previews against a real climb (Col de Rates); other modes keep the mixed RvV
    // terrain.
    val climbsMode = sparklineConfig.hudMode == SparklineMode.CLIMBS
    val elevationPoints =
        if (climbsMode) colDeRatesElevationFixture() else previewElevationFixture()
    val climbRanges = if (climbsMode) colDeRatesClimbsFixture() else rvvClimbsFixture()
    val poiDistances = if (climbsMode) colDeRatesPoisFixture() else rvvPoisFixture()

    // Animate position: sweep from route start to end, then loop
    var positionM by remember { mutableStateOf(elevationPoints.first().first) }
    var lastPositionM by remember { mutableStateOf(elevationPoints.first().first) }
    var displayedRange by remember { mutableStateOf(0f) }
    val routeEndM = remember(elevationPoints) { elevationPoints.last().first }
    // Total seconds to complete one full sweep at 30 fps.
    val speedMPerTick =
        remember(elevationPoints, previewSweepSeconds) {
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
    val simplifiedElevationPoints =
        remember(elevationPoints, sparklineConfig.simplification) {
            visvalingamWhyatt(elevationPoints, sparklineConfig.simplification.minAreaM2)
        }

    val reveal =
        resolveClimbReveal(
            sparklineConfig.hudMode,
            climbRanges,
            simplifiedElevationPoints,
            positionM,
        )
    // Tell the HUD preview whether the strip is currently showing, so it can reclaim the row
    // (matching on-device, where the area collapses to the Off-mode layout when no climb is near).
    LaunchedEffect(reveal.visible) { onVisibleChange(reveal.visible) }

    val sparklineBitmap =
        remember(
            sparklineConfig,
            zoneConfig,
            boxWidthPx,
            boxHeightPx,
            isNightMode,
            simplifiedElevationPoints,
            positionM,
            climbRanges,
            poiDistances,
            spaceReserved,
        ) {
            if (
                boxWidthPx <= 0 ||
                    boxHeightPx <= 0 ||
                    !reveal.visible ||
                    reveal.counterText != null ||
                    !spaceReserved
            )
                null
            else {
                val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
                lastPositionM = positionM
                val (climbEdge, descentEdge) = sparklineConfig.gradeEdges(zoneConfig.gradePalette)
                val (bitmap, newRange) =
                    renderElevationSparkline(
                        elevationPoints = simplifiedElevationPoints,
                        positionM = positionM,
                        widthPx = boxWidthPx,
                        heightPx = boxHeightPx,
                        density = density,
                        palette = zoneConfig.gradePalette,
                        // Sparkline always renders as a fill; use brand colors.
                        readable = false,
                        lookaheadM = sparklineConfig.lookaheadKm * 1_000f,
                        climbEdge = climbEdge,
                        descentEdge = descentEdge,
                        displayedRange = displayedRange,
                        distanceDeltaM = distanceDeltaM,
                        isNightMode = isNightMode,
                        minElevRangeM = sparklineConfig.yZoom.minRangeM,
                        logWarpK = sparklineConfig.warp.k,
                        positionFraction = sparklineConfig.warp.positionFraction,
                        climbRanges = climbRanges,
                        showClimbs = sparklineConfig.showClimbs,
                        poiDistances = poiDistances,
                        showPois = sparklineConfig.showPois,
                        windowOverride = reveal.windowOverride,
                    )
                displayedRange = newRange
                bitmap
            }
        }

    Box(
        modifier =
            Modifier.fillMaxSize().onSizeChanged {
                boxWidthPx = it.width
                boxHeightPx = it.height
            }
    ) {
        if (sparklineBitmap != null) {
            Image(
                bitmap = sparklineBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        } else if (reveal.counterText != null && spaceReserved) {
            Text(
                text = reveal.counterText,
                color = if (isNightMode) Color.White else Color.Black,
                fontSize = 14.sp,
                fontFamily = HudHeaderFontFamily,
                modifier = Modifier.align(Alignment.Center),
            )
        }
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
    stripSelected: Boolean,
    onStripSelected: () -> Unit,
    previewSweepSeconds: Int = 10,
) {
    val states =
        remember(hudConfig, zoneConfig, timeCfg, profile) {
            HUDField.previewStates(hudConfig, timeCfg, profile, zoneConfig)
        }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(states) {
        index = 0
        while (true) {
            delay(PREVIEW_DELAY_MS)
            index = (index + 1) % states.size
        }
    }
    val current = states[index.coerceIn(states.indices)]
    // Driven by the sparkline sweep: in Climbs mode the strip is hidden between climbs, and the
    // columns then reclaim the row exactly as on-device.
    var sparklineVisible by remember { mutableStateOf(false) }
    val showSparkline = sparklineConfig.hudMode != SparklineMode.OFF && sparklineVisible

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(HUD_PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSystemInDarkTheme()) Color.Black else Color.White)
    ) {
        Row(Modifier.fillMaxSize()) {
            buildList {
                add(Triple(0, current.left.field, current.left.colorMode))
                add(Triple(1, current.middle.field, current.middle.colorMode))
                add(Triple(2, current.right.field, current.right.colorMode))
                if (hudConfig.columns == 4)
                    add(Triple(3, current.fourth.field, current.fourth.colorMode))
            }
                .forEach { (idx, field, colorMode) ->
                    HUDPreviewCell(
                        field = field,
                        colorMode = colorMode,
                        selected = selectedSlot == idx,
                        onClick = { onSlotSelected(idx) },
                        modifier = Modifier.weight(1f),
                        columns = hudConfig.columns,
                        reserveSparklineSpace = showSparkline,
                    )
                }
        }
        if (sparklineConfig.hudMode != SparklineMode.OFF) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .pointerInput(onStripSelected) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onStripSelected()
                            }
                        }
                        .then(
                            if (stripSelected)
                                Modifier.border(2.dp, BarberfishYellow, RoundedCornerShape(6.dp))
                            else Modifier
                        )
            ) {
                SparklinePreview(
                    sparklineConfig = sparklineConfig,
                    zoneConfig = zoneConfig,
                    previewSweepSeconds = previewSweepSeconds,
                    onVisibleChange = { sparklineVisible = it },
                    spaceReserved = showSparkline,
                )
            }
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
    reserveSparklineSpace: Boolean = true,
) {
    val context = LocalContext.current
    val baseConfig =
        if (columns == 4) ViewSizeConfig.PREVIEW_HUD_FOUR else ViewSizeConfig.PREVIEW_HUD_THREE
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
                    if (selected) Modifier.border(2.dp, BarberfishYellow, RoundedCornerShape(6.dp))
                    else Modifier
                )
    ) {
        val density = LocalDensity.current.density
        val widthPx = (maxWidth.value * density).toInt()
        val heightPx = (maxHeight.value * density).toInt()
        val sparklineMarginPx =
            if (reserveSparklineSpace) HUD_SPARKLINE_CELL_RESERVATION_DP * density else 0f
        val slotHeightPx = heightPx - sparklineMarginPx.toInt()
        val design = LocalDataFieldDesign.current
        val sizeConfig =
            remember(baseConfig, widthPx, slotHeightPx, sparklineMarginPx, design) {
                baseConfig.copy(
                    cellWidthPxOverride = widthPx.toFloat(),
                    showIcons = design.showIcons,
                )
            }
        val bitmap =
            remember(field, colorMode, sizeConfig, slotHeightPx) {
                val rv =
                    barberfishFieldRemoteViews(
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
    SegmentedRow(
        options = listOf(3 to "3", 4 to "4"),
        selected = columns,
        onSelect = onSelect,
        trackColor = Grey200,
    )
}

@Composable
private fun SparklineModeToggle(mode: SparklineMode, onSelect: (SparklineMode) -> Unit) {
    ControlLabel("ELEVATION PROFILE")
    SegmentedRow(
        options =
            listOf(
                SparklineMode.OFF to "Off",
                SparklineMode.CLIMBS to "Climbs",
                SparklineMode.ON to "On",
            ),
        selected = mode,
        onSelect = onSelect,
        trackColor = Grey200,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HUDSlotFieldCard(
    slot: HUDSlotConfig,
    profile: UserProfile,
    onUpdate: (HUDSlotConfig) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Grey200, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Grey100).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HUDFieldTypeDropdown(slot = slot, onUpdate = onUpdate)
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(Grey200).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val f = slot.field) {
                HUDSlotField.Power -> HUDPowerCard(slot, onUpdate)
                HUDSlotField.AvgPower -> {}
                HUDSlotField.NP -> {}
                HUDSlotField.LapPower -> {}
                HUDSlotField.LastLapPower -> {}
                HUDSlotField.PowerZone -> {}
                HUDSlotField.MaxPower -> {}
                HUDSlotField.HR -> {}
                HUDSlotField.AvgHR -> {}
                HUDSlotField.LapAvgHR -> {}
                HUDSlotField.LastLapAvgHR -> {}
                HUDSlotField.HRMaxPercent -> {}
                HUDSlotField.MaxHR -> {}
                HUDSlotField.HRZone -> {}
                HUDSlotField.Speed -> HUDSpeedCard(slot, onUpdate)
                is HUDSlotField.AvgSpeed ->
                    AvgSpeedThresholdControls(
                        config = slot.avgSpeedConfig,
                        profile = profile,
                        onConfigChange = { onUpdate(slot.copy(avgSpeedConfig = it)) },
                    )
                HUDSlotField.Cadence -> HUDCadenceCard(slot, onUpdate)
                HUDSlotField.Grade -> HUDGradeCard(slot, onUpdate)
                HUDSlotField.Distance -> {}
                HUDSlotField.DistanceRemaining -> {}
                HUDSlotField.ElevationRemaining -> {}
                HUDSlotField.DescentRemaining -> {}
                is HUDSlotField.Time -> {}
                is HUDSlotField.ETA -> {}
            }
            if (
                slot.field == HUDSlotField.Power ||
                    slot.field == HUDSlotField.AvgPower ||
                    slot.field == HUDSlotField.NP ||
                    slot.field == HUDSlotField.LapPower ||
                    slot.field == HUDSlotField.LastLapPower ||
                    slot.field == HUDSlotField.PowerZone ||
                    slot.field == HUDSlotField.MaxPower ||
                    slot.field == HUDSlotField.HR ||
                    slot.field == HUDSlotField.AvgHR ||
                    slot.field == HUDSlotField.LapAvgHR ||
                    slot.field == HUDSlotField.LastLapAvgHR ||
                    slot.field == HUDSlotField.HRMaxPercent ||
                    slot.field == HUDSlotField.MaxHR ||
                    slot.field == HUDSlotField.HRZone ||
                    slot.field == HUDSlotField.Grade ||
                    slot.field == HUDSlotField.Cadence
            ) {
                ZoneColorSlider(
                    selected = slot.colorMode,
                    onSelected = { onUpdate(slot.copy(colorMode = it)) },
                )
            }
            if (slot.field == HUDSlotField.HRZone || slot.field == HUDSlotField.PowerZone) {
                ZoneDisplaySlider(
                    selected = slot.zoneDisplayMode,
                    onSelected = { onUpdate(slot.copy(zoneDisplayMode = it)) },
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
            HUDSlotField.NP -> "Normalized Power"
            HUDSlotField.LapPower -> "Lap Power"
            HUDSlotField.LastLapPower -> "Last Lap Power"
            HUDSlotField.PowerZone -> "Power Zone"
            HUDSlotField.MaxPower -> "Max Power"
            HUDSlotField.HR -> "Heart rate"
            HUDSlotField.AvgHR -> "Avg heart rate"
            HUDSlotField.LapAvgHR -> "Lap avg heart rate"
            HUDSlotField.LastLapAvgHR -> "Last lap avg heart rate"
            HUDSlotField.HRMaxPercent -> "%Max HR"
            HUDSlotField.MaxHR -> "Max HR"
            HUDSlotField.HRZone -> "HR Zone"
            HUDSlotField.Speed -> "Speed"
            is HUDSlotField.AvgSpeed ->
                if (f.includePaused) "Avg Speed (Total)" else "Avg Speed (Moving)"
            HUDSlotField.Cadence -> "Cadence"
            HUDSlotField.Grade -> "Grade"
            HUDSlotField.Distance -> "Distance"
            HUDSlotField.DistanceRemaining -> "Distance remaining"
            HUDSlotField.ElevationRemaining -> "Ascent remaining"
            HUDSlotField.DescentRemaining -> "Descent remaining"
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
            val groups =
                listOf(
                    "Power" to
                        listOf(
                            "Power" to HUDSlotField.Power,
                            "Avg Power" to HUDSlotField.AvgPower,
                            "Normalized Power" to HUDSlotField.NP,
                            "Lap Power" to HUDSlotField.LapPower,
                            "Last Lap Power" to HUDSlotField.LastLapPower,
                            "Power Zone" to HUDSlotField.PowerZone,
                            "Max Power" to HUDSlotField.MaxPower,
                        ),
                    "Heart rate" to
                        listOf(
                            "Heart rate" to HUDSlotField.HR,
                            "Avg heart rate" to HUDSlotField.AvgHR,
                            "Lap avg heart rate" to HUDSlotField.LapAvgHR,
                            "Last lap avg heart rate" to HUDSlotField.LastLapAvgHR,
                            "%Max HR" to HUDSlotField.HRMaxPercent,
                            "Max HR" to HUDSlotField.MaxHR,
                            "HR Zone" to HUDSlotField.HRZone,
                        ),
                    "Speed" to
                        listOf(
                            "Speed" to HUDSlotField.Speed,
                            "Avg Speed (Total)" to HUDSlotField.AvgSpeed(includePaused = true),
                            "Avg Speed (Moving)" to HUDSlotField.AvgSpeed(includePaused = false),
                        ),
                    "Cadence" to listOf("Cadence" to HUDSlotField.Cadence),
                    "Climbing" to listOf("Grade" to HUDSlotField.Grade),
                    "Navigation" to
                        listOf(
                            "Distance" to HUDSlotField.Distance,
                            "Distance remaining" to HUDSlotField.DistanceRemaining,
                            "Ascent remaining" to HUDSlotField.ElevationRemaining,
                            "Descent remaining" to HUDSlotField.DescentRemaining,
                        ),
                    "Time" to
                        listOf(
                            "Elapsed time" to HUDSlotField.Time(TimeKind.TOTAL),
                            "Moving time" to HUDSlotField.Time(TimeKind.RIDING),
                            "Paused time" to HUDSlotField.Time(TimeKind.PAUSED),
                            "Lap time" to HUDSlotField.Time(TimeKind.LAP),
                            "Last lap time" to HUDSlotField.Time(TimeKind.LAST_LAP),
                        ),
                    "ETA" to
                        listOf(
                            "Remaining ride time" to HUDSlotField.ETA(ETAKind.REMAINING_RIDE_TIME),
                            "To destination" to HUDSlotField.ETA(ETAKind.TIME_TO_DESTINATION),
                            "ETA" to HUDSlotField.ETA(ETAKind.TIME_OF_ARRIVAL),
                        ),
                    "Daylight" to
                        listOf(
                            "Sunrise" to HUDSlotField.Time(TimeKind.TIME_TO_SUNRISE),
                            "Sunset" to HUDSlotField.Time(TimeKind.TIME_TO_SUNSET),
                            "Dawn" to HUDSlotField.Time(TimeKind.TIME_TO_CIVIL_DAWN),
                            "Dusk" to HUDSlotField.Time(TimeKind.TIME_TO_CIVIL_DUSK),
                        ),
                )
            groups.forEachIndexed { groupIndex, (groupLabel, fields) ->
                if (groupIndex > 0) HorizontalDivider()
                Caption(
                    groupLabel,
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
    ControlLabel("SMOOTHING")
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
    ControlLabel("SMOOTHING")
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
    ControlLabel("SMOOTHING")
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
private fun HUDGradeCard(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    ChoiceRow(
        label = "DECIMALS",
        options =
            listOf(
                ZoneDisplayMode.INTEGER to "Integer",
                ZoneDisplayMode.FLOAT to "Decimal",
            ),
        selected = slot.gradePrecision,
        onSelect = { onUpdate(slot.copy(gradePrecision = it)) },
    )
    BoolToggleRow(
        label = "PERCENT SIGN",
        value = slot.gradeShowPercentSign,
        onChange = { onUpdate(slot.copy(gradeShowPercentSign = it)) },
    )
}

@Composable
internal fun SparklineOptionsControls(
    config: SparklineConfig,
    zoneConfig: ZoneConfig,
    profile: UserProfile,
    onUpdate: (SparklineConfig) -> Unit,
) {
    // Lookahead is inert in Climbs mode: the window is pinned to the climb, not your position.
    if (config.hudMode != SparklineMode.CLIMBS) {
        ChoiceRow(
            label = "LOOKAHEAD",
            options =
                listOf(5, 10, 20).map { km ->
                    val display = ConvertType.DISTANCE.toDisplay(km.toDouble(), profile).toInt()
                    km to "$display ${ConvertType.DISTANCE.unit(profile)}"
                },
            selected = config.lookaheadKm,
            onSelect = { onUpdate(config.copy(lookaheadKm = it)) },
            help = "Distance shown ahead of your position.",
        )
    }
    // Read through gradeEdges, the same resolution the renderers use, so the bar and thumbs
    // show exactly which bands the profile colours.
    val (climbEdge, descentEdge) = config.gradeEdges(zoneConfig.gradePalette)
    LabeledHelper("EMPHASIS") {
        HelperText("Filter out gentle grades so meaningful climbs and descents stand out.")
    }
    // The profile adds no colour inside the edges (its faint silhouette shows through), so its
    // neutral is the card background: an excluded band reads as no colour added.
    GradeBandBar(
        palette = zoneConfig.gradePalette,
        climbEdge = climbEdge,
        descentEdge = descentEdge,
        neutral = Grey200,
    )
    GradeEdgeSliders(
        palette = zoneConfig.gradePalette,
        climbEdge = climbEdge,
        descentEdge = descentEdge,
        onEdgesChange = { climb, descent ->
            onUpdate(config.copy(climbEdge = climb, descentEdge = descent))
        },
    )
    ChoiceRow(
        label = "SIMPLIFICATION",
        options = ElevationSimplification.entries.map { it to it.label },
        selected = config.simplification,
        onSelect = { onUpdate(config.copy(simplification = it)) },
        help = "Merges small elevation wiggles into larger same-colour blocks.",
    )
    // X-warp is inert in Climbs mode: the climb frame uses a linear axis, not a fisheye.
    if (config.hudMode != SparklineMode.CLIMBS) {
        ChoiceRow(
            label = "X-WARP",
            options = SparklineWarp.entries.map { it to it.label },
            selected = config.warp,
            onSelect = { onUpdate(config.copy(warp = it)) },
            help = "Fisheye magnification around the position dot.",
        )
    }
    ChoiceRow(
        label = "Y-ZOOM",
        options = ElevationZoom.entries.map { it to it.label },
        selected = config.yZoom,
        onSelect = { onUpdate(config.copy(yZoom = it)) },
        help = "Zoom in on elevation changes. Close amplifies minor bumps, wide smooths them out.",
    )
    BoolToggleRow(
        label = "CLIMBS",
        value = config.showClimbs,
        onChange = { onUpdate(config.copy(showClimbs = it)) },
        help = "Tint the outline blue on climbs as detected by Karoo Climber.",
    )
    BoolToggleRow(
        label = "POIs",
        value = config.showPois,
        onChange = { onUpdate(config.copy(showPois = it)) },
        help = "Mark points of interest (POIs) along the elevation profile.",
    )
    BoolToggleRow(
        label = "HEADER",
        value = config.showHeader,
        onChange = { onUpdate(config.copy(showHeader = it)) },
        help = "Show the field name and icon above the profile.",
    )
}

@Composable
internal fun SparklineCard(
    config: SparklineConfig,
    zoneConfig: ZoneConfig,
    profile: UserProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (SparklineConfig) -> Unit,
) {
    ExpandableCard(
        title = "PROFILE",
        selected = selected,
        onSelect = onSelect,
        headerExtra = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HelperText("Elevation profile shown ahead when a route is loaded.")
                val context = LocalContext.current
                val density = LocalDensity.current.density
                val isNight = isSystemInDarkTheme()
                BoxWithConstraints(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isNight) Color.Black else Color.White)
                ) {
                    val widthPx = (maxWidth.value * density).toInt().coerceAtLeast(1)
                    val sizeConfig =
                        ViewSizeConfig.STANDARD.copy(cellWidthPxOverride = widthPx.toFloat())
                    val headerPx = sparklineHeaderPx(sizeConfig, density)
                    // Header drawn by the same chrome the live cell uses, so the preview's icon
                    // and label match the data field exactly; the body below stays the animated
                    // Compose sparkline.
                    val headerBitmap =
                        remember(widthPx, headerPx, isNight) {
                            val rv = RemoteViews(context.packageName, R.layout.barberfish_sparkline)
                            applySparklineHeaderChrome(
                                rv,
                                context.getString(R.string.elevation_sparkline_name),
                                R.drawable.ic_grade,
                                sizeConfig,
                                ViewConfig.Alignment.RIGHT,
                                context,
                            )
                            remoteViewsToBitmap(rv, widthPx, headerPx, context)
                        }
                    Column {
                        if (config.showHeader) {
                            Image(
                                bitmap = headerBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height((headerPx / density).dp),
                                contentScale = ContentScale.FillBounds,
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            SparklinePreview(
                                sparklineConfig = config,
                                zoneConfig = zoneConfig,
                            )
                        }
                    }
                }
            }
        },
    ) {
        SparklineOptionsControls(
            config = config,
            zoneConfig = zoneConfig,
            profile = profile,
            onUpdate = onUpdate,
        )
    }
}

@Composable
internal fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(trackColor)
                .padding(3.dp)
                .pointerInput(options, onSelect) {
                    val slotWidthPx = size.width.toFloat() / options.size
                    fun idxAt(x: Float) = (x / slotWidthPx).toInt().coerceIn(0, options.lastIndex)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onSelect(options[idxAt(down.position.x)].first)
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            onSelect(options[idxAt(change.position.x)].first)
                            event = awaitPointerEvent()
                        }
                    }
                }
    ) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) BarberfishYellow else Color.Transparent)
                        .padding(vertical = 6.dp),
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
