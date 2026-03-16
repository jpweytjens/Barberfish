package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.extension.AvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamAvgSpeedConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamUserProfile

@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalCoroutinesApi::class)
class AvgSpeedField(
    private val karooSystem: KarooSystemService,
    private val includePaused: Boolean,
) : DataTypeImpl("barberfish", if (includePaused) "avg-speed-total" else "avg-speed-moving") {

    private val glance = GlanceRemoteViews()

    private val label get() = if (includePaused) "Avg Spd" else "Avg Spd▶"

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val scope = CoroutineScope(Dispatchers.IO + Job())
            emitter.setCancellable { scope.cancel() }
            scope.launch {
                combine(
                    context.streamAvgSpeedConfig(includePaused),
                    karooSystem.streamUserProfile(),
                ) { cfg, profile -> cfg to profile }
                    .flatMapLatest { (cfg, profile) ->
                        previewSpeedFlow().map { rawMs -> toFieldValue(rawMs, cfg, profile) }
                    }
                    .collect { fieldValue ->
                        val composition = glance.compose(context, DpSize.Unspecified) {
                            SingleValueView(fieldValue, config.alignment)
                        }
                        emitter.updateView(composition.remoteViews)
                    }
            }
            return
        }

        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }

        scope.launch {
            combine(
                context.streamAvgSpeedConfig(includePaused),
                karooSystem.streamUserProfile(),
            ) { cfg, profile -> cfg to profile }
                .flatMapLatest { (cfg, profile) ->
                    streamAvgSpeed(cfg, profile)
                }
                .sample(400L)
                .collect { fieldValue ->
                    val composition = glance.compose(context, DpSize.Unspecified) {
                        SingleValueView(fieldValue, config.alignment)
                    }
                    emitter.updateView(composition.remoteViews)
                }
        }
    }

    private fun streamAvgSpeed(cfg: AvgSpeedConfig, profile: UserProfile): Flow<FieldValue> {
        return if (includePaused) {
            karooSystem.streamDataFlow(DataType.Type.AVERAGE_SPEED)
                .map { state ->
                    val rawMs = (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.AVERAGE_SPEED)
                        ?: 0.0
                    toFieldValue(rawMs, cfg, profile)
                }
        } else {
            val distanceFlow = karooSystem.streamDataFlow(DataType.Type.DISTANCE)
                .map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.DISTANCE)
                        ?: 0.0
                }
            val elapsedFlow = karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
                .map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.ELAPSED_TIME)
                        ?: 0.0
                }
            val pausedFlow = karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)
                .map { state ->
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(DataType.Field.PAUSED_TIME)
                        ?: 0.0
                }
            combine(distanceFlow, elapsedFlow, pausedFlow) { distanceM: Double, elapsed: Double, paused: Double ->
                val movingSeconds = elapsed - paused
                val rawMs = if (movingSeconds > 0) distanceM / movingSeconds else 0.0
                toFieldValue(rawMs, cfg, profile)
            }
        }
    }

    private fun previewSpeedFlow() = flow {
        // values in m/s — toFieldValue converts to user unit and applies threshold
        val steps = listOf(4.17, 6.11, 7.22, 7.78, 8.89, 11.11) // 15, 22, 26, 28, 32, 40 km/h
        var i = 0
        while (true) {
            emit(steps[i++ % steps.size])
            delay(Delay.PREVIEW.time)
        }
    }.flowOn(Dispatchers.IO)

    private fun toFieldValue(rawMs: Double, cfg: AvgSpeedConfig, profile: UserProfile): FieldValue {
        val converted = ConvertType.SPEED.apply(rawMs, profile)
        val unit = ConvertType.SPEED.unit(profile)
        val color = cfg.thresholdKph?.let { threshKph ->
            val imperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            val threshInUnit = if (imperial) threshKph * 0.621371 else threshKph
            FieldColor.Threshold(converted >= threshInUnit)
        } ?: FieldColor.Default
        return FieldValue(
            primary = "%.1f".format(converted),
            unit = unit,
            label = label,
            color = color,
        )
    }
}
