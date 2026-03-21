package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedThresholdMode
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamGradeFieldConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

private const val EMA_ALPHA = 0.15

@OptIn(ExperimentalCoroutinesApi::class)
class HUDField(private val karooSystem: KarooSystemService) :
    HUDDataType("barberfish", "three-column") {

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
                ) { left, middle, right ->
                    HUDState(
                        left, cfg.leftSlot.colorMode,
                        middle, cfg.middleSlot.colorMode,
                        right, cfg.rightSlot.colorMode,
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
                previewHudFlow().map { (speedKph, hrBpm, powerW) ->
                    HUDState(
                        leftSlot = previewSlotState(cfg.leftSlot, zones, profile, speedKph, hrBpm, powerW),
                        leftColorMode = cfg.leftSlot.colorMode,
                        middleSlot = previewSlotState(cfg.middleSlot, zones, profile, speedKph, hrBpm, powerW),
                        middleColorMode = cfg.middleSlot.colorMode,
                        rightSlot = previewSlotState(cfg.rightSlot, zones, profile, speedKph, hrBpm, powerW),
                        rightColorMode = cfg.rightSlot.colorMode,
                    )
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
            HUDSlotField.Grade -> gradeSlotFlow(context)
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
            ),
            iconRes = R.drawable.ic_col_power,
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
            ),
            iconRes = R.drawable.ic_col_power,
        )
    }

    private fun gradeSlotFlow(context: Context): Flow<FieldState> =
        context.streamGradeFieldConfig().flatMapLatest { gradeConfig ->
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
                        color = FieldColor.Grade(percent, gradeConfig.palette),
                        iconRes = R.drawable.ic_grade,
                    )
                }
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
                val movingSeconds = elapsed - paused
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
                SpeedThresholdMode.SINGLE -> {
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
                        ?.dataPoint?.values?.get(DataType.Field.TIME_OF_ARRIVAL)?.toLong() ?: 0L
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
        (state as? StreamState.Streaming)?.dataPoint?.values?.get(fieldKey)?.toLong() ?: 0L

    // --- Preview helpers ---

    private fun previewSlotState(
        slot: HUDSlotConfig,
        zones: ZoneConfig,
        profile: UserProfile,
        speedKph: Double,
        hrBpm: Int,
        powerW: Int,
    ): FieldState =
        when (slot.field) {
            HUDSlotField.Speed -> {
                val label =
                    if (slot.speedSmoothing == SpeedSmoothingStream.S0) "Speed"
                    else "${slot.speedSmoothing.label} Speed"
                FieldState(
                    "%.1f".format(ConvertType.SPEED.apply(speedKph / 3.6, profile)),
                    label,
                    FieldColor.Default,
                    R.drawable.ic_col_speed,
                )
            }
            HUDSlotField.HR -> {
                val zone = hrZone(hrBpm.toDouble(), profile.heartRateZones)
                FieldState(
                    hrBpm.toString(),
                    "HR",
                    FieldColor.Zone(
                        zone,
                        profile.heartRateZones.size.coerceAtLeast(1),
                        zones.hrPalette,
                        isHr = true,
                    ),
                    R.drawable.ic_col_hr,
                )
            }
            HUDSlotField.Power -> {
                val label =
                    if (slot.powerSmoothing == PowerSmoothingStream.S0) "Power"
                    else "${slot.powerSmoothing.label} Power"
                val zone = powerZone(powerW.toDouble(), profile.powerZones)
                FieldState(
                    powerW.toString(),
                    label,
                    FieldColor.Zone(
                        zone,
                        profile.powerZones.size.coerceAtLeast(1),
                        zones.powerPalette,
                        isHr = false,
                    ),
                    R.drawable.ic_col_power,
                )
            }
            HUDSlotField.Cadence -> {
                val label = if (slot.cadenceSmoothing == CadenceSmoothingStream.S0) "Cadence"
                            else "${slot.cadenceSmoothing.label} Cad"
                FieldState("87", label, FieldColor.Default, R.drawable.ic_cadence)
            }
            HUDSlotField.AvgPower -> {
                val zone = powerZone(220.0, profile.powerZones)
                FieldState(
                    "220", "Avg Power",
                    FieldColor.Zone(zone, profile.powerZones.size.coerceAtLeast(1), zones.powerPalette, isHr = false),
                    R.drawable.ic_col_power,
                )
            }
            HUDSlotField.NP -> {
                val zone = powerZone(247.0, profile.powerZones)
                FieldState(
                    "247", "NP",
                    FieldColor.Zone(zone, profile.powerZones.size.coerceAtLeast(1), zones.powerPalette, isHr = false),
                    R.drawable.ic_col_power,
                )
            }
            HUDSlotField.Grade ->
                FieldState("6.2%", "Grade", FieldColor.Grade(6.2, GradePalette.WAHOO), R.drawable.ic_grade)
            is HUDSlotField.AvgSpeed -> {
                val includePaused = (slot.field as HUDSlotField.AvgSpeed).includePaused
                val label = if (includePaused) "Avg Speed\nTotal" else "Avg Speed\nMoving"
                toAvgSpeedFieldState(speedKph / 3.6, slot.avgSpeedConfig, profile, label)
            }
            is HUDSlotField.Time -> {
                val kind = (slot.field as HUDSlotField.Time).kind
                FieldState("0:23:45", kind.label, FieldColor.Default, kind.iconRes)
            }
        }

    private fun previewHudFlow() =
        flow {
                val steps =
                    listOf(
                        Triple(28.5, 130, 180),
                        Triple(35.2, 152, 240),
                        Triple(42.1, 168, 320),
                        Triple(58.7, 187, 247),
                        Triple(31.0, 145, 200),
                    )
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)
}
