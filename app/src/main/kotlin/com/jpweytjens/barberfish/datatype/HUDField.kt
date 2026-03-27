package com.jpweytjens.barberfish.datatype

import android.content.Context
import android.view.View
import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.ICON_TINT_TEAL
import com.jpweytjens.barberfish.datatype.shared.KAROO_RED
import com.jpweytjens.barberfish.datatype.shared.KAROO_PURPLE
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.previewElevationFixture
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.extension.AvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.NPFieldConfig
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedThresholdMode
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

private const val EMA_ALPHA = 0.15

@OptIn(ExperimentalCoroutinesApi::class)
class HUDField(private val karooSystem: KarooSystemService) :
    HUDDataType("barberfish", "three-column") {

    @OptIn(FlowPreview::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (!config.preview && config.gridSize.second < 18) {
            super.startView(context, config, emitter)
            return
        }
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }
        scope.launch {
            val distFlow: Flow<StreamState> = if (BuildConfig.DEBUG || config.preview)
                flow { while (true) { emit(StreamState.NotAvailable); delay(1000L) } }
            else
                karooSystem.streamDataFlow(DataType.Type.DISTANCE).sample(1000L)
            val hudStateFlow = if (config.preview) previewFlow(context) else liveFlow(context)
            var ratchetRange = 0f
            var lastPositionM = 0f
            var lastOnRoutePositionM = 0f
            combine(
                hudStateFlow.sample(1000L),
                karooSystem.streamNavigationState().sample(1000L),
                distFlow,
                context.streamZoneConfig(),
                context.streamHUDConfig(),
            ) { hudState, navState, distState, zoneConfig, hudConfig ->
                val dm = context.resources.displayMetrics
                val sparkCfg = hudConfig.sparkline
                val route = navState.state as? OnNavigationState.NavigationState.NavigatingRoute
                val dest  = navState.state as? OnNavigationState.NavigationState.NavigatingToDestination
                val elevPoints: List<Pair<Float, Float>> = when {
                    route != null -> decodeElevationPolyline(route.routeElevationPolyline ?: "")
                    dest  != null -> decodeElevationPolyline(dest.elevationPolyline ?: "")
                    BuildConfig.DEBUG || config.preview -> previewElevationFixture()
                    else -> emptyList()
                }
                val routeLengthM = route?.routeDistance?.toFloat()
                    ?: elevPoints.lastOrNull()?.first ?: 20_000f
                val positionM = if (BuildConfig.DEBUG || config.preview)
                    (System.currentTimeMillis() % 180_000L).toFloat() / 180_000f * routeLengthM
                    else (distState as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.DISTANCE)
                        ?.toFloat() ?: 0f
                val isOffRoute = route != null &&
                    (route.rejoinPolyline != null || route.rejoinDistance != null)
                if (!isOffRoute) lastOnRoutePositionM = positionM
                val sparklinePositionM = if (isOffRoute) lastOnRoutePositionM else positionM
                val dotColor = when {
                    isOffRoute  -> KAROO_RED.toArgb()
                    dest != null -> KAROO_PURPLE.toArgb()
                    else        -> ICON_TINT_TEAL.toArgb()
                }
                val distanceDeltaM = (positionM - lastPositionM).coerceAtLeast(0f)
                lastPositionM = positionM
                val sparklineHeightPx = (44f * dm.density).toInt()
                val (bitmap, updatedRange) = if (sparkCfg.enabled)
                    renderElevationSparkline(
                        elevationPoints = elevPoints,
                        positionM       = sparklinePositionM,
                        widthPx         = dm.widthPixels,
                        heightPx        = sparklineHeightPx,
                        density         = dm.density,
                        palette         = zoneConfig.gradePalette,
                        readable        = zoneConfig.readableColors,
                        lookaheadM      = sparkCfg.lookaheadKm * 1000f,
                        skipBands       = sparkCfg.skipBands,
                        displayedRange  = ratchetRange,
                        distanceDeltaM  = distanceDeltaM,
                        dotColor        = dotColor,
                    )
                else Pair(null, ratchetRange)
                ratchetRange = updatedRange
                val rv = buildHudRemoteViews(
                    hudState,
                    config,
                    context,
                    sparklineHeightPx = if (bitmap != null) sparklineHeightPx else 0,
                )
                if (bitmap != null) {
                    rv.setImageViewBitmap(R.id.hud_elevation_sparkline, bitmap)
                    rv.setViewVisibility(R.id.hud_elevation_sparkline, View.VISIBLE)
                } else {
                    rv.setViewVisibility(R.id.hud_elevation_sparkline, View.GONE)
                }
                rv
            }.collect { rv -> emitter.updateView(rv) }
        }
    }

    override fun liveFlow(context: Context): Flow<HUDState> =
        combine(
                context.streamHUDConfig(),
                context.streamZoneConfig(),
                karooSystem.streamUserProfile(),
            ) { cfg, zones, profile ->
                Triple(cfg, zones, profile)
            }
            .flatMapLatest { (cfg, zones, profile) ->
                combine(
                    slotFlow(cfg.leftSlot, zones, profile, context),
                    slotFlow(cfg.middleSlot, zones, profile, context),
                    slotFlow(cfg.rightSlot, zones, profile, context),
                    slotFlow(cfg.fourthSlot, zones, profile, context),
                ) { left, middle, right, fourth ->
                    HUDState(
                        cfg.columns,
                        left, cfg.leftSlot.colorMode,
                        middle, cfg.middleSlot.colorMode,
                        right, cfg.rightSlot.colorMode,
                        fourth, cfg.fourthSlot.colorMode,
                    )
                }
            }

    override fun previewFlow(context: Context): Flow<HUDState> =
        combine(
                context.streamHUDConfig(),
                context.streamZoneConfig(),
                karooSystem.streamUserProfile(),
            ) { cfg, zones, profile ->
                Triple(cfg, zones, profile)
            }
            .flatMapLatest { (cfg, zones, profile) ->
                context.streamTimeConfig().flatMapLatest { timeCfg ->
                    flow {
                        val states = previewStates(cfg, timeCfg, profile, zones)
                        var i = 0
                        while (true) {
                            emit(states[i++ % states.size])
                            delay(Delay.PREVIEW.time)
                        }
                    }.flowOn(Dispatchers.IO)
                }
            }

    // --- Slot flow factory ---

    private fun slotFlow(
        slot: HUDSlotConfig,
        zones: ZoneConfig,
        profile: UserProfile,
        context: Context,
    ): Flow<FieldState> =
        when (slot.field) {
            HUDSlotField.Speed ->
                karooSystem
                    .streamDataFlow(slot.speedSmoothing.typeId)
                    .map { toSpeed(it, profile, slot.speedSmoothing) }
            HUDSlotField.HR ->
                karooSystem
                    .streamDataFlow(DataType.Type.HEART_RATE)
                    .map { toHr(it, profile, zones) }
            HUDSlotField.Power ->
                karooSystem
                    .streamDataFlow(slot.powerSmoothing.typeId)
                    .map { toPower(it, slot.powerSmoothing, profile, zones) }
            HUDSlotField.Cadence ->
                karooSystem
                    .streamDataFlow(slot.cadenceSmoothing.typeId)
                    .map { toCadence(it, slot.cadenceSmoothing) }
            HUDSlotField.AvgPower ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_POWER)
                    .map { toAvgPower(it, profile, zones) }
            HUDSlotField.NP ->
                karooSystem
                    .streamDataFlow(DataType.Type.NORMALIZED_POWER)
                    .map { toNP(it, profile, zones) }
            HUDSlotField.Grade -> gradeSlotFlow(zones)
            is HUDSlotField.AvgSpeed -> avgSpeedSlotFlow(slot, profile)
            is HUDSlotField.Time -> timeSlotFlow(slot, context)
        }

    // --- Per-type live helpers ---

    private fun toSpeed(
        state: StreamState,
        profile: UserProfile,
        smoothing: SpeedSmoothingStream,
    ): FieldState {
        val label =
            if (smoothing == SpeedSmoothingStream.S0) "Speed"
            else "${smoothing.label} Speed"
        state.toErrorFieldState(label)?.let { return it }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[smoothing.fieldId]
                ?: return FieldState.unavailable(label)
        return FieldState(
            primary = "%.1f".format(ConvertType.SPEED.apply(raw, profile)),
            label = label,
            color = FieldColor.Default,
            iconRes = R.drawable.ic_col_speed,
        )
    }

    private fun toHr(state: StreamState, profile: UserProfile, zones: ZoneConfig): FieldState {
        state.toErrorFieldState("HR")?.let { return it }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[DataType.Field.HEART_RATE]
                ?: return FieldState.unavailable("HR")
        return FieldState(
            primary = raw.toInt().toString(),
            label = "HR",
            color =
                FieldColor.Zone(
                    hrZone(raw, profile.heartRateZones),
                    profile.heartRateZones.size.coerceAtLeast(1),
                    zones.hrPalette,
                    isHr = true,
                    readable = zones.readableColors,
                ),
            iconRes = R.drawable.ic_col_hr,
        )
    }

    private fun toPower(
        state: StreamState,
        stream: PowerSmoothingStream,
        profile: UserProfile,
        zones: ZoneConfig,
    ): FieldState {
        val label = if (stream == PowerSmoothingStream.S0) "Power" else "${stream.label} Power"
        state.toErrorFieldState(label)?.let { return it }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[stream.fieldId]
                ?: return FieldState.unavailable(label)
        return FieldState(
            primary = raw.toInt().toString(),
            label = label,
            color =
                FieldColor.Zone(
                    powerZone(raw, profile.powerZones),
                    profile.powerZones.size.coerceAtLeast(1),
                    zones.powerPalette,
                    isHr = false,
                    readable = zones.readableColors,
                ),
            iconRes = R.drawable.ic_col_power,
        )
    }

    private fun toCadence(state: StreamState, smoothing: CadenceSmoothingStream): FieldState {
        val label = if (smoothing == CadenceSmoothingStream.S0) "Cadence" else "${smoothing.label} Cad"
        val raw =
            (state as? StreamState.Streaming)?.dataPoint?.values?.get(smoothing.fieldId)
                ?: return FieldState.unavailable(label)
        return FieldState(
            primary = raw.toInt().toString(),
            label = label,
            color = FieldColor.Default,
            iconRes = R.drawable.ic_cadence,
        )
    }

    private fun toAvgPower(state: StreamState, profile: UserProfile, zones: ZoneConfig): FieldState {
        state.toErrorFieldState("Avg Power")?.let { return it }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[DataType.Field.AVERAGE_POWER]
                ?: return FieldState.unavailable("Avg Power")
        return FieldState(
            primary = raw.toInt().toString(),
            label = "Avg Power",
            color = FieldColor.Zone(
                powerZone(raw, profile.powerZones),
                profile.powerZones.size.coerceAtLeast(1),
                zones.powerPalette,
                isHr = false,
                readable = zones.readableColors,
            ),
            iconRes = R.drawable.ic_avg_power,
        )
    }

    private fun toNP(state: StreamState, profile: UserProfile, zones: ZoneConfig): FieldState {
        state.toErrorFieldState("NP")?.let { return it }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[DataType.Field.NORMALIZED_POWER]
                ?: return FieldState.unavailable("NP")
        return FieldState(
            primary = raw.toInt().toString(),
            label = "NP",
            color = FieldColor.Zone(
                powerZone(raw, profile.powerZones),
                profile.powerZones.size.coerceAtLeast(1),
                zones.powerPalette,
                isHr = false,
                readable = zones.readableColors,
            ),
            iconRes = R.drawable.ic_col_power,
        )
    }

    private fun gradeSlotFlow(zones: ZoneConfig): Flow<FieldState> =
        karooSystem
            .streamDataFlow(DataType.Type.ELEVATION_GRADE)
            .map { state ->
                (state as? StreamState.Streaming)
                    ?.dataPoint?.values?.get(DataType.Field.ELEVATION_GRADE)
            }
            .filterNotNull()
            .scan(0.0) { ema, raw -> EMA_ALPHA * raw + (1 - EMA_ALPHA) * ema }
            .map { percent ->
                FieldState(
                    primary = "%.1f%%".format(percent),
                    label = "Grade",
                    color = FieldColor.Grade(percent, zones.gradePalette, zones.readableColors),
                    iconRes = R.drawable.ic_grade,
                )
            }

    private fun avgSpeedSlotFlow(slot: HUDSlotConfig, profile: UserProfile): Flow<FieldState> {
        val includePaused = (slot.field as HUDSlotField.AvgSpeed).includePaused
        val label = if (includePaused) "Avg Speed\nTotal" else "Avg Speed\nMoving"
        return if (includePaused) {
            karooSystem.streamDataFlow(DataType.Type.AVERAGE_SPEED).map { state ->
                val rawMs =
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.AVERAGE_SPEED) ?: 0.0
                toAvgSpeedFieldState(rawMs, slot.avgSpeedConfig, profile, label)
            }
        } else {
            val distanceFlow =
                karooSystem.streamDataFlow(DataType.Type.DISTANCE).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.DISTANCE) ?: 0.0
                }
            val elapsedFlow =
                karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.ELAPSED_TIME) ?: 0.0
                }
            val pausedFlow =
                karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.PAUSED_TIME) ?: 0.0
                }
            combine(distanceFlow, elapsedFlow, pausedFlow) { distanceM, elapsed, paused ->
                val movingSeconds = ConvertType.TIME.apply(elapsed) - ConvertType.TIME.apply(paused)
                val rawMs = if (movingSeconds > 0) distanceM / movingSeconds else 0.0
                toAvgSpeedFieldState(rawMs, slot.avgSpeedConfig, profile, label)
            }
        }
    }

    private fun toAvgSpeedFieldState(
        rawMs: Double,
        cfg: AvgSpeedConfig,
        profile: UserProfile,
        label: String,
    ): FieldState {
        val converted = ConvertType.SPEED.apply(rawMs, profile)
        val imperial =
            profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        val color =
            when (cfg.mode) {
                SpeedThresholdMode.TARGET -> {
                    if (cfg.thresholdKph <= 0.0) {
                        FieldColor.Default
                    } else {
                        val thresh =
                            if (imperial) cfg.thresholdKph * 0.621371 else cfg.thresholdKph
                        val rangePercent =
                            if (converted >= thresh) cfg.rangePercentAbove
                            else cfg.rangePercentBelow
                        val factor =
                            ((converted - thresh) / thresh * 100.0 / rangePercent)
                                .coerceIn(-1.0, 1.0)
                                .toFloat()
                        FieldColor.Threshold(factor)
                    }
                }
                SpeedThresholdMode.MIN_MAX -> {
                    val min = cfg.minKph?.let { if (imperial) it * 0.621371 else it }
                    val max = cfg.maxKph?.let { if (imperial) it * 0.621371 else it }
                    if (min == null && max == null) {
                        FieldColor.Default
                    } else {
                        val bandBelow = min?.let { it * cfg.rangePercentBelow / 100.0 } ?: 0.0
                        val bandAbove = max?.let { it * cfg.rangePercentAbove / 100.0 } ?: 0.0
                        val hasSafeZone = min != null && max != null
                        when {
                            min != null && converted < min -> {
                                val outsideFactor =
                                    ((min - converted) / bandBelow).coerceIn(0.0, 1.0).toFloat()
                                FieldColor.DangerZone(outsideFactor, 1f, hasSafeZone)
                            }
                            max != null && converted > max -> {
                                val outsideFactor =
                                    ((converted - max) / bandAbove).coerceIn(0.0, 1.0).toFloat()
                                FieldColor.DangerZone(outsideFactor, 1f, hasSafeZone)
                            }
                            else -> {
                                val nearMin =
                                    if (min != null && bandBelow > 0.0)
                                        (1.0 - (converted - min) / bandBelow)
                                            .coerceIn(0.0, 1.0)
                                            .toFloat()
                                    else 0f
                                val nearMax =
                                    if (max != null && bandAbove > 0.0)
                                        (1.0 - (max - converted) / bandAbove)
                                            .coerceIn(0.0, 1.0)
                                            .toFloat()
                                    else 0f
                                FieldColor.DangerZone(0f, maxOf(nearMin, nearMax), hasSafeZone)
                            }
                        }
                    }
                }
            }
        return FieldState(
            primary = "%.1f".format(converted),
            label = label,
            color = color,
            iconRes = R.drawable.ic_speed_average,
        )
    }

    private fun timeSlotFlow(slot: HUDSlotConfig, context: Context): Flow<FieldState> {
        val kind = (slot.field as HUDSlotField.Time).kind
        if (kind == TimeKind.TIME_OF_ARRIVAL) {
            return karooSystem.streamDataFlow(DataType.Type.TIME_OF_ARRIVAL).map { state ->
                val seconds =
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.TIME_OF_ARRIVAL)
                        ?.let { ConvertType.TIME.apply(it).toLong() } ?: 0L
                timeFieldState(formatClockTime(seconds), kind)
            }
        }
        val secondsFlow =
            when (kind) {
                TimeKind.TOTAL ->
                    karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
                        .map { extractSeconds(it, DataType.Field.ELAPSED_TIME) }
                TimeKind.PAUSED ->
                    karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)
                        .map { extractSeconds(it, DataType.Field.PAUSED_TIME) }
                TimeKind.RIDING ->
                    combine(
                        karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
                            .map { extractSeconds(it, DataType.Field.ELAPSED_TIME) },
                        karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)
                            .map { extractSeconds(it, DataType.Field.PAUSED_TIME) },
                    ) { elapsed, paused -> max(0L, elapsed - paused) }
                TimeKind.TIME_TO_DESTINATION ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_DESTINATION)
                        .map { extractSeconds(it, DataType.Field.TIME_TO_DESTINATION) }
                TimeKind.TIME_OF_ARRIVAL -> error("handled above")
                TimeKind.TIME_TO_SUNRISE ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_SUNRISE)
                        .map { extractSeconds(it, DataType.Field.TIME_TO_SUNRISE) }
                TimeKind.TIME_TO_SUNSET ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_SUNSET)
                        .map { extractSeconds(it, DataType.Field.TIME_TO_SUNSET) }
                TimeKind.TIME_TO_CIVIL_DAWN ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_CIVIL_DAWN)
                        .map { extractSeconds(it, DataType.Field.TIME_TO_CIVIL_DAWN) }
                TimeKind.TIME_TO_CIVIL_DUSK ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_CIVIL_DUSK)
                        .map { extractSeconds(it, DataType.Field.TIME_TO_CIVIL_DUSK) }
            }
        return combine(secondsFlow, context.streamTimeConfig()) { seconds, cfg ->
            timeFieldState(formatTime(seconds, cfg.format), kind)
        }
    }

    private fun timeFieldState(primary: String, kind: TimeKind) =
        FieldState(
            primary = primary,
            label = kind.label,
            color = FieldColor.Default,
            iconRes = kind.iconRes,
        )

    private fun extractSeconds(state: StreamState, fieldKey: String): Long =
        (state as? StreamState.Streaming)?.dataPoint?.values?.get(fieldKey)
            ?.let { ConvertType.TIME.apply(it).toLong() } ?: 0L

    companion object {
        fun previewStates(
            hudConfig: HUDConfig,
            timeCfg: TimeConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<HUDState> {
            fun slot(slotCfg: HUDSlotConfig): List<FieldState> = when (val field = slotCfg.field) {
                HUDSlotField.Power ->
                    PowerField.previewStates(
                        PowerFieldConfig(slotCfg.powerSmoothing, slotCfg.colorMode), profile, zones
                    )
                HUDSlotField.HR ->
                    HRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.Speed ->
                    SpeedField.previewStates(SpeedFieldConfig(slotCfg.speedSmoothing), profile)
                HUDSlotField.Cadence ->
                    CadenceField.previewStates(CadenceFieldConfig(slotCfg.cadenceSmoothing))
                is HUDSlotField.AvgSpeed ->
                    AvgSpeedField.previewStates(slotCfg.avgSpeedConfig, profile, field.includePaused)
                HUDSlotField.AvgPower ->
                    AvgPowerField.previewStates(AvgPowerFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.NP ->
                    NPField.previewStates(NPFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.Grade ->
                    GradeField.previewStates(GradeFieldConfig(slotCfg.colorMode), zones)
                is HUDSlotField.Time ->
                    TimeField.previewStates(timeCfg, field.kind)
            }
            val l = slot(hudConfig.leftSlot)
            val m = slot(hudConfig.middleSlot)
            val r = slot(hudConfig.rightSlot)
            val f = slot(hudConfig.fourthSlot)
            val n = if (hudConfig.columns == 4) minOf(l.size, m.size, r.size, f.size)
                    else minOf(l.size, m.size, r.size)
            return (0 until n).map { i ->
                HUDState(
                    columns = hudConfig.columns,
                    leftSlot = l[i],   leftColorMode = hudConfig.leftSlot.colorMode,
                    middleSlot = m[i], middleColorMode = hudConfig.middleSlot.colorMode,
                    rightSlot = r[i],  rightColorMode = hudConfig.rightSlot.colorMode,
                    fourthSlot = f[i], fourthColorMode = hudConfig.fourthSlot.colorMode,
                )
            }
        }
    }
}
