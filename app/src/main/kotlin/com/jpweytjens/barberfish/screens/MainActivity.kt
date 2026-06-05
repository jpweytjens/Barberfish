package com.jpweytjens.barberfish.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.AvgHRField
import com.jpweytjens.barberfish.datatype.AvgPowerField
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.CadenceField
import com.jpweytjens.barberfish.datatype.EffortField
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HRField
import com.jpweytjens.barberfish.datatype.HRMaxPercentField
import com.jpweytjens.barberfish.datatype.HRZoneField
import com.jpweytjens.barberfish.datatype.LapAvgHRField
import com.jpweytjens.barberfish.datatype.LapPowerField
import com.jpweytjens.barberfish.datatype.LastLapAvgHRField
import com.jpweytjens.barberfish.datatype.MaxHRField
import com.jpweytjens.barberfish.datatype.MaxPowerField
import com.jpweytjens.barberfish.datatype.NPField
import com.jpweytjens.barberfish.datatype.PowerField
import com.jpweytjens.barberfish.datatype.PowerZoneField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.formatTime
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.DANGER_ORANGE
import com.jpweytjens.barberfish.datatype.shared.OceanBlue
import com.jpweytjens.barberfish.datatype.shared.overviewPreviewBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.jpweytjens.barberfish.datatype.barberfishFieldRemoteViews
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.datatype.shared.PREVIEW_DELAY_MS
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.RDYLGN_GREEN
import com.jpweytjens.barberfish.datatype.shared.RDYLGN_RED
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.bestTextOnBackground
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.datatype.shared.gradeFillRange
import com.jpweytjens.barberfish.datatype.shared.hrZoneColor
import com.jpweytjens.barberfish.datatype.shared.powerZoneColor
import com.jpweytjens.barberfish.datatype.shared.BackButtonTint
import com.jpweytjens.barberfish.datatype.shared.BarberfishYellow
import com.jpweytjens.barberfish.datatype.shared.Grey100
import com.jpweytjens.barberfish.datatype.shared.Grey200
import com.jpweytjens.barberfish.datatype.shared.Grey400
import com.jpweytjens.barberfish.datatype.shared.Grey500
import com.jpweytjens.barberfish.datatype.shared.ICON_TINT_TEAL
import com.jpweytjens.barberfish.datatype.shared.TextDark
import com.jpweytjens.barberfish.extension.AvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.ETAConfig
import com.jpweytjens.barberfish.extension.LapPowerFieldConfig
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.CadenceThresholdConfig
import com.jpweytjens.barberfish.extension.ClimberMapConfig
import com.jpweytjens.barberfish.extension.ElevationSimplification
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.HRFieldKind
import com.jpweytjens.barberfish.extension.HRMaxPercentFieldConfig
import com.jpweytjens.barberfish.extension.HRZoneFieldConfig
import com.jpweytjens.barberfish.extension.MaxHRFieldConfig
import com.jpweytjens.barberfish.extension.MaxPowerFieldConfig
import com.jpweytjens.barberfish.extension.ZoneDisplayMode
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.NPFieldConfig
import com.jpweytjens.barberfish.extension.EffortFieldConfig
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.PowerZoneFieldConfig
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import com.jpweytjens.barberfish.extension.ThresholdMode
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.saveEffortFieldConfig
import com.jpweytjens.barberfish.extension.saveETAConfig
import com.jpweytjens.barberfish.extension.saveAvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.saveAvgSpeedConfig
import com.jpweytjens.barberfish.extension.saveCadenceFieldConfig
import com.jpweytjens.barberfish.extension.saveGradeFieldConfig
import com.jpweytjens.barberfish.extension.saveHRFieldConfig
import com.jpweytjens.barberfish.extension.saveHRMaxPercentFieldConfig
import com.jpweytjens.barberfish.extension.saveHRZoneFieldConfig
import com.jpweytjens.barberfish.extension.saveMaxHRFieldConfig
import com.jpweytjens.barberfish.extension.saveMaxPowerFieldConfig
import com.jpweytjens.barberfish.extension.saveHUDConfig
import com.jpweytjens.barberfish.extension.saveLapPowerFieldConfig
import com.jpweytjens.barberfish.extension.saveNPFieldConfig
import com.jpweytjens.barberfish.extension.savePowerFieldConfig
import com.jpweytjens.barberfish.extension.savePowerZoneFieldConfig
import com.jpweytjens.barberfish.extension.saveSpeedFieldConfig
import com.jpweytjens.barberfish.extension.saveTimeConfig
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamEffortFieldConfig
import com.jpweytjens.barberfish.extension.streamETAConfig
import com.jpweytjens.barberfish.extension.streamAvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamCadenceFieldConfig
import com.jpweytjens.barberfish.extension.streamGradeFieldConfig
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
import com.jpweytjens.barberfish.extension.streamHRMaxPercentFieldConfig
import com.jpweytjens.barberfish.extension.streamHRZoneFieldConfig
import com.jpweytjens.barberfish.extension.streamMaxHRFieldConfig
import com.jpweytjens.barberfish.extension.streamMaxPowerFieldConfig
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.saveClimberMapConfig
import com.jpweytjens.barberfish.extension.saveFieldSparklineConfig
import com.jpweytjens.barberfish.extension.saveHudSparklineConfig
import com.jpweytjens.barberfish.extension.streamClimberMapConfig
import com.jpweytjens.barberfish.extension.streamFieldSparklineConfig
import com.jpweytjens.barberfish.extension.streamHudSparklineConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamLapPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamNPFieldConfig
import com.jpweytjens.barberfish.extension.streamPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamPowerZoneFieldConfig
import com.jpweytjens.barberfish.extension.streamSpeedFieldConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var karooSystem: KarooSystemService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect {}
        actionBar?.title =
            android.text.SpannableString(getString(R.string.extension_name)).also {
                it.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK),
                    0,
                    it.length,
                    android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE,
                )
            }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = OceanBlue, onPrimary = Color.White),
            ) { ConfigScreen() }
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    @Composable
    private fun ConfigScreen() {
        var hudConfig by remember { mutableStateOf(HUDConfig()) }
        var hudSparklineConfig by remember { mutableStateOf(SparklineConfig()) }
        var fieldSparklineConfig by remember { mutableStateOf(SparklineConfig()) }
        var climberMapConfig by remember { mutableStateOf(ClimberMapConfig()) }
        var powerFieldConfig by remember { mutableStateOf(PowerFieldConfig()) }
        var hrFieldConfig by remember { mutableStateOf(HRFieldConfig()) }
        var avgHrFieldConfig by remember { mutableStateOf(HRFieldConfig()) }
        var lapAvgHrFieldConfig by remember { mutableStateOf(HRFieldConfig()) }
        var lastLapAvgHrFieldConfig by remember { mutableStateOf(HRFieldConfig()) }
        var hrMaxPercentFieldConfig by remember { mutableStateOf(HRMaxPercentFieldConfig()) }
        var maxHrFieldConfig by remember { mutableStateOf(MaxHRFieldConfig()) }
        var hrZoneFieldConfig by remember { mutableStateOf(HRZoneFieldConfig()) }
        var speedFieldConfig by remember { mutableStateOf(SpeedFieldConfig()) }
        var cadenceFieldConfig by remember { mutableStateOf(CadenceFieldConfig()) }
        var avgPowerFieldConfig by remember { mutableStateOf(AvgPowerFieldConfig()) }
        var npFieldConfig by remember { mutableStateOf(NPFieldConfig()) }
        var lapPowerFieldConfig by remember { mutableStateOf(LapPowerFieldConfig()) }
        var lastLapPowerFieldConfig by remember { mutableStateOf(LapPowerFieldConfig()) }
        var powerZoneFieldConfig by remember { mutableStateOf(PowerZoneFieldConfig()) }
        var maxPowerFieldConfig by remember { mutableStateOf(MaxPowerFieldConfig()) }
        var gradeFieldConfig by remember { mutableStateOf(GradeFieldConfig()) }
        var avgTotalConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var avgMovingConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var timeConfig by remember { mutableStateOf(TimeConfig()) }
        var etaConfig by remember { mutableStateOf(ETAConfig()) }
        var effortFieldConfig by remember { mutableStateOf(EffortFieldConfig()) }
        var zoneConfig by remember { mutableStateOf(ZoneConfig()) }
        var userProfile by remember {
            mutableStateOf(
                UserProfile(
                    weight = 70f,
                    preferredUnit =
                        UserProfile.PreferredUnit(
                            distance = UserProfile.PreferredUnit.UnitType.METRIC,
                            elevation = UserProfile.PreferredUnit.UnitType.METRIC,
                            temperature = UserProfile.PreferredUnit.UnitType.METRIC,
                            weight = UserProfile.PreferredUnit.UnitType.METRIC,
                        ),
                    maxHr = 190,
                    restingHr = 60,
                    heartRateZones = emptyList(),
                    ftp = 250,
                    powerZones = emptyList(),
                )
            )
        }

        var fieldsExpanded by remember { mutableStateOf(false) }
        var hudExpanded by remember { mutableStateOf(false) }
        var climberExpanded by remember { mutableStateOf(false) }
        var etaExpanded by remember { mutableStateOf(false) }
        var globalExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch { streamHUDConfig().collect { hudConfig = it } }
            launch { streamHudSparklineConfig().collect { hudSparklineConfig = it } }
            launch { streamFieldSparklineConfig().collect { fieldSparklineConfig = it } }
            launch { streamClimberMapConfig().collect { climberMapConfig = it } }
            launch { streamPowerFieldConfig().collect { powerFieldConfig = it } }
            launch { streamHRFieldConfig().collect { hrFieldConfig = it } }
            launch { streamHRFieldConfig(HRFieldKind.AVG).collect { avgHrFieldConfig = it } }
            launch { streamHRFieldConfig(HRFieldKind.LAP_AVG).collect { lapAvgHrFieldConfig = it } }
            launch { streamHRFieldConfig(HRFieldKind.LAST_LAP_AVG).collect { lastLapAvgHrFieldConfig = it } }
            launch { streamHRMaxPercentFieldConfig().collect { hrMaxPercentFieldConfig = it } }
            launch { streamMaxHRFieldConfig().collect { maxHrFieldConfig = it } }
            launch { streamHRZoneFieldConfig().collect { hrZoneFieldConfig = it } }
            launch { streamSpeedFieldConfig().collect { speedFieldConfig = it } }
            launch { streamCadenceFieldConfig().collect { cadenceFieldConfig = it } }
            launch { streamAvgPowerFieldConfig().collect { avgPowerFieldConfig = it } }
            launch { streamNPFieldConfig().collect { npFieldConfig = it } }
            launch { streamLapPowerFieldConfig(isLastLap = false).collect { lapPowerFieldConfig = it } }
            launch { streamLapPowerFieldConfig(isLastLap = true).collect { lastLapPowerFieldConfig = it } }
            launch { streamPowerZoneFieldConfig().collect { powerZoneFieldConfig = it } }
            launch { streamMaxPowerFieldConfig().collect { maxPowerFieldConfig = it } }
            launch { streamGradeFieldConfig().collect { gradeFieldConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = true).collect { avgTotalConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = false).collect { avgMovingConfig = it } }
            launch { streamTimeConfig().collect { timeConfig = it } }
            launch { streamETAConfig().collect { etaConfig = it } }
            launch { streamEffortFieldConfig().collect { effortFieldConfig = it } }
            launch { streamZoneConfig().collect { zoneConfig = it } }
            launch { karooSystem.streamUserProfile().collect { userProfile = it } }
        }

        Box(modifier = Modifier.fillMaxSize().background(Grey100)) {
            Column(
                modifier =
                    Modifier.fillMaxSize().padding(6.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CollapsibleSection(
                    title = "HUD",
                    description = "Configure the heads-up display",
                    icon = R.drawable.ic_section_hud,
                    expanded = hudExpanded,
                    onToggle = { hudExpanded = !hudExpanded },
                ) {
                    HUDConfigSection(
                        hudConfig = hudConfig,
                        sparklineConfig = hudSparklineConfig,
                        zoneConfig = zoneConfig,
                        timeCfg = timeConfig,
                        profile = userProfile,
                        onUpdate = { updated ->
                            hudConfig = updated
                            lifecycleScope.launch { saveHUDConfig(updated) }
                        },
                        onSparklineUpdate = { updated ->
                            hudSparklineConfig = updated
                            lifecycleScope.launch { saveHudSparklineConfig(updated) }
                        },
                    )
                } // end HUD

                val powerPreviewStates = remember(powerFieldConfig, userProfile, zoneConfig) {
                    PowerField.previewStates(powerFieldConfig, userProfile, zoneConfig)
                }
                val hrPreviewStates = remember(hrFieldConfig, userProfile, zoneConfig) {
                    HRField.previewStates(hrFieldConfig, userProfile, zoneConfig)
                }
                val avgHrPreviewStates = remember(avgHrFieldConfig, userProfile, zoneConfig) {
                    AvgHRField.previewStates(avgHrFieldConfig, userProfile, zoneConfig)
                }
                val lapAvgHrPreviewStates = remember(lapAvgHrFieldConfig, userProfile, zoneConfig) {
                    LapAvgHRField.previewStates(lapAvgHrFieldConfig, userProfile, zoneConfig)
                }
                val lastLapAvgHrPreviewStates = remember(lastLapAvgHrFieldConfig, userProfile, zoneConfig) {
                    LastLapAvgHRField.previewStates(lastLapAvgHrFieldConfig, userProfile, zoneConfig)
                }
                val hrMaxPercentPreviewStates = remember(hrMaxPercentFieldConfig, userProfile, zoneConfig) {
                    HRMaxPercentField.previewStates(hrMaxPercentFieldConfig, userProfile, zoneConfig)
                }
                val maxHrPreviewStates = remember(maxHrFieldConfig, userProfile, zoneConfig) {
                    MaxHRField.previewStates(maxHrFieldConfig, userProfile, zoneConfig)
                }
                val hrZonePreviewStates = remember(hrZoneFieldConfig, userProfile, zoneConfig) {
                    HRZoneField.previewStates(hrZoneFieldConfig, userProfile, zoneConfig)
                }
                val speedPreviewStates = remember(speedFieldConfig, userProfile) {
                    SpeedField.previewStates(speedFieldConfig, userProfile)
                }
                val cadencePreviewStates = remember(cadenceFieldConfig) {
                    CadenceField.previewStates(cadenceFieldConfig)
                }
                val avgPowerPreviewStates = remember(avgPowerFieldConfig, userProfile, zoneConfig) {
                    AvgPowerField.previewStates(avgPowerFieldConfig, userProfile, zoneConfig)
                }
                val npPreviewStates = remember(npFieldConfig, userProfile, zoneConfig) {
                    NPField.previewStates(npFieldConfig, userProfile, zoneConfig)
                }
                val lapPowerPreviewStates = remember(lapPowerFieldConfig, userProfile, zoneConfig) {
                    LapPowerField.previewStates(lapPowerFieldConfig, userProfile, zoneConfig, isLastLap = false)
                }
                val lastLapPowerPreviewStates = remember(lastLapPowerFieldConfig, userProfile, zoneConfig) {
                    LapPowerField.previewStates(lastLapPowerFieldConfig, userProfile, zoneConfig, isLastLap = true)
                }
                val powerZonePreviewStates = remember(powerZoneFieldConfig, userProfile, zoneConfig) {
                    PowerZoneField.previewStates(powerZoneFieldConfig, userProfile, zoneConfig)
                }
                val maxPowerPreviewStates = remember(maxPowerFieldConfig, userProfile, zoneConfig) {
                    MaxPowerField.previewStates(maxPowerFieldConfig, userProfile, zoneConfig)
                }
                val gradePreviewStates = remember(gradeFieldConfig, zoneConfig) {
                    GradeField.previewStates(gradeFieldConfig, zoneConfig)
                }
                val effortPreviewStates = remember(effortFieldConfig, userProfile) {
                    EffortField.previewStates(userProfile, effortFieldConfig.climbFirst)
                }

                CollapsibleSection(
                    title = "Data fields",
                    description = "Configure standalone data fields",
                    icon = R.drawable.ic_section_fields,
                    expanded = fieldsExpanded,
                    onToggle = { fieldsExpanded = !fieldsExpanded },
                ) {
                    var selectedDataField by remember { mutableStateOf<String?>(null) }

                    ControlLabel("POWER")
                    FieldCard(
                        title = "POWER",
                        description = "Current power output",
                        previewFields = powerPreviewStates,
                        colorMode = powerFieldConfig.colorMode,
                        selected = selectedDataField == "POWER",
                        onSelect = { selectedDataField = if (selectedDataField == "POWER") null else "POWER" },
                    ) {
                        ControlLabel("SMOOTHING")
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
                        title = "AVG POWER",
                        description = "Average power with zone coloring.",
                        previewFields = avgPowerPreviewStates,
                        colorMode = avgPowerFieldConfig.colorMode,
                        selected = selectedDataField == "AVG POWER",
                        onSelect = { selectedDataField = if (selectedDataField == "AVG POWER") null else "AVG POWER" },
                    ) {
                        ZoneColorSlider(
                            selected = avgPowerFieldConfig.colorMode,
                            onSelected = { mode ->
                                avgPowerFieldConfig = avgPowerFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveAvgPowerFieldConfig(avgPowerFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "LAP AVG POWER",
                        description = "Average power this lap with zone coloring.",
                        previewFields = lapPowerPreviewStates,
                        colorMode = lapPowerFieldConfig.colorMode,
                        selected = selectedDataField == "LAP AVG POWER",
                        onSelect = { selectedDataField = if (selectedDataField == "LAP AVG POWER") null else "LAP AVG POWER" },
                    ) {
                        ZoneColorSlider(
                            selected = lapPowerFieldConfig.colorMode,
                            onSelected = { mode ->
                                lapPowerFieldConfig = lapPowerFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveLapPowerFieldConfig(isLastLap = false, lapPowerFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "LAST LAP AVG POWER",
                        description = "Average power from the previous lap with zone coloring.",
                        previewFields = lastLapPowerPreviewStates,
                        colorMode = lastLapPowerFieldConfig.colorMode,
                        selected = selectedDataField == "LAST LAP AVG POWER",
                        onSelect = { selectedDataField = if (selectedDataField == "LAST LAP AVG POWER") null else "LAST LAP AVG POWER" },
                    ) {
                        ZoneColorSlider(
                            selected = lastLapPowerFieldConfig.colorMode,
                            onSelected = { mode ->
                                lastLapPowerFieldConfig = lastLapPowerFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveLapPowerFieldConfig(isLastLap = true, lastLapPowerFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "NP",
                        description = "Normalized power with zone coloring.",
                        previewFields = npPreviewStates,
                        colorMode = npFieldConfig.colorMode,
                        selected = selectedDataField == "NP",
                        onSelect = { selectedDataField = if (selectedDataField == "NP") null else "NP" },
                    ) {
                        ZoneColorSlider(
                            selected = npFieldConfig.colorMode,
                            onSelected = { mode ->
                                npFieldConfig = npFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveNPFieldConfig(npFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "POWER ZONE",
                        description = "Current power zone, with zone coloring.",
                        previewFields = powerZonePreviewStates,
                        colorMode = powerZoneFieldConfig.colorMode,
                        selected = selectedDataField == "POWER ZONE",
                        onSelect = { selectedDataField = if (selectedDataField == "POWER ZONE") null else "POWER ZONE" },
                    ) {
                        ZoneColorSlider(
                            selected = powerZoneFieldConfig.colorMode,
                            onSelected = { mode ->
                                powerZoneFieldConfig = powerZoneFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { savePowerZoneFieldConfig(powerZoneFieldConfig) }
                            },
                        )
                        ZoneDisplaySlider(
                            selected = powerZoneFieldConfig.zoneDisplayMode,
                            onSelected = { mode ->
                                powerZoneFieldConfig = powerZoneFieldConfig.copy(zoneDisplayMode = mode)
                                lifecycleScope.launch { savePowerZoneFieldConfig(powerZoneFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "MAX POWER",
                        description = "Maximum power reached this ride, with zone coloring.",
                        previewFields = maxPowerPreviewStates,
                        colorMode = maxPowerFieldConfig.colorMode,
                        selected = selectedDataField == "MAX POWER",
                        onSelect = { selectedDataField = if (selectedDataField == "MAX POWER") null else "MAX POWER" },
                    ) {
                        ZoneColorSlider(
                            selected = maxPowerFieldConfig.colorMode,
                            onSelected = { mode ->
                                maxPowerFieldConfig = maxPowerFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveMaxPowerFieldConfig(maxPowerFieldConfig) }
                            },
                        )
                    }

                    ControlLabel("SPEED", modifier = Modifier.padding(top = 8.dp))
                    FieldCard(
                        title = "SPEED",
                        description = "Current speed",
                        previewFields = speedPreviewStates,
                        colorMode = speedFieldConfig.colorMode,
                        selected = selectedDataField == "SPEED",
                        onSelect = { selectedDataField = if (selectedDataField == "SPEED") null else "SPEED" },
                    ) {
                        ControlLabel("SMOOTHING")
                        SmoothingSlider(
                            options = SpeedSmoothingStream.entries,
                            selected = speedFieldConfig.smoothing,
                            label = { it.label },
                            thumbIcon = R.drawable.ic_col_speed,
                            onSelected = { stream ->
                                speedFieldConfig = speedFieldConfig.copy(smoothing = stream)
                                lifecycleScope.launch { saveSpeedFieldConfig(speedFieldConfig) }
                            },
                        )
                        ZoneColorSlider(
                            selected = speedFieldConfig.colorMode,
                            onSelected = { mode ->
                                speedFieldConfig = speedFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveSpeedFieldConfig(speedFieldConfig) }
                            },
                        )
                        SpeedThresholdControls(
                            config = speedFieldConfig,
                            profile = userProfile,
                            onConfigChange = { cfg ->
                                speedFieldConfig = cfg
                                lifecycleScope.launch { saveSpeedFieldConfig(cfg) }
                            },
                        )
                    }

                    val avgTotalPreviewStates = remember(avgTotalConfig, userProfile) {
                        AvgSpeedField.previewStates(avgTotalConfig, userProfile, includePaused = true)
                    }

                    FieldCard(
                        title = "AVG SPEED (TOTAL)",
                        description = "Average speed including paused time.",
                        previewFields = avgTotalPreviewStates,
                        colorMode = avgTotalConfig.colorMode,
                        selected = selectedDataField == "AVG SPEED (TOTAL)",
                        onSelect = {
                            selectedDataField =
                                if (selectedDataField == "AVG SPEED (TOTAL)") null else "AVG SPEED (TOTAL)"
                        },
                    ) {
                        ZoneColorSlider(
                            selected = avgTotalConfig.colorMode,
                            onSelected = { mode ->
                                avgTotalConfig = avgTotalConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveAvgSpeedConfig(includePaused = true, avgTotalConfig) }
                            },
                        )
                        AvgSpeedThresholdControls(
                            config = avgTotalConfig,
                            profile = userProfile,
                            onConfigChange = { cfg ->
                                avgTotalConfig = cfg
                                lifecycleScope.launch { saveAvgSpeedConfig(includePaused = true, cfg) }
                            },
                        )
                    }

                    val avgMovingPreviewStates = remember(avgMovingConfig, userProfile) {
                        AvgSpeedField.previewStates(avgMovingConfig, userProfile, includePaused = false)
                    }

                    FieldCard(
                        title = "AVG SPEED (MOVING)",
                        description = "Average speed excluding paused time.",
                        previewFields = avgMovingPreviewStates,
                        colorMode = avgMovingConfig.colorMode,
                        selected = selectedDataField == "AVG SPEED (MOVING)",
                        onSelect = {
                            selectedDataField =
                                if (selectedDataField == "AVG SPEED (MOVING)") null else "AVG SPEED (MOVING)"
                        },
                    ) {
                        ZoneColorSlider(
                            selected = avgMovingConfig.colorMode,
                            onSelected = { mode ->
                                avgMovingConfig = avgMovingConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveAvgSpeedConfig(includePaused = false, avgMovingConfig) }
                            },
                        )
                        AvgSpeedThresholdControls(
                            config = avgMovingConfig,
                            profile = userProfile,
                            onConfigChange = { cfg ->
                                avgMovingConfig = cfg
                                lifecycleScope.launch { saveAvgSpeedConfig(includePaused = false, cfg) }
                            },
                        )
                    }

                    ControlLabel("CADENCE", modifier = Modifier.padding(top = 8.dp))
                    FieldCard(
                        title = "CADENCE",
                        description = "Current cadence with threshold coloring.",
                        previewFields = cadencePreviewStates,
                        colorMode = cadenceFieldConfig.colorMode,
                        selected = selectedDataField == "CADENCE",
                        onSelect = {
                            selectedDataField = if (selectedDataField == "CADENCE") null else "CADENCE"
                        },
                    ) {
                        ControlLabel("SMOOTHING")
                        SmoothingSlider(
                            options = CadenceSmoothingStream.entries,
                            selected = cadenceFieldConfig.smoothing,
                            label = { it.label },
                            thumbIcon = R.drawable.ic_cadence,
                            onSelected = { stream ->
                                cadenceFieldConfig = cadenceFieldConfig.copy(smoothing = stream)
                                lifecycleScope.launch { saveCadenceFieldConfig(cadenceFieldConfig) }
                            },
                        )
                        ZoneColorSlider(
                            selected = cadenceFieldConfig.colorMode,
                            onSelected = { mode ->
                                cadenceFieldConfig = cadenceFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveCadenceFieldConfig(cadenceFieldConfig) }
                            },
                        )
                        CadenceThresholdControls(
                            config = cadenceFieldConfig.threshold,
                            onConfigChange = { cfg ->
                                cadenceFieldConfig = cadenceFieldConfig.copy(threshold = cfg)
                                lifecycleScope.launch { saveCadenceFieldConfig(cadenceFieldConfig) }
                            },
                        )
                    }

                    ControlLabel("HEART RATE", modifier = Modifier.padding(top = 8.dp))
                    FieldCard(
                        title = "HEART RATE",
                        description = "Current heart rate",
                        previewFields = hrPreviewStates,
                        colorMode = hrFieldConfig.colorMode,
                        selected = selectedDataField == "HEART RATE",
                        onSelect = { selectedDataField = if (selectedDataField == "HEART RATE") null else "HEART RATE" },
                    ) {
                        ZoneColorSlider(
                            selected = hrFieldConfig.colorMode,
                            onSelected = { mode ->
                                hrFieldConfig = hrFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRFieldConfig(config = hrFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "AVG HR",
                        description = "Average heart rate with zone coloring.",
                        previewFields = avgHrPreviewStates,
                        colorMode = avgHrFieldConfig.colorMode,
                        selected = selectedDataField == "AVG HR",
                        onSelect = { selectedDataField = if (selectedDataField == "AVG HR") null else "AVG HR" },
                    ) {
                        ZoneColorSlider(
                            selected = avgHrFieldConfig.colorMode,
                            onSelected = { mode ->
                                avgHrFieldConfig = avgHrFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRFieldConfig(HRFieldKind.AVG, avgHrFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "LAP AVG HR",
                        description = "Average heart rate this lap with zone coloring.",
                        previewFields = lapAvgHrPreviewStates,
                        colorMode = lapAvgHrFieldConfig.colorMode,
                        selected = selectedDataField == "LAP AVG HR",
                        onSelect = { selectedDataField = if (selectedDataField == "LAP AVG HR") null else "LAP AVG HR" },
                    ) {
                        ZoneColorSlider(
                            selected = lapAvgHrFieldConfig.colorMode,
                            onSelected = { mode ->
                                lapAvgHrFieldConfig = lapAvgHrFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRFieldConfig(HRFieldKind.LAP_AVG, lapAvgHrFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "LAST LAP AVG HR",
                        description = "Average heart rate from the previous lap with zone coloring.",
                        previewFields = lastLapAvgHrPreviewStates,
                        colorMode = lastLapAvgHrFieldConfig.colorMode,
                        selected = selectedDataField == "LAST LAP AVG HR",
                        onSelect = { selectedDataField = if (selectedDataField == "LAST LAP AVG HR") null else "LAST LAP AVG HR" },
                    ) {
                        ZoneColorSlider(
                            selected = lastLapAvgHrFieldConfig.colorMode,
                            onSelected = { mode ->
                                lastLapAvgHrFieldConfig = lastLapAvgHrFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRFieldConfig(HRFieldKind.LAST_LAP_AVG, lastLapAvgHrFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "%MAX HR",
                        description = "Current heart rate as a percentage of max HR, with zone coloring.",
                        previewFields = hrMaxPercentPreviewStates,
                        colorMode = hrMaxPercentFieldConfig.colorMode,
                        selected = selectedDataField == "%MAX HR",
                        onSelect = { selectedDataField = if (selectedDataField == "%MAX HR") null else "%MAX HR" },
                    ) {
                        ZoneColorSlider(
                            selected = hrMaxPercentFieldConfig.colorMode,
                            onSelected = { mode ->
                                hrMaxPercentFieldConfig = hrMaxPercentFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRMaxPercentFieldConfig(hrMaxPercentFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "MAX HR",
                        description = "Maximum heart rate reached this ride, with zone coloring.",
                        previewFields = maxHrPreviewStates,
                        colorMode = maxHrFieldConfig.colorMode,
                        selected = selectedDataField == "MAX HR",
                        onSelect = { selectedDataField = if (selectedDataField == "MAX HR") null else "MAX HR" },
                    ) {
                        ZoneColorSlider(
                            selected = maxHrFieldConfig.colorMode,
                            onSelected = { mode ->
                                maxHrFieldConfig = maxHrFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveMaxHRFieldConfig(maxHrFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "HR ZONE",
                        description = "Current heart rate zone, with zone coloring.",
                        previewFields = hrZonePreviewStates,
                        colorMode = hrZoneFieldConfig.colorMode,
                        selected = selectedDataField == "HR ZONE",
                        onSelect = { selectedDataField = if (selectedDataField == "HR ZONE") null else "HR ZONE" },
                    ) {
                        ZoneColorSlider(
                            selected = hrZoneFieldConfig.colorMode,
                            onSelected = { mode ->
                                hrZoneFieldConfig = hrZoneFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveHRZoneFieldConfig(hrZoneFieldConfig) }
                            },
                        )
                        ZoneDisplaySlider(
                            selected = hrZoneFieldConfig.zoneDisplayMode,
                            onSelected = { mode ->
                                hrZoneFieldConfig = hrZoneFieldConfig.copy(zoneDisplayMode = mode)
                                lifecycleScope.launch { saveHRZoneFieldConfig(hrZoneFieldConfig) }
                            },
                        )
                    }

                    ControlLabel("CLIMBING", modifier = Modifier.padding(top = 8.dp))
                    FieldCard(
                        title = "GRADE",
                        description = "Road gradient with color coding.",
                        previewFields = gradePreviewStates,
                        colorMode = gradeFieldConfig.colorMode,
                        selected = selectedDataField == "GRADE",
                        onSelect = { selectedDataField = if (selectedDataField == "GRADE") null else "GRADE" },
                    ) {
                        ZoneColorSlider(
                            selected = gradeFieldConfig.colorMode,
                            onSelected = { mode ->
                                gradeFieldConfig = gradeFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveGradeFieldConfig(gradeFieldConfig) }
                            },
                        )
                    }

                    ControlLabel("NAVIGATION", modifier = Modifier.padding(top = 8.dp))
                    ControlLabel("ROUTE REMAINING")
                    HelperText("Whole-route elevation profile with your position.")
                    OverviewPreviewBox()

                    FieldCard(
                        title = "REMAINING EFFORT",
                        description = "Distance and climbing left, stacked.",
                        previewFields = effortPreviewStates,
                        colorMode = ZoneColorMode.NONE,
                        selected = selectedDataField == "REMAINING EFFORT",
                        onSelect = {
                            selectedDataField =
                                if (selectedDataField == "REMAINING EFFORT") null else "REMAINING EFFORT"
                        },
                    ) {
                        ControlLabel("STACK ORDER")
                        SegmentedRow(
                            options = listOf(false to "Distance", true to "Climb"),
                            selected = effortFieldConfig.climbFirst,
                            onSelect = {
                                effortFieldConfig = effortFieldConfig.copy(climbFirst = it)
                                lifecycleScope.launch { saveEffortFieldConfig(effortFieldConfig) }
                            },
                        )
                    }

                } // end Fields

                CollapsibleSection(
                    title = "Climbing",
                    description = "Configure the elevation sparkline",
                    icon = R.drawable.ic_grade,
                    expanded = climberExpanded,
                    onToggle = { climberExpanded = !climberExpanded },
                ) {
                    var sparklineExpanded by remember { mutableStateOf(false) }
                    SparklineCard(
                        config = fieldSparklineConfig,
                        zoneConfig = zoneConfig,
                        profile = userProfile,
                        selected = sparklineExpanded,
                        onSelect = { sparklineExpanded = !sparklineExpanded },
                        onUpdate = { updated ->
                            fieldSparklineConfig = updated
                            lifecycleScope.launch { saveFieldSparklineConfig(updated) }
                        },
                    )
                    var climberMapExpanded by remember { mutableStateOf(false) }
                    ClimberMapCard(
                        config = climberMapConfig,
                        sparklineConfig = fieldSparklineConfig,
                        gradePalette = zoneConfig.gradePalette,
                        selected = climberMapExpanded,
                        onSelect = { climberMapExpanded = !climberMapExpanded },
                        onUpdate = { updated ->
                            climberMapConfig = updated
                            lifecycleScope.launch { saveClimberMapConfig(updated) }
                        },
                    )
                } // end Climbing

                CollapsibleSection(
                    title = "ETA",
                    description = "Configure time of arrival estimation",
                    icon = R.drawable.ic_time_to_dest,
                    expanded = etaExpanded,
                    onToggle = { etaExpanded = !etaExpanded },
                ) {
                    ControlLabel("PRIOR SPEED")
                    HelperText(
                        "Initial average speed (${ConvertType.SPEED.unit(userProfile)}) used for ETA until enough ride data is collected. " +
                            "Set to 0 to disable.",
                    )
                    ETAPriorSpeedInput(
                        priorSpeedKph = etaConfig.priorSpeedKph,
                        profile = userProfile,
                        onValueChange = { kph ->
                            etaConfig = ETAConfig(priorSpeedKph = kph)
                            lifecycleScope.launch { saveETAConfig(etaConfig) }
                        },
                    )
                }

                CollapsibleSection(
                    title = "Global",
                    description = "Color palettes and time format shared across all data fields",
                    icon = R.drawable.ic_section_global,
                    expanded = globalExpanded,
                    onToggle = { globalExpanded = !globalExpanded },
                ) {
                    ControlLabel("TIME FIELDS")
                    TimeFormatPills(
                        selected = timeConfig.format,
                        onSelected = { format ->
                            timeConfig = TimeConfig(format)
                            lifecycleScope.launch { saveTimeConfig(timeConfig) }
                        },
                    )
                    TimeFormatPreview(format = timeConfig.format)

                    ControlLabel("ZONE COLORS")
                    EnumDropdown(
                        title = "Power zones",
                        entries = ZonePalette.entries,
                        selected = zoneConfig.powerPalette,
                        label = ::zonePaletteLabel,
                        onSelected = { palette ->
                            zoneConfig = zoneConfig.copy(powerPalette = palette)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    ZonePalettePreview(palette = zoneConfig.powerPalette, isHr = false)

                    EnumDropdown(
                        title = "HR zones",
                        entries = ZonePalette.entries,
                        selected = zoneConfig.hrPalette,
                        label = ::zonePaletteLabel,
                        onSelected = { palette ->
                            zoneConfig = zoneConfig.copy(hrPalette = palette)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    ZonePalettePreview(palette = zoneConfig.hrPalette, isHr = true)

                    EnumDropdown(
                        title = "Grade",
                        entries = GradePalette.entries,
                        selected = zoneConfig.gradePalette,
                        label = { it.label },
                        onSelected = { palette ->
                            zoneConfig = zoneConfig.copy(gradePalette = palette)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    GradePalettePreview(palette = zoneConfig.gradePalette)

                } // end Global
                Spacer(modifier = Modifier.height(72.dp))
            }
            Box(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(bottom = 16.dp)
                        .offset(x = (-8).dp)
                        .size(width = 62.dp, height = 50.dp)
                        .clip(RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
                        .background(BackButtonTint)
                        .clickable { finish() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp),
                    tint = Color.Black,
                )
            }
        } // end Box
    }
}

// Duration (ms) of expand/shrink/chevron animations for collapsible sections and cards.
internal const val SECTION_ANIM_MS = 200

@Composable
private fun ClimberMapCard(
    config: ClimberMapConfig,
    sparklineConfig: SparklineConfig,
    gradePalette: GradePalette,
    selected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (ClimberMapConfig) -> Unit,
) {
    ExpandableCard(
        title = "MAP OVERLAY",
        selected = selected,
        onSelect = onSelect,
        headerExtra = {
            HelperText("Gradient-colour upcoming climbs along the route on the map.")
            ClimbOverlayPreview(
                config = config,
                sparklineConfig = sparklineConfig,
                gradePalette = gradePalette,
            )
        },
    ) {
        ControlLabel("ENABLED")
        SegmentedRow(
            options = listOf(true to "On", false to "Off"),
            selected = config.enabled,
            onSelect = { onUpdate(config.copy(enabled = it)) },
        )

        if (config.enabled) {
            ControlLabel("TUNING")
            SegmentedRow(
                options = listOf(true to "Sync", false to "Independent"),
                selected = config.syncWithSparkline,
                onSelect = { onUpdate(config.copy(syncWithSparkline = it)) },
            )

            if (config.syncWithSparkline) {
                HelperText("Following the sparkline's emphasis and simplification.")
            } else {
                val posMin = gradeFillRange(gradePalette, skipBandsClimb = config.skipBands).posMin
                val emphasisReadout =
                    if (config.skipBands > 0 && posMin != null) {
                        "Grades below ${"%.0f".format(posMin)}% stay uncoloured."
                    } else {
                        null
                    }
                LabeledHelper("EMPHASIS") {
                    HelperText("Filter out gentle grades so meaningful climbs stand out.")
                    if (emphasisReadout != null) HelperText(emphasisReadout)
                }
                SegmentedRow(
                    options = listOf(0 to "Off", 1 to "1", 2 to "2", 3 to "3"),
                    selected = config.skipBands,
                    onSelect = { onUpdate(config.copy(skipBands = it)) },
                )

                LabeledHelper("SIMPLIFICATION") {
                    HelperText("Merges small elevation wiggles into larger same-colour blocks.")
                }
                SegmentedRow(
                    options = ElevationSimplification.entries.map { it to it.label },
                    selected = config.simplification,
                    onSelect = { onUpdate(config.copy(simplification = it)) },
                )
            }

            ControlLabel("CHEVRONS")
            HelperText("Draw direction chevrons inside each coloured segment.")
            SegmentedRow(
                options = listOf(true to "On", false to "Off"),
                selected = config.showChevrons,
                onSelect = { onUpdate(config.copy(showChevrons = it)) },
            )
        }
    }
}

@Composable
internal fun ControlLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
}

@Composable
internal fun SubControlLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextDark)
}

@Composable
internal fun HelperText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 12.sp, lineHeight = 14.sp, color = Grey500)
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 10.sp, lineHeight = 12.sp, color = Grey500)
}

@Composable
internal fun LabeledHelper(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        ControlLabel(label)
        content()
    }
}

@Composable
internal fun <T> SmoothingSlider(
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
                            .background(Grey400)
                )
            }
            // Thumb on top
            Box(
                modifier =
                    Modifier.size(thumbSizeDp)
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((thumbCenterX - thumbSizePx / 2).toInt(), 0) }
                        .clip(CircleShape)
                        .background(Grey400),
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
                    color = TextDark,
                    fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    description: String,
    icon: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Grey200, RoundedCornerShape(6.dp))
                .background(Color.White)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .pointerInput(onToggle) { detectTapGestures(onTap = { onToggle() }) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                HelperText(description)
            }
            val rotation by
                animateFloatAsState(
                    if (expanded) 0f else 90f,
                    label = "chevron",
                    animationSpec = tween(SECTION_ANIM_MS),
                )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(32.dp).rotate(rotation),
            )
        }
        var everExpanded by remember { mutableStateOf(expanded) }
        if (expanded) everExpanded = true
        if (everExpanded) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(SECTION_ANIM_MS)),
                exit = shrinkVertically(animationSpec = tween(SECTION_ANIM_MS)),
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }
}

private val FIELD_PREVIEW_WIDTH = 120.dp
private val FIELD_PREVIEW_HEIGHT = 80.dp

@Composable
private fun FieldPreviewBox(previewFields: List<FieldState>, colorMode: ZoneColorMode) {
    val context = LocalContext.current
    val densityValue = LocalDensity.current.density
    val widthPx = (FIELD_PREVIEW_WIDTH.value * densityValue).toInt()
    val heightPx = (FIELD_PREVIEW_HEIGHT.value * densityValue).toInt()
    val sizeConfig = remember(widthPx) {
        ViewSizeConfig.STANDARD.copy(
            cellWidthPxOverride = widthPx.toFloat(),
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewFields) {
        index = 0
        while (true) {
            delay(PREVIEW_DELAY_MS)
            index = (index + 1) % previewFields.size
        }
    }
    val field = previewFields[index.coerceAtMost(previewFields.size - 1)]
    val bitmap = remember(field, colorMode, widthPx, heightPx) {
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
        modifier = Modifier.width(FIELD_PREVIEW_WIDTH).height(FIELD_PREVIEW_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSystemInDarkTheme()) Color.Black else Color.White),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun OverviewPreviewBox() {
    val densityValue = LocalDensity.current.density
    val isNight = isSystemInDarkTheme()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = (maxWidth.value * densityValue).toInt().coerceAtLeast(1)
        val heightPx = (80.dp.value * densityValue).toInt()
        val bitmap = remember(widthPx, heightPx, isNight) {
            overviewPreviewBitmap(widthPx, heightPx, isNight)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isNight) Color.Black else Color.White),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

// Shared expand/collapse shell for FieldCard and SparklineCard. The header shows
// `title` always; `headerExtra` (description + preview) animates in while selected,
// and `controls` is the Grey200 body revealed below. The everSelected gate lazy-mounts
// the animated regions so the collapse animation can play on first deselect.
@Composable
internal fun ExpandableCard(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    headerExtra: (@Composable () -> Unit)? = null,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Grey200, RoundedCornerShape(6.dp)),
    ) {
        var everSelected by remember { mutableStateOf(selected) }
        if (selected) everSelected = true
        Column(
            modifier = Modifier.fillMaxWidth().background(Grey100)
                .padding(12.dp)
                .pointerInput(onSelect) { detectTapGestures(onTap = { onSelect() }) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlLabel(title)
            if (everSelected && headerExtra != null) {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandVertically(animationSpec = tween(SECTION_ANIM_MS)),
                    exit = shrinkVertically(animationSpec = tween(SECTION_ANIM_MS)),
                ) {
                    headerExtra()
                }
            }
        }
        if (everSelected) {
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(animationSpec = tween(SECTION_ANIM_MS)),
                exit = shrinkVertically(animationSpec = tween(SECTION_ANIM_MS)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Grey200).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = controls,
                )
            }
        }
    }
}

@Composable
private fun FieldCard(
    title: String,
    description: String,
    previewFields: List<FieldState>,
    colorMode: ZoneColorMode,
    selected: Boolean,
    onSelect: () -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    ExpandableCard(
        title = title,
        selected = selected,
        onSelect = onSelect,
        headerExtra = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HelperText(description, modifier = Modifier.weight(1f))
                FieldPreviewBox(previewFields, colorMode)
            }
        },
        controls = controls,
    )
}

@Composable
private fun ThresholdLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Target\n") }
                withStyle(SpanStyle(color = RDYLGN_RED)) { append("red") }
                append(" · target · ")
                withStyle(SpanStyle(color = RDYLGN_GREEN)) { append("green") }
            },
            fontSize = 12.sp,
            color = Grey500,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Min / Max\n") }
                withStyle(SpanStyle(color = RDYLGN_RED)) { append("red") }
                append(" · ")
                withStyle(SpanStyle(color = DANGER_ORANGE)) { append("orange") }
                append(" · min · ")
                withStyle(SpanStyle(color = RDYLGN_GREEN)) { append("green") }
                append(" · max · ")
                withStyle(SpanStyle(color = DANGER_ORANGE)) { append("orange") }
                append(" · ")
                withStyle(SpanStyle(color = RDYLGN_RED)) { append("red") }
            },
            fontSize = 12.sp,
            color = Grey500,
        )
        HelperText("Leave fields empty to disable.")
    }
}

@Composable
internal fun ZoneColorSlider(selected: ZoneColorMode, onSelected: (ZoneColorMode) -> Unit) {
    ControlLabel("ZONE COLOR")
    SegmentedRow(
        options = ZoneColorMode.entries.map { it to it.label },
        selected = selected,
        onSelect = onSelected,
    )
}

@Composable
internal fun ZoneDisplaySlider(selected: ZoneDisplayMode, onSelected: (ZoneDisplayMode) -> Unit) {
    ControlLabel("ZONE DISPLAY")
    SegmentedRow(
        options = ZoneDisplayMode.entries.map { it to it.label },
        selected = selected,
        onSelect = onSelected,
    )
}

@Composable
private fun TimeFormatPills(selected: TimeFormat, onSelected: (TimeFormat) -> Unit) {
    SegmentedRow(
        options = TimeFormat.entries.map { it to it.label },
        selected = selected,
        onSelect = onSelected,
        trackColor = Grey100,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> EnumDropdown(
    title: String,
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(label(entry)) },
                    onClick = { onSelected(entry); expanded = false },
                )
            }
        }
    }
}

private fun zonePaletteLabel(palette: ZonePalette) = when (palette) {
    ZonePalette.KAROO -> "Karoo"
    ZonePalette.WAHOO -> "Wahoo"
    ZonePalette.INTERVALS -> "Intervals.icu"
    ZonePalette.ZWIFT -> "Zwift"
    ZonePalette.HSLUV -> "HSLuv"
}

// Two-row preview demonstrating how the selected palette renders in each
// color mode. Top row (Text): palette color drawn as text on the datafield
// dark bg, using the contrast-tuned variant. Bottom row (Fill): palette
// color as cell fill with auto-picked text on top via bestTextOnBackground.
@Composable
private fun ZonePalettePreview(palette: ZonePalette, isHr: Boolean) {
    val zoneCount = if (isHr) 5 else 7
    val labels = (1..zoneCount).map { "Z$it" }
    val isNightMode = isSystemInDarkTheme()
    val textColors = (1..zoneCount).map { z ->
        if (isHr) hrZoneColor(z, palette, readable = true, isNightMode = isNightMode)
        else powerZoneColor(z, palette, readable = true, isNightMode = isNightMode)
    }
    val fillColors = (1..zoneCount).map { z ->
        if (isHr) hrZoneColor(z, palette, readable = false)
        else powerZoneColor(z, palette, readable = false)
    }
    DualRowPalettePreview(labels = labels, textRowColors = textColors, fillRowColors = fillColors)
}

@Composable
private fun GradePalettePreview(palette: GradePalette) {
    // Lower bounds of each band. Each band runs from thresholds[i] to thresholds[i+1] (or +∞ for the last).
    val thresholds: List<Double> = when (palette) {
        GradePalette.WAHOO -> listOf(0.0, 4.0, 8.0, 12.0, 20.0)
        GradePalette.GARMIN -> listOf(0.0, 3.0, 6.0, 9.0, 12.0)
        GradePalette.KAROO -> listOf(0.0, 2.0, 5.0, 8.0, 11.0, 14.0, 20.0)
        GradePalette.HSLUV -> listOf(0.0, 3.0, 6.0, 9.0, 12.0, 15.0, 18.0)
        GradePalette.ZWIFT -> listOf(0.0, 3.0, 6.0, 9.0)
        GradePalette.TURBO -> listOf(Double.NEGATIVE_INFINITY, -9.0, -6.0, -3.0, 0.0, 3.0, 6.0, 9.0, 12.0, 15.0)
    }
    val labels = thresholds.map {
        if (it == Double.NEGATIVE_INFINITY) "<-9" else formatGradePct(it)
    }
    val isNightMode = isSystemInDarkTheme()
    val textColors = thresholds.map {
        gradeColor(it, palette, readable = true, isNightMode = isNightMode) ?: Color.Transparent
    }
    val fillColors = thresholds.map { gradeColor(it, palette, readable = false) ?: Color.Transparent }
    DualRowPalettePreview(labels = labels, textRowColors = textColors, fillRowColors = fillColors)
    GradeRangeBar(thresholds = thresholds)
    Caption(gradeBandSummary(thresholds))
}

// Thin scale under the dual preview showing min / 0 / max anchor labels.
// Mid "0%" is only shown when the palette spans negative grades (Turbo).
@Composable
private fun GradeRangeBar(thresholds: List<Double>) {
    val numericLowers = thresholds.filterNot { it == Double.NEGATIVE_INFINITY }
    val hasNegativeInf = thresholds.first() == Double.NEGATIVE_INFINITY
    val minVal = numericLowers.first()
    val maxVal = numericLowers.last()
    val minLabel = if (hasNegativeInf) "<${formatGradePct(minVal)}%" else "${formatGradePct(minVal)}%"
    val maxLabel = "≥${formatGradePct(maxVal)}%"
    val zeroIdx = thresholds.indexOf(0.0)
    val showZero = hasNegativeInf && zeroIdx > 0 && zeroIdx < thresholds.size - 1
    Column(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Caption(minLabel)
            if (showZero) {
                Spacer(modifier = Modifier.weight(zeroIdx.toFloat()))
                Caption("0%")
                Spacer(modifier = Modifier.weight((thresholds.size - zeroIdx).toFloat()))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Caption(maxLabel)
        }
    }
}

private fun gradeBandSummary(thresholds: List<Double>): String {
    val nBands = thresholds.size
    val numericLowers = thresholds.filterNot { it == Double.NEGATIVE_INFINITY }
    val diffs = (1 until numericLowers.size).map { numericLowers[it] - numericLowers[it - 1] }
    val uniformStep = diffs.firstOrNull()?.takeIf { first -> diffs.all { kotlin.math.abs(it - first) < 0.01 } }
    val stepDesc = if (uniformStep != null) "${formatGradePct(uniformStep)}% steps" else "uneven steps"
    return "$nBands bands · $stepDesc"
}

@Composable
private fun DualRowPalettePreview(
    labels: List<String>,
    textRowColors: List<Color>,
    fillRowColors: List<Color>,
) {
    val textRowBg = if (isSystemInDarkTheme()) Color.Black else Color.White
    Column(modifier = Modifier.fillMaxWidth()) {
        Caption("Text mode (top) · Fill mode (bottom)")
        Spacer(modifier = Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth().height(28.dp).background(textRowBg)) {
            labels.forEachIndexed { i, label ->
                PreviewSwatch(label = label, bg = textRowBg, text = textRowColors[i])
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            labels.forEachIndexed { i, label ->
                val fill = fillRowColors[i]
                PreviewSwatch(label = label, bg = fill, text = bestTextOnBackground(fill))
            }
        }
    }
}

@Composable
private fun RowScope.PreviewSwatch(label: String, bg: Color, text: Color) {
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight().background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 10.sp, color = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeFormatPreview(format: TimeFormat) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (isSystemInDarkTheme()) Color.Black else Color.White, RoundedCornerShape(6.dp))
                .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatTime(5025L, format),
            style = MaterialTheme.typography.displaySmall.copy(
                color = if (isSystemInDarkTheme()) Color.White else Color.Black,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CommitOnFocusLossTextField(
    text: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
) {
    val focusManager = LocalFocusManager.current
    var wasFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = {
            Text(placeholder, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(); focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            if (wasFocused && !state.isFocused) onCommit()
            wasFocused = state.isFocused
        },
    )
}

@Composable
private fun ETAPriorSpeedInput(
    priorSpeedKph: Double,
    profile: UserProfile,
    onValueChange: (Double) -> Unit,
) {
    val displayValue = ConvertType.SPEED.toDisplay(priorSpeedKph, profile)
    var text by remember(priorSpeedKph) { mutableStateOf(if (priorSpeedKph == 0.0) "" else displayValue.toString()) }
    val speedUnit = ConvertType.SPEED.unit(profile)
    CommitOnFocusLossTextField(
        text = text,
        onTextChange = { text = it },
        onCommit = {
            val entered = text.toDoubleOrNull() ?: 0.0
            onValueChange(ConvertType.SPEED.fromDisplay(entered, profile))
        },
        placeholder = "Speed ($speedUnit)",
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun ThresholdInput(
    value: Double,
    profile: UserProfile,
    onValueChange: (Double) -> Unit,
) {
    val displayValue = ConvertType.SPEED.toDisplay(value, profile)
    var text by remember(value) { mutableStateOf(if (value == 0.0) "" else displayValue.toString()) }
    val speedUnit = ConvertType.SPEED.unit(profile)
    CommitOnFocusLossTextField(
        text = text,
        onTextChange = { text = it },
        onCommit = {
            val entered = text.toDoubleOrNull() ?: 0.0
            onValueChange(ConvertType.SPEED.fromDisplay(entered, profile))
        },
        placeholder = "Target ($speedUnit)",
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun RangeInput(value: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    CommitOnFocusLossTextField(
        text = text,
        onTextChange = { text = it },
        onCommit = { text.toDoubleOrNull()?.let { onValueChange(it) } },
        placeholder = "Range (%)",
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun NullableThresholdInput(
    value: Double?,
    placeholder: String,
    profile: UserProfile,
    onValueChange: (Double?) -> Unit,
) {
    val displayValue = value?.let { ConvertType.SPEED.toDisplay(it, profile) }
    var text by remember(value) { mutableStateOf(displayValue?.toString() ?: "") }
    CommitOnFocusLossTextField(
        text = text,
        onTextChange = { text = it },
        onCommit = {
            onValueChange(text.toDoubleOrNull()?.let { ConvertType.SPEED.fromDisplay(it, profile) })
        },
        placeholder = placeholder,
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
internal fun SpeedThresholdControls(
    config: SpeedFieldConfig,
    profile: UserProfile,
    onConfigChange: (SpeedFieldConfig) -> Unit,
) {
    val sourceOptions =
        listOf(
            SpeedThresholdSource.FIXED to "Fixed",
            SpeedThresholdSource.AVG_TOTAL to "Avg total",
            SpeedThresholdSource.AVG_MOVING to "Avg moving",
        )
    ControlLabel("THRESHOLD SOURCE")
    SegmentedRow(
        options = sourceOptions,
        selected = config.source,
        onSelect = { onConfigChange(config.copy(source = it)) },
    )
    val speedUnit = ConvertType.SPEED.unit(profile).uppercase()
    if (config.source == SpeedThresholdSource.FIXED) {
        ControlLabel("TARGET ($speedUnit)")
        ThresholdInput(
            value = config.thresholdKph,
            profile = profile,
            onValueChange = { onConfigChange(config.copy(thresholdKph = it)) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            ControlLabel("UNDER (%)")
            RangeInput(
                value = config.rangePercentBelow,
                onValueChange = { onConfigChange(config.copy(rangePercentBelow = it)) },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            ControlLabel("OVER (%)")
            RangeInput(
                value = config.rangePercentAbove,
                onValueChange = { onConfigChange(config.copy(rangePercentAbove = it)) },
            )
        }
    }
}

@Composable
internal fun AvgSpeedThresholdControls(
    config: AvgSpeedConfig,
    profile: UserProfile,
    onConfigChange: (AvgSpeedConfig) -> Unit,
) {
    ControlLabel("THRESHOLD")
    ThresholdLegend()
    val modeOptions =
        listOf(ThresholdMode.TARGET to "Target", ThresholdMode.MIN_MAX to "Min / Max")
    SegmentedRow(
        options = modeOptions,
        selected = config.mode,
        onSelect = { onConfigChange(config.copy(mode = it)) },
    )
    val speedUnit = ConvertType.SPEED.unit(profile).uppercase()
    if (config.mode == ThresholdMode.TARGET) {
        ControlLabel("TARGET ($speedUnit)")
        ThresholdInput(
            value = config.thresholdKph,
            profile = profile,
            onValueChange = { onConfigChange(config.copy(thresholdKph = it)) },
        )
    } else {
        ControlLabel("MIN SPEED ($speedUnit)")
        NullableThresholdInput(
            value = config.minKph,
            placeholder = "Min (${ConvertType.SPEED.unit(profile)})",
            profile = profile,
            onValueChange = { onConfigChange(config.copy(minKph = it)) },
        )
        ControlLabel("MAX SPEED ($speedUnit)")
        NullableThresholdInput(
            value = config.maxKph,
            placeholder = "Max (${ConvertType.SPEED.unit(profile)})",
            profile = profile,
            onValueChange = { onConfigChange(config.copy(maxKph = it)) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            ControlLabel("UNDER (%)")
            RangeInput(
                value = config.rangePercentBelow,
                onValueChange = { onConfigChange(config.copy(rangePercentBelow = it)) },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            ControlLabel("OVER (%)")
            RangeInput(
                value = config.rangePercentAbove,
                onValueChange = { onConfigChange(config.copy(rangePercentAbove = it)) },
            )
        }
    }
}

@Composable
internal fun CadenceThresholdControls(
    config: CadenceThresholdConfig,
    onConfigChange: (CadenceThresholdConfig) -> Unit,
) {
    ControlLabel("THRESHOLD")
    ThresholdLegend()
    val modeOptions =
        listOf(ThresholdMode.TARGET to "Target", ThresholdMode.MIN_MAX to "Min / Max")
    SegmentedRow(
        options = modeOptions,
        selected = config.mode,
        onSelect = { onConfigChange(config.copy(mode = it)) },
    )
    if (config.mode == ThresholdMode.TARGET) {
        ControlLabel("TARGET (RPM)")
        CadenceThresholdInput(
            value = config.thresholdRpm,
            onValueChange = { onConfigChange(config.copy(thresholdRpm = it)) },
        )
    } else {
        ControlLabel("MIN CADENCE (RPM)")
        NullableCadenceThresholdInput(
            value = config.minRpm,
            placeholder = "Min (rpm)",
            onValueChange = { onConfigChange(config.copy(minRpm = it)) },
        )
        ControlLabel("MAX CADENCE (RPM)")
        NullableCadenceThresholdInput(
            value = config.maxRpm,
            placeholder = "Max (rpm)",
            onValueChange = { onConfigChange(config.copy(maxRpm = it)) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            ControlLabel("UNDER (%)")
            RangeInput(
                value = config.rangePercentBelow,
                onValueChange = { onConfigChange(config.copy(rangePercentBelow = it)) },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            ControlLabel("OVER (%)")
            RangeInput(
                value = config.rangePercentAbove,
                onValueChange = { onConfigChange(config.copy(rangePercentAbove = it)) },
            )
        }
    }
}

@Composable
private fun CadenceThresholdInput(
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(if (value == 0.0) "" else value.toInt().toString()) }
    CommitOnFocusLossTextField(
        text = text,
        onTextChange = { text = it },
        onCommit = {
            val entered = text.toDoubleOrNull() ?: 0.0
            onValueChange(entered)
        },
        placeholder = "Target (rpm)",
        keyboardType = KeyboardType.Number,
    )
}

@Composable
private fun NullableCadenceThresholdInput(
    value: Double?,
    placeholder: String,
    onValueChange: (Double?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toInt()?.toString() ?: "") }
    CommitOnFocusLossTextField(
        text = text,
        onTextChange = { text = it },
        onCommit = { onValueChange(text.toDoubleOrNull()) },
        placeholder = placeholder,
        keyboardType = KeyboardType.Number,
    )
}

private fun formatGradePct(d: Double) = "%.0f".format(d)
