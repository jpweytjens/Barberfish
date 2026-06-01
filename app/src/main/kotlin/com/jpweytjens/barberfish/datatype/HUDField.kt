package com.jpweytjens.barberfish.datatype

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.SlotState
import com.jpweytjens.barberfish.datatype.shared.HUD_UPDATE_INTERVAL_MS
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.sparklineBitmapFlow
import com.jpweytjens.barberfish.extension.ETAConfig
import com.jpweytjens.barberfish.extension.AvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.HRMaxPercentFieldConfig
import com.jpweytjens.barberfish.extension.HRZoneFieldConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.LapPowerFieldConfig
import com.jpweytjens.barberfish.extension.MaxHRFieldConfig
import com.jpweytjens.barberfish.extension.MaxPowerFieldConfig
import com.jpweytjens.barberfish.extension.NPFieldConfig
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerZoneFieldConfig
import com.jpweytjens.barberfish.extension.SparklineTapReceiver
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.TimeConfig
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.lapNumberFrom
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamETAConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamHudSparklineConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

// Fixed-height overlay strip inside a HUD slot. Independent of cell size — the HUD
// design budgets 34dp of vertical space for the sparkline regardless of slot height.
private const val HUD_SPARKLINE_HEIGHT_DP = 34f

@OptIn(ExperimentalCoroutinesApi::class)
class HUDField(private val karooSystem: KarooSystemService) :
    HUDDataType("barberfish", "three-column") {

    @OptIn(FlowPreview::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.gridSize.second < 18) {
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
            val hudStateFlow = if (config.preview) previewFlow(context) else liveFlow(context)
            val dm = context.resources.displayMetrics
            val sparklineHeightPx = (HUD_SPARKLINE_HEIGHT_DP * dm.density).toInt()
            val sparklineFlow = sparklineBitmapFlow(
                karooSystem, context,
                configFlow = context.streamHudSparklineConfig(),
                widthPx = dm.widthPixels,
                heightPx = sparklineHeightPx,
                isPreview = config.preview,
            )
            val transitionFlow: Flow<Int?> = SparklineTapReceiver.tapSignal
                .flatMapLatest { (ts, km) ->
                    if (ts == 0L) flowOf(null)
                    else flow { emit(km); delay(2000L); emit(null) }
                }
            transitionFlow.flatMapLatest { transitionKm ->
                combine(
                    hudStateFlow.sample(HUD_UPDATE_INTERVAL_MS),
                    sparklineFlow,
                ) { hudState, frame ->
                    val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    val showSparklineArea = frame.hudEnabled &&
                        (frame.bitmap != null || transitionKm != null || frame.counterText != null)
                    val rv = buildHudRemoteViews(
                        hudState,
                        config,
                        context,
                        sparklineHeightPx = if (showSparklineArea) sparklineHeightPx else 0,
                    )
                    when {
                        transitionKm != null && frame.hudEnabled -> {
                            rv.setViewVisibility(R.id.hud_sparkline_container, View.VISIBLE)
                            rv.setViewVisibility(R.id.hud_elevation_sparkline, View.GONE)
                            rv.setViewVisibility(R.id.hud_sparkline_transition, View.VISIBLE)
                            val displayDist = ConvertType.DISTANCE.toDisplay(transitionKm.toDouble(), hudState.profile).toInt()
                            val distUnit = ConvertType.DISTANCE.unit(hudState.profile)
                            rv.setTextViewText(R.id.hud_transition_text, "$displayDist$distUnit")
                            val transitionColor = if (isNightMode) Color.WHITE else Color.BLACK
                            rv.setTextColor(R.id.hud_transition_text, transitionColor)
                            rv.setInt(R.id.hud_transition_icon, "setColorFilter", transitionColor)
                        }
                        frame.counterText != null -> {
                            rv.setViewVisibility(R.id.hud_sparkline_container, View.VISIBLE)
                            rv.setViewVisibility(R.id.hud_elevation_sparkline, View.GONE)
                            rv.setViewVisibility(R.id.hud_sparkline_transition, View.VISIBLE)
                            rv.setViewVisibility(R.id.hud_transition_icon, View.GONE)
                            rv.setTextViewText(R.id.hud_transition_text, frame.counterText)
                            rv.setTextColor(
                                R.id.hud_transition_text,
                                if (isNightMode) Color.WHITE else Color.BLACK,
                            )
                        }
                        frame.bitmap != null -> {
                            rv.setViewVisibility(R.id.hud_sparkline_container, View.VISIBLE)
                            rv.setImageViewBitmap(R.id.hud_elevation_sparkline, frame.bitmap)
                            rv.setViewVisibility(R.id.hud_elevation_sparkline, View.VISIBLE)
                            rv.setViewVisibility(R.id.hud_sparkline_transition, View.GONE)
                        }
                        else -> rv.setViewVisibility(R.id.hud_sparkline_container, View.GONE)
                    }
                    if (!config.preview && frame.hudEnabled) {
                        val layoutRes = if (hudState.columns == 4)
                            R.layout.barberfish_hud_four else R.layout.barberfish_hud
                        val intent = Intent(context, SparklineTapReceiver::class.java).apply {
                            action = SparklineTapReceiver.ACTION
                            putExtra(SparklineTapReceiver.EXTRA_SURFACE, SparklineTapReceiver.SURFACE_HUD)
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
                        columns = cfg.columns,
                        left = SlotState(left, cfg.leftSlot.colorMode),
                        middle = SlotState(middle, cfg.middleSlot.colorMode),
                        right = SlotState(right, cfg.rightSlot.colorMode),
                        fourth = SlotState(fourth, cfg.fourthSlot.colorMode),
                        profile = profile,
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
                    cyclePreview(previewStates(cfg, timeCfg, profile, zones))
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
                    .map { CadenceField.toFieldState(it, slot.cadenceSmoothing, slot.cadenceThreshold) }
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
                combine(
                    karooSystem.streamDataFlow(DataType.Type.AVERAGE_POWER_LAST_LAP),
                    karooSystem.streamDataFlow(DataType.Type.LAP_NUMBER),
                ) { state, lapState ->
                    LapPowerField.toFieldState(state, profile, zones, slot.colorMode, isLastLap = true, lapNumber = lapNumberFrom(lapState))
                }
            HUDSlotField.PowerZone ->
                karooSystem
                    .streamDataFlow(DataType.Type.POWER_ZONE)
                    .map { PowerZoneField.toFieldState(it, profile, zones, slot.colorMode, slot.zoneDisplayMode) }
            HUDSlotField.MaxPower ->
                karooSystem
                    .streamDataFlow(DataType.Type.MAX_POWER)
                    .map { MaxPowerField.toFieldState(it, profile, zones, slot.colorMode) }
            HUDSlotField.AvgHR ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_HR)
                    .map { AvgHRField.toFieldState(it, profile, zones, slot.colorMode, "Avg HR", R.drawable.ic_avg_hr) }
            HUDSlotField.LapAvgHR ->
                karooSystem
                    .streamDataFlow(DataType.Type.AVERAGE_LAP_HR)
                    .map { AvgHRField.toFieldState(it, profile, zones, slot.colorMode, "Lap Avg HR", R.drawable.ic_lap, R.drawable.ic_avg_hr) }
            HUDSlotField.LastLapAvgHR ->
                combine(
                    karooSystem.streamDataFlow(DataType.Type.AVERAGE_HR_LAST_LAP),
                    karooSystem.streamDataFlow(DataType.Type.LAP_NUMBER),
                ) { state, lapState ->
                    AvgHRField.toFieldState(
                        state, profile, zones, slot.colorMode,
                        "LL Avg HR", R.drawable.ic_last_lap, R.drawable.ic_avg_hr,
                        isLastLap = true, lapNumber = lapNumberFrom(lapState),
                    )
                }
            HUDSlotField.HRMaxPercent ->
                combine(
                    karooSystem.streamDataFlow(DataType.Type.PERCENT_MAX_HR),
                    karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
                ) { percentState, hrState ->
                    HRMaxPercentField.toFieldState(percentState, hrState, profile, zones, slot.colorMode)
                }
            HUDSlotField.MaxHR ->
                karooSystem
                    .streamDataFlow(DataType.Type.MAX_HR)
                    .map { MaxHRField.toFieldState(it, profile, zones, slot.colorMode) }
            HUDSlotField.HRZone ->
                karooSystem
                    .streamDataFlow(DataType.Type.HR_ZONE)
                    .map { HRZoneField.toFieldState(it, profile, zones, slot.colorMode, slot.zoneDisplayMode) }
            HUDSlotField.Grade ->
                GradeField.gradeOlsFlow(karooSystem)
                    .map { GradeField.toGradeFieldState(it, GradeFieldConfig(slot.colorMode), zones.gradePalette) }
            is HUDSlotField.AvgSpeed ->
                AvgSpeedField.streamFlow(karooSystem, slot.avgSpeedConfig, profile, slot.field.includePaused)
            is HUDSlotField.Time ->
                if (slot.field.kind == TimeKind.LAST_LAP) {
                    combine(
                        TimeField.secondsFlow(karooSystem, slot.field.kind),
                        karooSystem.streamDataFlow(DataType.Type.LAP_NUMBER).map { lapNumberFrom(it) },
                        context.streamTimeConfig(),
                    ) { seconds, lapNumber, cfg ->
                        if (lapNumber <= 1) FieldState.noLapsYet(slot.field.kind.label, slot.field.kind.iconRes)
                        else TimeField.toFieldState(seconds, slot.field.kind, cfg.format)
                    }
                } else {
                    combine(TimeField.secondsFlow(karooSystem, slot.field.kind), context.streamTimeConfig()) { seconds, cfg ->
                        TimeField.toFieldState(seconds, slot.field.kind, cfg.format)
                    }
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
                    CadenceField.previewStates(CadenceFieldConfig(slotCfg.cadenceSmoothing, slotCfg.cadenceThreshold))
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
                HUDSlotField.PowerZone ->
                    PowerZoneField.previewStates(PowerZoneFieldConfig(slotCfg.colorMode, slotCfg.zoneDisplayMode), profile, zones)
                HUDSlotField.MaxPower ->
                    MaxPowerField.previewStates(MaxPowerFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.AvgHR ->
                    AvgHRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.LapAvgHR ->
                    LapAvgHRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.LastLapAvgHR ->
                    LastLapAvgHRField.previewStates(HRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.HRMaxPercent ->
                    HRMaxPercentField.previewStates(HRMaxPercentFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.MaxHR ->
                    MaxHRField.previewStates(MaxHRFieldConfig(slotCfg.colorMode), profile, zones)
                HUDSlotField.HRZone ->
                    HRZoneField.previewStates(HRZoneFieldConfig(slotCfg.colorMode, slotCfg.zoneDisplayMode), profile, zones)
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
                    left = SlotState(l[i], hudConfig.leftSlot.colorMode),
                    middle = SlotState(m[i], hudConfig.middleSlot.colorMode),
                    right = SlotState(r[i], hudConfig.rightSlot.colorMode),
                    fourth = SlotState(f[i], hudConfig.fourthSlot.colorMode),
                    profile = profile,
                )
            }
        }
    }
}
