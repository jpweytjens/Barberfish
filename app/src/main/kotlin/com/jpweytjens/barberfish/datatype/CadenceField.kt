package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.CadenceThresholdConfig
import com.jpweytjens.barberfish.extension.ThresholdMode
import com.jpweytjens.barberfish.extension.streamCadenceFieldConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal fun cadenceFieldColor(rpm: Double, cfg: CadenceThresholdConfig): FieldColor =
    when (cfg.mode) {
        ThresholdMode.TARGET -> {
            if (cfg.thresholdRpm <= 0.0) {
                FieldColor.Default
            } else {
                val rangePercent =
                    if (rpm >= cfg.thresholdRpm) cfg.rangePercentAbove
                    else cfg.rangePercentBelow
                val factor =
                    ((rpm - cfg.thresholdRpm) / cfg.thresholdRpm * 100.0 / rangePercent)
                        .coerceIn(-1.0, 1.0)
                        .toFloat()
                FieldColor.Threshold(factor)
            }
        }
        ThresholdMode.MIN_MAX -> {
            val min = cfg.minRpm
            val max = cfg.maxRpm
            if (min == null && max == null) {
                FieldColor.Default
            } else {
                val bandBelow = min?.let { it * cfg.rangePercentBelow / 100.0 } ?: 0.0
                val bandAbove = max?.let { it * cfg.rangePercentAbove / 100.0 } ?: 0.0
                val hasSafeZone = min != null && max != null
                when {
                    min != null && rpm < min -> {
                        val outsideFactor =
                            ((min - rpm) / bandBelow).coerceIn(0.0, 1.0).toFloat()
                        FieldColor.DangerZone(outsideFactor, 1f, hasSafeZone)
                    }
                    max != null && rpm > max -> {
                        val outsideFactor =
                            ((rpm - max) / bandAbove).coerceIn(0.0, 1.0).toFloat()
                        FieldColor.DangerZone(outsideFactor, 1f, hasSafeZone)
                    }
                    else -> {
                        val nearMin =
                            if (min != null && bandBelow > 0.0)
                                (1.0 - (rpm - min) / bandBelow)
                                    .coerceIn(0.0, 1.0)
                                    .toFloat()
                            else 0f
                        val nearMax =
                            if (max != null && bandAbove > 0.0)
                                (1.0 - (max - rpm) / bandAbove)
                                    .coerceIn(0.0, 1.0)
                                    .toFloat()
                            else 0f
                        FieldColor.DangerZone(0f, maxOf(nearMin, nearMax), hasSafeZone)
                    }
                }
            }
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
class CadenceField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "cadence") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        context.streamCadenceFieldConfig().flatMapLatest { cfg ->
            karooSystem.streamDataFlow(cfg.smoothing.typeId).map { state ->
                toFieldState(state, cfg.smoothing, cfg.threshold)
            }
        }

    override fun previewFlow(context: Context): Flow<FieldState> =
        context.streamCadenceFieldConfig().flatMapLatest { cfg ->
            cyclePreview(previewStates(cfg))
        }

    companion object {
        fun cadenceLabel(smoothing: CadenceSmoothingStream) =
            if (smoothing == CadenceSmoothingStream.S0) "Cadence" else "${smoothing.label} Cad"

        fun toFieldState(
            state: StreamState,
            smoothing: CadenceSmoothingStream,
            threshold: CadenceThresholdConfig,
        ): FieldState {
            val label = cadenceLabel(smoothing)
            val raw =
                (state as? StreamState.Streaming)?.dataPoint?.values?.get(smoothing.fieldId)
                    ?: return FieldState.unavailable(label)
            return FieldState(
                raw.toInt().toString(),
                label = label,
                color = cadenceFieldColor(raw, threshold),
                iconRes = R.drawable.ic_cadence,
            )
        }

        private fun isThresholdActive(cfg: CadenceThresholdConfig): Boolean =
            when (cfg.mode) {
                ThresholdMode.TARGET -> cfg.thresholdRpm > 0.0
                ThresholdMode.MIN_MAX -> cfg.minRpm != null || cfg.maxRpm != null
            }

        fun previewStates(cfg: CadenceFieldConfig): List<FieldState> {
            val label = cadenceLabel(cfg.smoothing)
            if (!isThresholdActive(cfg.threshold)) {
                return listOf(82, 87, 91, 78, 95, 45, 120).map { rpm ->
                    FieldState(
                        rpm.toString(),
                        label = label,
                        color = FieldColor.Default,
                        iconRes = R.drawable.ic_cadence,
                    )
                }
            }
            val centerRpm = when (cfg.threshold.mode) {
                ThresholdMode.TARGET -> cfg.threshold.thresholdRpm
                ThresholdMode.MIN_MAX -> {
                    val min = cfg.threshold.minRpm
                    val max = cfg.threshold.maxRpm
                    when {
                        min != null && max != null -> (min + max) / 2.0
                        min != null -> min
                        max != null -> max
                        else -> 90.0
                    }
                }
            }
            val offsets = listOf(-0.15, -0.08, -0.03, 0.03, 0.08, 0.15)
            return offsets.map { pct ->
                val rpm = centerRpm * (1.0 + pct)
                FieldState(
                    rpm.toInt().toString(),
                    label = label,
                    color = cadenceFieldColor(rpm, cfg.threshold),
                    iconRes = R.drawable.ic_cadence,
                )
            }
        }
    }
}
