package com.jpweytjens.barberfish.datatype

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.R
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.ICON_TINT_TEAL
import com.jpweytjens.barberfish.datatype.shared.KAROO_DESTINATION_PURPLE
import com.jpweytjens.barberfish.datatype.shared.KAROO_REJOIN_RED
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.previewElevationFixture
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.extension.ETAConfig
import com.jpweytjens.barberfish.extension.AvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.LapPowerFieldConfig
import com.jpweytjens.barberfish.extension.NPFieldConfig
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.SparklineTapReceiver
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamETAConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamNavigationState
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

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
        emitter.setCancellable {
            scope.cancel()
            SparklineTapReceiver.tapSignal.value = 0L to 10
        }
        scope.launch {
            val distFlow: Flow<StreamState> = if (BuildConfig.DEBUG || config.preview)
                flow { while (true) { emit(StreamState.NotAvailable); delay(1000L) } }
            else
                karooSystem.streamDataFlow(DataType.Type.DISTANCE).sample(1000L)
            val hudStateFlow = if (config.preview) previewFlow(context) else liveFlow(context)
            var ratchetRange = 0f
            var lastPositionM = 0f
            var lastOnRoutePositionM = 0f
            val transitionFlow: Flow<Int?> = SparklineTapReceiver.tapSignal
                .flatMapLatest { (ts, km) ->
                    if (ts == 0L) flowOf(null)
                    else flow { emit(km); delay(2000L); emit(null) }
                }
            transitionFlow.flatMapLatest { transitionKm ->
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
                        isOffRoute  -> KAROO_REJOIN_RED.toArgb()
                        dest != null -> KAROO_DESTINATION_PURPLE.toArgb()
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
                    val showSparklineArea = sparkCfg.enabled && (bitmap != null || transitionKm != null)
                    val rv = buildHudRemoteViews(
                        hudState,
                        config,
                        context,
                        sparklineHeightPx = if (showSparklineArea) sparklineHeightPx else 0,
                    )
                    when {
                        transitionKm != null && sparkCfg.enabled -> {
                            rv.setViewVisibility(R.id.hud_sparkline_container, View.VISIBLE)
                            rv.setViewVisibility(R.id.hud_elevation_sparkline, View.GONE)
                            rv.setViewVisibility(R.id.hud_sparkline_transition, View.VISIBLE)
                            rv.setTextViewText(R.id.hud_transition_text, "${transitionKm}km")
                            rv.setInt(R.id.hud_transition_icon, "setColorFilter", Color.WHITE)
                        }
                        bitmap != null -> {
                            rv.setViewVisibility(R.id.hud_sparkline_container, View.VISIBLE)
                            rv.setImageViewBitmap(R.id.hud_elevation_sparkline, bitmap)
                            rv.setViewVisibility(R.id.hud_elevation_sparkline, View.VISIBLE)
                            rv.setViewVisibility(R.id.hud_sparkline_transition, View.GONE)
                        }
                        else -> rv.setViewVisibility(R.id.hud_sparkline_container, View.GONE)
                    }
                    if (!config.preview && sparkCfg.enabled) {
                        val layoutRes = if (hudState.columns == 4)
                            R.layout.barberfish_hud_four else R.layout.barberfish_hud
                        val intent = Intent(context, SparklineTapReceiver::class.java).apply {
                            action = SparklineTapReceiver.ACTION
                            putExtra(SparklineTapReceiver.EXTRA_LOOKAHEAD, sparkCfg.lookaheadKm)
                        }
                        val pi = PendingIntent.getBroadcast(
                            context,
                            layoutRes,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        rv.setOnClickPendingIntent(R.id.hud_sparkline_container, pi)
                    }
                    rv
                }
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
                    .map { SpeedField.toFieldState(it, profile, slot.speedSmoothing) }
            HUDSlotField.HR ->
                karooSystem
                    .streamDataFlow(DataType.Type.HEART_RATE)
                    .map { HRField.toFieldState(it, profile, zones, slot.colorMode) }
            HUDSlotField.Power ->
                karooSystem
                    .streamDataFlow(slot.powerSmoothing.typeId)
                    .map { PowerField.toFieldState(it, slot.powerSmoothing, profile, zones, slot.colorMode) }
            HUDSlotField.Cadence ->
                karooSystem
                    .streamDataFlow(slot.cadenceSmoothing.typeId)
                    .map { CadenceField.toFieldState(it, slot.cadenceSmoothing) }
            HUDSlotField.AvgPower ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_POWER)
                    .map { AvgPowerField.toFieldState(it, profile, zones, slot.colorMode) }
            HUDSlotField.NP ->
                karooSystem
                    .streamDataFlow(DataType.Type.NORMALIZED_POWER)
                    .map { NPField.toFieldState(it, profile, zones, slot.colorMode) }
            HUDSlotField.LapPower ->
                karooSystem
                    .streamDataFlow(DataType.Type.POWER_LAP)
                    .map { LapPowerField.toFieldState(it, profile, zones, slot.colorMode, isLastLap = false) }
            HUDSlotField.LastLapPower ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_POWER_LAST_LAP)
                    .map { LapPowerField.toFieldState(it, profile, zones, slot.colorMode, isLastLap = true) }
            HUDSlotField.AvgHR ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_HR)
                    .map { AvgHRField.toFieldState(it, profile, zones, slot.colorMode, "Avg HR", R.drawable.ic_avg_hr) }
            HUDSlotField.LapAvgHR ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_LAP_HR)
                    .map { AvgHRField.toFieldState(it, profile, zones, slot.colorMode, "Lap Avg HR", R.drawable.ic_lap, R.drawable.ic_avg_hr) }
            HUDSlotField.LastLapAvgHR ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_HR_LAST_LAP)
                    .map { AvgHRField.toFieldState(it, profile, zones, slot.colorMode, "LL Avg HR", R.drawable.ic_last_lap, R.drawable.ic_avg_hr) }
            HUDSlotField.Grade ->
                GradeField.gradeEmaFlow(karooSystem)
                    .map { GradeField.toGradeFieldState(it, GradeFieldConfig(slot.colorMode), zones.gradePalette, zones.readableColors) }
            is HUDSlotField.AvgSpeed ->
                AvgSpeedField.streamFlow(karooSystem, slot.avgSpeedConfig, profile, slot.field.includePaused)
            is HUDSlotField.Time ->
                combine(TimeField.secondsFlow(karooSystem, slot.field.kind), context.streamTimeConfig()) { seconds, cfg ->
                    TimeField.toFieldState(seconds, slot.field.kind, cfg.format)
                }
            is HUDSlotField.ETA ->
                combine(context.streamETAConfig(), context.streamTimeConfig()) { etaCfg, timeCfg ->
                    etaCfg to timeCfg
                }.flatMapLatest { (etaCfg, timeCfg) ->
                    ETAField.streamFlow(karooSystem, slot.field.kind, etaCfg, timeCfg.format)
                }
        }

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
                HUDSlotField.LapPower ->
                    LapPowerField.previewStates(LapPowerFieldConfig(slotCfg.colorMode), profile, zones, isLastLap = false)
                HUDSlotField.LastLapPower ->
                    LapPowerField.previewStates(LapPowerFieldConfig(slotCfg.colorMode), profile, zones, isLastLap = true)
                HUDSlotField.AvgHR ->
                    AvgHRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.LapAvgHR ->
                    LapAvgHRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.LastLapAvgHR ->
                    LastLapAvgHRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.Grade ->
                    GradeField.previewStates(GradeFieldConfig(slotCfg.colorMode), zones)
                is HUDSlotField.Time ->
                    TimeField.previewStates(timeCfg, field.kind)
                is HUDSlotField.ETA ->
                    ETAField.previewStates(field.kind, timeCfg.format)
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
