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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.AvgPowerField
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.CadenceField
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HRField
import com.jpweytjens.barberfish.datatype.NPField
import com.jpweytjens.barberfish.datatype.PowerField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.formatTime
import com.jpweytjens.barberfish.datatype.shared.DANGER_ORANGE
import com.jpweytjens.barberfish.datatype.shared.PreviewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.RDYLGN_GREEN
import com.jpweytjens.barberfish.datatype.shared.RDYLGN_RED
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.hsluvHrColors
import com.jpweytjens.barberfish.datatype.shared.hsluvPowerColors
import com.jpweytjens.barberfish.datatype.shared.intervalsHrColors
import com.jpweytjens.barberfish.datatype.shared.intervalsHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.intervalsPowerColors
import com.jpweytjens.barberfish.datatype.shared.intervalsPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.karooHrColors
import com.jpweytjens.barberfish.datatype.shared.karooHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.karooPowerColors
import com.jpweytjens.barberfish.datatype.shared.karooPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.toColor
import com.jpweytjens.barberfish.datatype.shared.wahooHrColors
import com.jpweytjens.barberfish.datatype.shared.wahooHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColors
import com.jpweytjens.barberfish.datatype.shared.wahooPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.zwiftHrColors
import com.jpweytjens.barberfish.datatype.shared.zwiftHrColorsReadable
import com.jpweytjens.barberfish.datatype.shared.zwiftPowerColors
import com.jpweytjens.barberfish.datatype.shared.zwiftPowerColorsReadable
import com.jpweytjens.barberfish.datatype.shared.gradeColor
import com.jpweytjens.barberfish.extension.AvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.NPFieldConfig
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedThresholdMode
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.saveAvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.saveAvgSpeedConfig
import com.jpweytjens.barberfish.extension.saveCadenceFieldConfig
import com.jpweytjens.barberfish.extension.saveGradeFieldConfig
import com.jpweytjens.barberfish.extension.saveHRFieldConfig
import com.jpweytjens.barberfish.extension.saveHUDConfig
import com.jpweytjens.barberfish.extension.saveNPFieldConfig
import com.jpweytjens.barberfish.extension.savePowerFieldConfig
import com.jpweytjens.barberfish.extension.saveSpeedFieldConfig
import com.jpweytjens.barberfish.extension.saveTimeConfig
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamAvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamCadenceFieldConfig
import com.jpweytjens.barberfish.extension.streamGradeFieldConfig
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamNPFieldConfig
import com.jpweytjens.barberfish.extension.streamPowerFieldConfig
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
        setContent { MaterialTheme { ConfigScreen() } }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    @Composable
    private fun ConfigScreen() {
        var hudConfig by remember { mutableStateOf(HUDConfig()) }
        var powerFieldConfig by remember { mutableStateOf(PowerFieldConfig()) }
        var hrFieldConfig by remember { mutableStateOf(HRFieldConfig()) }
        var speedFieldConfig by remember { mutableStateOf(SpeedFieldConfig()) }
        var cadenceFieldConfig by remember { mutableStateOf(CadenceFieldConfig()) }
        var avgPowerFieldConfig by remember { mutableStateOf(AvgPowerFieldConfig()) }
        var npFieldConfig by remember { mutableStateOf(NPFieldConfig()) }
        var gradeFieldConfig by remember { mutableStateOf(GradeFieldConfig()) }
        var avgTotalConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var avgMovingConfig by remember { mutableStateOf(AvgSpeedConfig()) }
        var timeConfig by remember { mutableStateOf(TimeConfig()) }
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
        var thresholdsExpanded by remember { mutableStateOf(false) }
        var hudExpanded by remember { mutableStateOf(false) }
        var globalExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            launch { streamHUDConfig().collect { hudConfig = it } }
            launch { streamPowerFieldConfig().collect { powerFieldConfig = it } }
            launch { streamHRFieldConfig().collect { hrFieldConfig = it } }
            launch { streamSpeedFieldConfig().collect { speedFieldConfig = it } }
            launch { streamCadenceFieldConfig().collect { cadenceFieldConfig = it } }
            launch { streamAvgPowerFieldConfig().collect { avgPowerFieldConfig = it } }
            launch { streamNPFieldConfig().collect { npFieldConfig = it } }
            launch { streamGradeFieldConfig().collect { gradeFieldConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = true).collect { avgTotalConfig = it } }
            launch { streamAvgSpeedConfig(includePaused = false).collect { avgMovingConfig = it } }
            launch { streamTimeConfig().collect { timeConfig = it } }
            launch { streamZoneConfig().collect { zoneConfig = it } }
            launch { karooSystem.streamUserProfile().collect { userProfile = it } }
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
                    FieldCard(
                        title = "POWER",
                        description = "Power output in W.",
                        previewFields = PowerField.previewStates(powerFieldConfig, userProfile, zoneConfig),
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
                        previewFields = HRField.previewStates(hrFieldConfig, userProfile, zoneConfig),
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
                        previewFields = SpeedField.previewStates(speedFieldConfig, userProfile),
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
                            thumbIcon = R.drawable.ic_col_speed,
                            onSelected = { stream ->
                                speedFieldConfig = speedFieldConfig.copy(smoothing = stream)
                                lifecycleScope.launch { saveSpeedFieldConfig(speedFieldConfig) }
                            },
                        )
                    }

                    FieldCard(
                        title = "CADENCE",
                        description = "Cadence in rpm.",
                        previewFields = CadenceField.previewStates(cadenceFieldConfig),
                        colorMode = ZoneColorMode.NONE,
                    ) {
                        Text(
                            "SMOOTHING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B2D2D),
                        )
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
                    }

                    FieldCard(
                        title = "AVG POWER",
                        description = "Average power in W with zone coloring.",
                        previewFields = AvgPowerField.previewStates(avgPowerFieldConfig, userProfile, zoneConfig),
                        colorMode = avgPowerFieldConfig.colorMode,
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
                        title = "NP",
                        description = "Normalized power in W with zone coloring.",
                        previewFields = NPField.previewStates(npFieldConfig, userProfile, zoneConfig),
                        colorMode = npFieldConfig.colorMode,
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
                        title = "GRADE",
                        description = "Road gradient with palette-based coloring.",
                        previewFields = GradeField.previewStates(gradeFieldConfig, zoneConfig),
                        colorMode = gradeFieldConfig.colorMode,
                    ) {
                        ZoneColorSlider(
                            selected = gradeFieldConfig.colorMode,
                            onSelected = { mode ->
                                gradeFieldConfig = gradeFieldConfig.copy(colorMode = mode)
                                lifecycleScope.launch { saveGradeFieldConfig(gradeFieldConfig) }
                            },
                        )
                    }
                } // end Fields

                CollapsibleSection(
                    title = "Speed thresholds",
                    description =
                        "Color average speed fields by distance from a target speed or zone.",
                    expanded = thresholdsExpanded,
                    onToggle = { thresholdsExpanded = !thresholdsExpanded },
                ) {
                    Text(
                        buildAnnotatedString {
                            append("Single: ")
                            withStyle(SpanStyle(color = RDYLGN_GREEN)) { append("green") }
                            append(" above and ")
                            withStyle(SpanStyle(color = RDYLGN_RED)) { append("red") }
                            append(" below the threshold.\n")
                            append("Min/Max: ")
                            withStyle(SpanStyle(color = RDYLGN_GREEN)) { append("green") }
                            append(" inside the zone, ")
                            withStyle(SpanStyle(color = DANGER_ORANGE)) { append("orange") }
                            append(" near the boundaries, ")
                            withStyle(SpanStyle(color = RDYLGN_RED)) { append("red") }
                            append(" outside.\n")
                            append("Leave fields empty to disable.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FieldCard(
                        title = "AVG SPEED (TOTAL)",
                        description = "Average speed including paused time.",
                        previewFields = AvgSpeedField.previewStates(avgTotalConfig, userProfile, includePaused = true),
                        colorMode = ZoneColorMode.TEXT,
                    ) {
                        AvgSpeedThresholdControls(
                            config = avgTotalConfig,
                            onConfigChange = { cfg ->
                                avgTotalConfig = cfg
                                lifecycleScope.launch {
                                    saveAvgSpeedConfig(includePaused = true, cfg)
                                }
                            },
                        )
                    }

                    FieldCard(
                        title = "AVG SPEED (MOVING)",
                        description = "Average speed excluding paused time.",
                        previewFields = AvgSpeedField.previewStates(avgMovingConfig, userProfile, includePaused = false),
                        colorMode = ZoneColorMode.TEXT,
                    ) {
                        AvgSpeedThresholdControls(
                            config = avgMovingConfig,
                            onConfigChange = { cfg ->
                                avgMovingConfig = cfg
                                lifecycleScope.launch {
                                    saveAvgSpeedConfig(includePaused = false, cfg)
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
                    HUDConfigSection(
                        hudConfig = hudConfig,
                        zoneConfig = zoneConfig,
                        onUpdate = { updated ->
                            hudConfig = updated
                            lifecycleScope.launch { saveHUDConfig(updated) }
                        },
                    )
                } // end HUD

                CollapsibleSection(
                    title = "Global",
                    description = "Zone color palettes and time format shared across all fields.",
                    expanded = globalExpanded,
                    onToggle = { globalExpanded = !globalExpanded },
                ) {
                    Text("Time fields", style = MaterialTheme.typography.titleMedium)
                    TimeFormatPills(
                        selected = timeConfig.format,
                        onSelected = { format ->
                            timeConfig = TimeConfig(format)
                            lifecycleScope.launch { saveTimeConfig(timeConfig) }
                        },
                    )
                    TimeFormatPreview(format = timeConfig.format)

                    Text("Zone colors", style = MaterialTheme.typography.titleMedium)
                    ReadabilityToggle(
                        readable = zoneConfig.readableColors,
                        onSelected = { readable ->
                            val newPower =
                                if (!readable && zoneConfig.powerPalette == ZonePalette.HSLUV)
                                    ZonePalette.KAROO
                                else zoneConfig.powerPalette
                            val newHr =
                                if (!readable && zoneConfig.hrPalette == ZonePalette.HSLUV)
                                    ZonePalette.KAROO
                                else zoneConfig.hrPalette
                            zoneConfig =
                                zoneConfig.copy(
                                    readableColors = readable,
                                    powerPalette = newPower,
                                    hrPalette = newHr,
                                )
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    Text("Power zones", style = MaterialTheme.typography.labelMedium)
                    ZonePalettePills(
                        selected = zoneConfig.powerPalette,
                        readable = zoneConfig.readableColors,
                        onSelected = { palette ->
                            zoneConfig = zoneConfig.copy(powerPalette = palette)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    ZoneColorBar(
                        colors =
                            when (zoneConfig.powerPalette) {
                                ZonePalette.KAROO ->
                                    if (zoneConfig.readableColors) karooPowerColorsReadable
                                    else karooPowerColors
                                ZonePalette.WAHOO ->
                                    if (zoneConfig.readableColors) wahooPowerColorsReadable
                                    else wahooPowerColors
                                ZonePalette.INTERVALS ->
                                    if (zoneConfig.readableColors) intervalsPowerColorsReadable
                                    else intervalsPowerColors
                                ZonePalette.ZWIFT ->
                                    if (zoneConfig.readableColors) zwiftPowerColorsReadable
                                    else zwiftPowerColors
                                ZonePalette.HSLUV -> hsluvPowerColors
                            }
                    )

                    Text("HR zones", style = MaterialTheme.typography.labelMedium)
                    ZonePalettePills(
                        selected = zoneConfig.hrPalette,
                        readable = zoneConfig.readableColors,
                        onSelected = { palette ->
                            zoneConfig = zoneConfig.copy(hrPalette = palette)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    ZoneColorBar(
                        colors =
                            when (zoneConfig.hrPalette) {
                                ZonePalette.KAROO ->
                                    if (zoneConfig.readableColors) karooHrColorsReadable
                                    else karooHrColors
                                ZonePalette.WAHOO ->
                                    if (zoneConfig.readableColors) wahooHrColorsReadable
                                    else wahooHrColors
                                ZonePalette.INTERVALS ->
                                    if (zoneConfig.readableColors) intervalsHrColorsReadable
                                    else intervalsHrColors
                                ZonePalette.ZWIFT ->
                                    if (zoneConfig.readableColors) zwiftHrColorsReadable
                                    else zwiftHrColors
                                ZonePalette.HSLUV -> hsluvHrColors
                            }
                    )

                    Text("Gradient colors", style = MaterialTheme.typography.titleMedium)
                    ReadabilityToggle(
                        readable = zoneConfig.readableColors,
                        onSelected = { readable ->
                            zoneConfig = zoneConfig.copy(readableColors = readable)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    Text("Grade", style = MaterialTheme.typography.labelMedium)
                    GradePalettePills(
                        selected = zoneConfig.gradePalette,
                        onSelected = { palette ->
                            zoneConfig = zoneConfig.copy(gradePalette = palette)
                            lifecycleScope.launch { saveZoneConfig(zoneConfig) }
                        },
                    )
                    GradeBandBar(palette = zoneConfig.gradePalette, readable = zoneConfig.readableColors)
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
    previewFields: List<FieldState>,
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
                        previewFields[index.coerceAtMost(previewFields.size - 1)],
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
internal fun ZoneColorSlider(selected: ZoneColorMode, onSelected: (ZoneColorMode) -> Unit) {
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
internal fun ZoneColorModeDropdown(selected: ZoneColorMode, onSelected: (ZoneColorMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Zone color") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
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

@Composable
private fun TimeFormatPills(selected: TimeFormat, onSelected: (TimeFormat) -> Unit) {
    val options = TimeFormat.entries
    val grey = Color(0xFF9E9E9E)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFEEEEEE))
                .padding(3.dp)
                .pointerInput(options, onSelected) {
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
        options.forEach { format ->
            val isSelected = format == selected
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) grey else Color.Transparent)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = format.label,
                    fontSize = 10.sp,
                    color = Color(0xFF1B2D2D),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ReadabilityToggle(readable: Boolean, onSelected: (Boolean) -> Unit) {
    val options = listOf(false, true)
    val grey = Color(0xFF9E9E9E)
    Text("Color adjustment", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2D2D))
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFEEEEEE))
                .padding(3.dp)
                .pointerInput(readable, onSelected) {
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
        options.forEach { opt ->
            val isSelected = opt == readable
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) grey else Color.Transparent)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (opt) "Readable" else "Original",
                    fontSize = 10.sp,
                    color = Color(0xFF1B2D2D),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ZonePalettePills(
    selected: ZonePalette,
    readable: Boolean,
    onSelected: (ZonePalette) -> Unit,
) {
    val options =
        if (readable) ZonePalette.entries
        else ZonePalette.entries.filter { it != ZonePalette.HSLUV }
    val grey = Color(0xFF9E9E9E)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFEEEEEE))
                .padding(3.dp)
                .pointerInput(options, onSelected) {
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
        options.forEach { palette ->
            val isSelected = palette == selected
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
                        when (palette) {
                            ZonePalette.KAROO -> "Karoo"
                            ZonePalette.WAHOO -> "Wahoo"
                            ZonePalette.INTERVALS -> "Intervals"
                            ZonePalette.ZWIFT -> "Zwift"
                            ZonePalette.HSLUV -> "HSLuv"
                        },
                    fontSize = 10.sp,
                    color = Color(0xFF1B2D2D),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    if (readable) {
        Text(
            "HSLuv is designed for Karoo's dark screen",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun NullableThresholdInput(
    value: Double?,
    placeholder: String,
    onValueChange: (Double?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            onValueChange(input.toDoubleOrNull())
        },
        placeholder = {
            Text(placeholder, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun AvgSpeedThresholdControls(
    config: AvgSpeedConfig,
    onConfigChange: (AvgSpeedConfig) -> Unit,
) {
    val modeOptions =
        listOf(SpeedThresholdMode.SINGLE to "Single", SpeedThresholdMode.MIN_MAX to "Min / Max")
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(3.dp)
                .pointerInput(onConfigChange) {
                    val slotWidthPx = size.width.toFloat() / modeOptions.size
                    fun idxAt(x: Float) =
                        (x / slotWidthPx).toInt().coerceIn(0, modeOptions.size - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onConfigChange(
                            config.copy(mode = modeOptions[idxAt(down.position.x)].first)
                        )
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            onConfigChange(
                                config.copy(mode = modeOptions[idxAt(change.position.x)].first)
                            )
                            event = awaitPointerEvent()
                        }
                    }
                }
    ) {
        modeOptions.forEach { (mode, label) ->
            val isSelected = config.mode == mode
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Color(0xFF9E9E9E) else Color.Transparent)
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
    if (config.mode == SpeedThresholdMode.SINGLE) {
        Text(
            "THRESHOLD (KM/H)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B2D2D),
        )
        ThresholdInput(
            value = config.thresholdKph,
            onValueChange = { onConfigChange(config.copy(thresholdKph = it)) },
        )
    } else {
        Text(
            "MIN SPEED (KM/H)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B2D2D),
        )
        NullableThresholdInput(
            value = config.minKph,
            placeholder = "Min (km/h)",
            onValueChange = { onConfigChange(config.copy(minKph = it)) },
        )
        Text(
            "MAX SPEED (KM/H)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B2D2D),
        )
        NullableThresholdInput(
            value = config.maxKph,
            placeholder = "Max (km/h)",
            onValueChange = { onConfigChange(config.copy(maxKph = it)) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "RANGE BELOW (%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2D2D),
            )
            RangeInput(
                value = config.rangePercentBelow,
                onValueChange = { onConfigChange(config.copy(rangePercentBelow = it)) },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "RANGE ABOVE (%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2D2D),
            )
            RangeInput(
                value = config.rangePercentAbove,
                onValueChange = { onConfigChange(config.copy(rangePercentAbove = it)) },
            )
        }
    }
}


@Composable
private fun GradePalettePills(selected: GradePalette, onSelected: (GradePalette) -> Unit) {
    val options = GradePalette.entries
    val grey = Color(0xFF9E9E9E)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFEEEEEE))
                .padding(3.dp)
                .pointerInput(options, onSelected) {
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
        options.forEach { palette ->
            val isSelected = palette == selected
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) grey else Color.Transparent)
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = palette.label,
                    fontSize = 10.sp,
                    color = Color(0xFF1B2D2D),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun GradeBandBar(palette: GradePalette, readable: Boolean = true) {
    val thresholds = when (palette) {
        GradePalette.WAHOO -> listOf(0.0, 4.0, 8.0, 12.0, 20.0)
        GradePalette.GARMIN -> listOf(0.0, 3.0, 6.0, 9.0, 12.0)
        GradePalette.KAROO -> listOf(0.0, 4.6, 7.6, 12.6, 15.6, 19.6, 23.6)
        GradePalette.HSLUV -> listOf(0.0, 3.0, 6.0, 9.0, 12.0, 15.0, 18.0)
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        thresholds.forEachIndexed { i, lower ->
            val color = gradeColor(lower, palette, readable)
            val label =
                if (i == thresholds.lastIndex) "${formatGradePct(lower)}%+"
                else "${formatGradePct(lower)}-${formatGradePct(thresholds[i + 1])}%"
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(16.dp).background(color ?: Color.Transparent))
                Text(text = label, fontSize = 7.sp, color = Color(0xFF1B2D2D))
            }
        }
    }
}

private fun formatGradePct(d: Double) = "%.0f".format(d)


@Composable
internal fun BarberfishPreviewCell(
    field: FieldState,
    alignment: ViewConfig.Alignment,
    colorMode: ZoneColorMode,
    modifier: Modifier = Modifier,
    sizeConfig: PreviewSizeConfig = PreviewSizeConfig.SINGLE,
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
                .padding(
                    start = sizeConfig.paddingStart,
                    end = sizeConfig.paddingEnd,
                    top = sizeConfig.paddingTop,
                    bottom = sizeConfig.valueBottomPadding,
                )
        else
            modifier.padding(
                start = sizeConfig.paddingStart,
                end = sizeConfig.paddingEnd,
                top = sizeConfig.paddingTop,
                bottom = sizeConfig.valueBottomPadding,
            )

    val textAlign =
        when (alignment) {
            ViewConfig.Alignment.LEFT -> TextAlign.Left
            ViewConfig.Alignment.CENTER -> TextAlign.Center
            ViewConfig.Alignment.RIGHT -> TextAlign.Right
        }

    Column(modifier = cellModifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            field.iconRes?.let { res ->
                Box(
                    modifier = Modifier.size(sizeConfig.headerIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(res),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(sizeConfig.headerIconSize),
                    )
                }
                Spacer(Modifier.width(sizeConfig.headerIconLabelGap))
            }
            Text(
                field.label.uppercase(),
                modifier = Modifier.weight(2f),
                fontSize = sizeConfig.headerFontSize,
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
            fontSize = sizeConfig.valueFontSize,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = textAlign,
            fontFamily = FontFamily.Monospace,
        )
    }
}
