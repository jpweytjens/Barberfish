package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "speed") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> coloredFlow(cfg, profile) }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) -> cyclePreview(previewStates(cfg, profile)) }

    private fun coloredFlow(cfg: SpeedFieldConfig, profile: UserProfile): Flow<FieldState> {
        val liveFlow = karooSystem.streamDataFlow(cfg.smoothing.typeId)
        return when (cfg.source) {
            SpeedThresholdSource.FIXED -> {
                val threshDisplay = ConvertType.SPEED.toDisplay(cfg.thresholdKph, profile)
                liveFlow.map { state ->
                    toFieldState(
                        state,
                        profile,
                        cfg.smoothing,
                        threshDisplay,
                        cfg.rangePercentBelow,
                        cfg.rangePercentAbove,
                        cfg.colorMode
                    )
                }
            }
            SpeedThresholdSource.AVG_TOTAL ->
                avgColoredFlow(liveFlow, cfg, profile, includePaused = true)
            SpeedThresholdSource.AVG_MOVING ->
                avgColoredFlow(liveFlow, cfg, profile, includePaused = false)
        }
    }

    private fun avgColoredFlow(
        liveFlow: Flow<StreamState>,
        cfg: SpeedFieldConfig,
        profile: UserProfile,
        includePaused: Boolean,
    ): Flow<FieldState> {
        val avgFlow = AvgSpeedField.avgSpeedRawMsFlow(karooSystem, includePaused)
        // Warmup gate: ELAPSED_TIME (moving time) >= 30 s. ELAPSED_TIME excludes paused
        // time, so 30 s cumulative motion means the rider has actually moved — handles both
        // "first move" and "warmup elapsed" with one stateless check. Used regardless of
        // whether the source is total or moving avg.
        val elapsedFlow =
            karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME).map { state ->
                (state as? StreamState.Streaming)
                    ?.dataPoint
                    ?.values
                    ?.get(DataType.Field.ELAPSED_TIME) ?: 0.0
            }
        return combine(liveFlow, avgFlow, elapsedFlow) { state, avgRawMs, elapsedMs ->
            val threshDisplay =
                if (elapsedMs >= WARMUP_MS) ConvertType.SPEED.apply(avgRawMs, profile) else 0.0
            toFieldState(
                state,
                profile,
                cfg.smoothing,
                threshDisplay,
                cfg.rangePercentBelow,
                cfg.rangePercentAbove,
                cfg.colorMode
            )
        }
    }

    companion object {
        private const val WARMUP_MS = 30_000.0

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
                "%.1f".format(converted),
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
                when (cfg.source) {
                    SpeedThresholdSource.FIXED ->
                        if (cfg.thresholdKph > 0.0) cfg.thresholdKph else 25.0
                    SpeedThresholdSource.AVG_TOTAL,
                    SpeedThresholdSource.AVG_MOVING -> 25.0
                }
            val threshDisplay = ConvertType.SPEED.toDisplay(centerKph, profile)
            val offsets = listOf(-0.15, -0.08, -0.03, 0.03, 0.08, 0.15)
            return offsets.map { pct ->
                val rawMs = centerKph * (1.0 + pct) / 3.6
                val converted = ConvertType.SPEED.apply(rawMs, profile)
                val color =
                    if (cfg.source == SpeedThresholdSource.FIXED && cfg.thresholdKph <= 0.0) {
                        FieldColor.Default
                    } else {
                        targetThresholdColor(
                            converted = converted,
                            threshDisplay = threshDisplay,
                            rangePercentBelow = cfg.rangePercentBelow,
                            rangePercentAbove = cfg.rangePercentAbove,
                        )
                    }
                FieldState(
                    "%.1f".format(converted),
                    label = label,
                    color = color,
                    iconRes = R.drawable.ic_col_speed,
                    colorMode = cfg.colorMode,
                )
            }
        }
    }
}
