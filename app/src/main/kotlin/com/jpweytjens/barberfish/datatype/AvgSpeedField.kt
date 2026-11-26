package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.MovingTimeAndDistance
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.minMaxThresholdColor
import com.jpweytjens.barberfish.datatype.shared.targetThresholdColor
import com.jpweytjens.barberfish.extension.AvgSpeedFieldConfig
import com.jpweytjens.barberfish.extension.ThresholdMode
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal fun avgSpeedFieldState(
    rawMs: Double,
    cfg: AvgSpeedFieldConfig,
    profile: UserProfile,
    includePaused: Boolean,
): FieldState {
    val converted = ConvertType.SPEED.apply(rawMs, profile)
    val color =
        when (cfg.threshold.mode) {
            ThresholdMode.TARGET ->
                targetThresholdColor(
                    converted = converted,
                    threshDisplay =
                        ConvertType.SPEED.toDisplay(cfg.threshold.thresholdKph, profile),
                    rangePercentBelow = cfg.threshold.rangePercentBelow,
                    rangePercentAbove = cfg.threshold.rangePercentAbove,
                )
            ThresholdMode.MIN_MAX ->
                minMaxThresholdColor(
                    value = converted,
                    min = cfg.threshold.minKph?.let { ConvertType.SPEED.toDisplay(it, profile) },
                    max = cfg.threshold.maxKph?.let { ConvertType.SPEED.toDisplay(it, profile) },
                    rangePercentBelow = cfg.threshold.rangePercentBelow,
                    rangePercentAbove = cfg.threshold.rangePercentAbove,
                )
        }
    return FieldState(
        primary = "%.1f".format(Locale.ROOT, converted),
        label = if (includePaused) "Avg Speed\nTotal" else "Avg Speed\nMoving",
        color = color,
        iconRes = R.drawable.ic_speed_average,
        colorMode = cfg.colorMode,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class AvgSpeedField(
    private val karooSystem: KarooSystemService,
    private val includePaused: Boolean,
    private val moving: Flow<MovingTimeAndDistance?>,
) : BarberfishDataType("barberfish", if (includePaused) "avg-speed-total" else "avg-speed-moving") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamAvgSpeedConfig(includePaused), karooSystem.streamUserProfile()) {
                cfg,
                profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) ->
                streamFlow(karooSystem, cfg, profile, includePaused, moving)
            }

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
        // Raw average speed in m/s. Total is DISTANCE / RIDE_TIME. Moving is our moving
        // distance / moving time, or DISTANCE / ELAPSED_TIME while the pair is null; the
        // Karoo streams for it exist only while it is null. Returns 0.0 until the time has
        // accumulated at least one second. Shared between AvgSpeedField and SpeedField
        // (which uses it as a dynamic threshold source).
        fun avgSpeedRawMsFlow(
            karooSystem: KarooSystemService,
            includePaused: Boolean,
            moving: Flow<MovingTimeAndDistance?>,
        ): Flow<Double> =
            if (includePaused) {
                karooAvgSpeedRawMsFlow(karooSystem, includePaused = true)
            } else {
                moving.flatMapLatest { pair ->
                    if (pair == null) karooAvgSpeedRawMsFlow(karooSystem, includePaused = false)
                    else flowOf(averageSpeed(pair.distanceM, pair.timeMs))
                }
            }

        private fun karooAvgSpeedRawMsFlow(
            karooSystem: KarooSystemService,
            includePaused: Boolean,
        ): Flow<Double> {
            val timeType =
                if (includePaused) DataType.Type.RIDE_TIME else DataType.Type.ELAPSED_TIME
            val timeField =
                if (includePaused) DataType.Field.RIDE_TIME else DataType.Field.ELAPSED_TIME
            val distanceFlow =
                karooSystem.streamDataFlow(DataType.Type.DISTANCE).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(DataType.Field.DISTANCE) ?: 0.0
                }
            val timeFlow =
                karooSystem.streamDataFlow(timeType).map { state ->
                    (state as? StreamState.Streaming)?.dataPoint?.values?.get(timeField) ?: 0.0
                }
            return combine(distanceFlow, timeFlow) { distanceM: Double, timeMs: Double ->
                averageSpeed(distanceM, timeMs)
            }
        }

        private fun averageSpeed(distanceM: Double, timeMs: Double): Double {
            val seconds = ConvertType.TIME.apply(timeMs)
            return if (seconds > 0) distanceM / seconds else 0.0
        }

        fun streamFlow(
            karooSystem: KarooSystemService,
            cfg: AvgSpeedFieldConfig,
            profile: UserProfile,
            includePaused: Boolean,
            moving: Flow<MovingTimeAndDistance?>,
        ): Flow<FieldState> =
            avgSpeedRawMsFlow(karooSystem, includePaused, moving).map { rawMs ->
                avgSpeedFieldState(rawMs, cfg, profile, includePaused)
            }

        fun previewStates(
            cfg: AvgSpeedFieldConfig,
            profile: UserProfile,
            includePaused: Boolean,
        ): List<FieldState> {
            // Generate values around the configured threshold so color transitions are visible.
            // Offsets are percentages of the threshold; the range params are typically 10%.
            val centerKph =
                when (cfg.threshold.mode) {
                    ThresholdMode.TARGET ->
                        if (cfg.threshold.thresholdKph > 0.0) cfg.threshold.thresholdKph else 25.0
                    ThresholdMode.MIN_MAX -> {
                        val min = cfg.threshold.minKph
                        val max = cfg.threshold.maxKph
                        when {
                            min != null && max != null -> (min + max) / 2.0
                            min != null -> min
                            max != null -> max
                            else -> 25.0
                        }
                    }
                }
            val offsets = listOf(-0.15, -0.08, -0.03, 0.03, 0.08, 0.15)
            // Total includes paused time, so for the same ride it always reads lower
            // than Moving. Bake that into the pair so side-by-side previews stay truthful.
            val pauseFactor = if (includePaused) 0.92 else 1.0
            val rawValues = offsets.map { pct -> centerKph * (1.0 + pct) * pauseFactor / 3.6 }
            return rawValues.map { rawMs -> avgSpeedFieldState(rawMs, cfg, profile, includePaused) }
        }
    }
}
