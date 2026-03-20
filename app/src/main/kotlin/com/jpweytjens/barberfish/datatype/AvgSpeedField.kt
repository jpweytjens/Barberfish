package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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
            .flatMapLatest { (cfg, profile) -> streamAvgSpeed(cfg, profile) }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamAvgSpeedConfig(includePaused), karooSystem.streamUserProfile()) {
                cfg,
                profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) ->
                previewSpeedFlow().map { rawMs -> toFieldState(rawMs, cfg, profile) }
            }

    private fun streamAvgSpeed(cfg: AvgSpeedConfig, profile: UserProfile): Flow<FieldState> {
        return if (includePaused) {
            karooSystem.streamDataFlow(DataType.Type.AVERAGE_SPEED).map { state ->
                val rawMs =
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(DataType.Field.AVERAGE_SPEED) ?: 0.0
                toFieldState(rawMs, cfg, profile)
            }
        } else {
            val distanceFlow =
                karooSystem.streamDataFlow(DataType.Type.DISTANCE).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(DataType.Field.DISTANCE) ?: 0.0
                }
            val elapsedFlow =
                karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(DataType.Field.ELAPSED_TIME) ?: 0.0
                }
            val pausedFlow =
                karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME).map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint
                        ?.values
                        ?.get(DataType.Field.PAUSED_TIME) ?: 0.0
                }
            combine(distanceFlow, elapsedFlow, pausedFlow) {
                distanceM: Double,
                elapsed: Double,
                paused: Double ->
                val movingSeconds = elapsed - paused
                val rawMs = if (movingSeconds > 0) distanceM / movingSeconds else 0.0
                toFieldState(rawMs, cfg, profile)
            }
        }
    }

    private fun previewSpeedFlow() =
        flow {
                // values in m/s — toFieldState converts to user unit and applies threshold
                val steps =
                    listOf(4.17, 6.11, 7.22, 7.78, 8.89, 11.11) // 15, 22, 26, 28, 32, 40 km/h
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)

    private fun toFieldState(rawMs: Double, cfg: AvgSpeedConfig, profile: UserProfile): FieldState {
        val converted = ConvertType.SPEED.apply(rawMs, profile)
        val imperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        val color =
            when (cfg.mode) {
                SpeedThresholdMode.SINGLE -> {
                    if (cfg.thresholdKph <= 0.0) {
                        FieldColor.Default
                    } else {
                        val thresh = if (imperial) cfg.thresholdKph * 0.621371 else cfg.thresholdKph
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
            label = if (includePaused) "Avg Speed\nTotal" else "Avg Speed\nMoving",
            color = color,
            iconRes = R.drawable.ic_speed_average,
        )
    }
}
