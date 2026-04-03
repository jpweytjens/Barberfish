package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdMode
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal fun avgSpeedFieldState(
    rawMs: Double,
    cfg: AvgSpeedConfig,
    profile: UserProfile,
    includePaused: Boolean,
): FieldState {
    val converted = ConvertType.SPEED.apply(rawMs, profile)
    val color =
        when (cfg.mode) {
            SpeedThresholdMode.TARGET -> {
                if (cfg.thresholdKph <= 0.0) {
                    FieldColor.Default
                } else {
                    val thresh = ConvertType.SPEED.toDisplay(cfg.thresholdKph, profile)
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
                val min = cfg.minKph?.let { ConvertType.SPEED.toDisplay(it, profile) }
                val max = cfg.maxKph?.let { ConvertType.SPEED.toDisplay(it, profile) }
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
        label = if (includePaused) "Avg Speed\nTotal" else "Avg Speed\nMoving",
        color = color,
        iconRes = R.drawable.ic_speed_average,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class AvgSpeedField(
    private val karooSystem: KarooSystemService,
    private val includePaused: Boolean,
) : BarberfishDataType("barberfish", if (includePaused) "avg-speed-total" else "avg-speed-moving") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamAvgSpeedConfig(includePaused), karooSystem.streamUserProfile()) {
                cfg,
                profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> streamFlow(karooSystem, cfg, profile, includePaused) }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamAvgSpeedConfig(includePaused), karooSystem.streamUserProfile()) {
                cfg,
                profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) ->
                cyclePreview(previewStates(cfg, profile, includePaused))
            }

    companion object {
        fun streamFlow(
            karooSystem: KarooSystemService,
            cfg: AvgSpeedConfig,
            profile: UserProfile,
            includePaused: Boolean,
        ): Flow<FieldState> {
            val timeType = if (includePaused) DataType.Type.RIDE_TIME else DataType.Type.ELAPSED_TIME
            val timeField = if (includePaused) DataType.Field.RIDE_TIME else DataType.Field.ELAPSED_TIME
            val distanceFlow =
                karooSystem.streamDataFlow(DataType.Type.DISTANCE).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(DataType.Field.DISTANCE) ?: 0.0
                }
            val timeFlow =
                karooSystem.streamDataFlow(timeType).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(timeField) ?: 0.0
                }
            return combine(distanceFlow, timeFlow) { distanceM: Double, timeMs: Double ->
                val seconds = ConvertType.TIME.apply(timeMs)
                val rawMs = if (seconds > 0) distanceM / seconds else 0.0
                avgSpeedFieldState(rawMs, cfg, profile, includePaused)
            }
        }
        fun previewStates(
            cfg: AvgSpeedConfig,
            profile: UserProfile,
            includePaused: Boolean,
        ): List<FieldState> {
            // Generate values around the configured threshold so color transitions are visible.
            // Offsets are percentages of the threshold; the range params are typically 10%.
            val centerKph = when (cfg.mode) {
                SpeedThresholdMode.TARGET ->
                    if (cfg.thresholdKph > 0.0) cfg.thresholdKph else 25.0
                SpeedThresholdMode.MIN_MAX -> {
                    val min = cfg.minKph
                    val max = cfg.maxKph
                    when {
                        min != null && max != null -> (min + max) / 2.0
                        min != null -> min
                        max != null -> max
                        else -> 25.0
                    }
                }
            }
            val offsets = listOf(-0.15, -0.08, -0.03, 0.03, 0.08, 0.15)
            val rawValues = offsets.map { pct -> centerKph * (1.0 + pct) / 3.6 }
            return rawValues.map { rawMs -> avgSpeedFieldState(rawMs, cfg, profile, includePaused) }
        }
    }
}
