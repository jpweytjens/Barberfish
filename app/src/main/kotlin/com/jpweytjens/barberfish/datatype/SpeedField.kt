package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.MovingTimeAndDistance
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.targetThresholdColor
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamSpeedFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.toErrorFieldState
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

private const val WARMUP_MS = 30_000.0

// The running average as a speed threshold in display units, or 0.0 (coloring off) during warmup.
// Warmup gate: 30 s of recording time (ELAPSED_TIME), or of our moving time when the
// source is the moving average and the pair exists. Neither counts paused time, so
// 30 s means the rider has actually moved — handles both "first move" and "warmup
// elapsed" with one stateless check.
internal fun averageThreshold(
    averageRawMs: Double,
    warmupTimeMs: Double,
    profile: UserProfile,
): Double = if (warmupTimeMs >= WARMUP_MS) ConvertType.SPEED.apply(averageRawMs, profile) else 0.0

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedField(
    private val karooSystem: KarooSystemService,
    private val moving: Flow<MovingTimeAndDistance?>,
) : BarberfishDataType("barberfish", "speed") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> streamFlow(karooSystem, cfg, profile, moving) }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> cyclePreview(previewStates(cfg, profile)) }

    companion object {
        // The live speed colored against its config's threshold. Shared between SpeedField and
        // the HUD's speed slot.
        fun streamFlow(
            karooSystem: KarooSystemService,
            config: SpeedFieldConfig,
            profile: UserProfile,
            moving: Flow<MovingTimeAndDistance?>,
        ): Flow<FieldState> {
            val liveFlow = karooSystem.streamDataFlow(config.smoothing.typeId)
            val threshold = config.threshold
            fun fieldState(state: StreamState, threshDisplay: Double) =
                toFieldState(
                    state,
                    profile,
                    config.smoothing,
                    threshDisplay,
                    threshold.rangePercentBelow,
                    threshold.rangePercentAbove,
                    config.colorMode,
                )
            return when (threshold.source) {
                SpeedThresholdSource.FIXED -> {
                    val threshDisplay = ConvertType.SPEED.toDisplay(threshold.thresholdKph, profile)
                    liveFlow.map { state -> fieldState(state, threshDisplay) }
                }
                SpeedThresholdSource.AVG_TOTAL,
                SpeedThresholdSource.AVG_MOVING -> {
                    val includePaused = threshold.source == SpeedThresholdSource.AVG_TOTAL
                    val avgFlow =
                        AvgSpeedField.avgSpeedRawMsFlow(karooSystem, includePaused, moving)
                    val elapsedFlow =
                        karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME).map { state ->
                            (state as? StreamState.Streaming)
                                ?.dataPoint
                                ?.values
                                ?.get(DataType.Field.ELAPSED_TIME) ?: 0.0
                        }
                    val warmupTimeFlow =
                        if (includePaused) {
                            elapsedFlow
                        } else {
                            moving.flatMapLatest { pair ->
                                if (pair == null) elapsedFlow else flowOf(pair.timeMs)
                            }
                        }
                    combine(liveFlow, avgFlow, warmupTimeFlow) { state, avgRawMs, warmupTimeMs ->
                        fieldState(state, averageThreshold(avgRawMs, warmupTimeMs, profile))
                    }
                }
            }
        }

        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            smoothing: SpeedSmoothingStream,
            threshDisplay: Double = 0.0,
            rangePercentBelow: Double = 10.0,
            rangePercentAbove: Double = 10.0,
            colorMode: ZoneColorMode = ZoneColorMode.TEXT,
        ): FieldState {
            val label =
                if (smoothing == SpeedSmoothingStream.S0) "Speed" else "${smoothing.label} Speed"
            state.toErrorFieldState(label, R.drawable.ic_col_speed)?.let {
                return it
            }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[smoothing.fieldId]
                    ?: return FieldState.notAvailable(label, R.drawable.ic_col_speed)
            val converted = ConvertType.SPEED.apply(raw, profile)
            val color =
                targetThresholdColor(
                    converted = converted,
                    threshDisplay = threshDisplay,
                    rangePercentBelow = rangePercentBelow,
                    rangePercentAbove = rangePercentAbove,
                )
            return FieldState(
                "%.1f".format(Locale.ROOT, converted),
                label = label,
                color = color,
                iconRes = R.drawable.ic_col_speed,
                colorMode = colorMode,
            )
        }

        fun previewStates(cfg: SpeedFieldConfig, profile: UserProfile): List<FieldState> {
            val label =
                if (cfg.smoothing == SpeedSmoothingStream.S0) "Speed"
                else "${cfg.smoothing.label} Speed"
            // Center the preview around the configured threshold (or a synthetic 25 km/h
            // when source != FIXED) so red→green transitions are visible in the config
            // preview. The warmup gate is intentionally NOT applied here — it would
            // suppress all coloring and defeat the point of the preview.
            val centerKph =
                when (cfg.threshold.source) {
                    SpeedThresholdSource.FIXED ->
                        if (cfg.threshold.thresholdKph > 0.0) cfg.threshold.thresholdKph else 25.0
                    SpeedThresholdSource.AVG_TOTAL,
                    SpeedThresholdSource.AVG_MOVING -> 25.0
                }
            val threshDisplay = ConvertType.SPEED.toDisplay(centerKph, profile)
            val offsets = listOf(-0.15, -0.08, -0.03, 0.03, 0.08, 0.15)
            return offsets.map { pct ->
                val rawMs = centerKph * (1.0 + pct) / 3.6
                val converted = ConvertType.SPEED.apply(rawMs, profile)
                val color =
                    if (
                        cfg.threshold.source == SpeedThresholdSource.FIXED &&
                            cfg.threshold.thresholdKph <= 0.0
                    ) {
                        FieldColor.Default
                    } else {
                        targetThresholdColor(
                            converted = converted,
                            threshDisplay = threshDisplay,
                            rangePercentBelow = cfg.threshold.rangePercentBelow,
                            rangePercentAbove = cfg.threshold.rangePercentAbove,
                        )
                    }
                FieldState(
                    "%.1f".format(Locale.ROOT, converted),
                    label = label,
                    color = color,
                    iconRes = R.drawable.ic_col_speed,
                    colorMode = cfg.colorMode,
                )
            }
        }
    }
}
