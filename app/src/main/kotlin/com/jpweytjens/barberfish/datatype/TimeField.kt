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
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamTimeConfig
import kotlin.math.max

fun formatTime(seconds: Long, format: TimeFormat): String {
    val t = maxOf(0L, seconds)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return when (format) {
        TimeFormat.COMPACT -> if (h > 0) "${h}h${m}'%02d\"".format(s) else "${m}'%02d\"".format(s)
        TimeFormat.CLOCK   -> "%d:%02d:%02d".format(h, m, s)
    }
}

enum class TimeKind(val label: String) {
    ELAPSED("Time"),
    MOVING("Moving"),
    PAUSED("Paused"),
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimeField(
    private val karooSystem: KarooSystemService,
    private val kind: TimeKind,
) : DataTypeImpl("barberfish", "time-${kind.name.lowercase()}") {

    private val glance = GlanceRemoteViews()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val scope = CoroutineScope(Dispatchers.IO + Job())
            emitter.setCancellable { scope.cancel() }
            scope.launch {
                context.streamTimeConfig()
                    .flatMapLatest { cfg ->
                        previewTimeFlow().map { seconds ->
                            FieldValue(
                                primary = formatTime(seconds, cfg.format),
                                unit = "",
                                label = kind.label,
                                color = FieldColor.Default,
                            )
                        }
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
            val secondsFlow = when (kind) {
                TimeKind.ELAPSED -> karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
                    .map { state -> extractSeconds(state, DataType.Field.ELAPSED_TIME) }
                TimeKind.PAUSED  -> karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)
                    .map { state -> extractSeconds(state, DataType.Field.PAUSED_TIME) }
                TimeKind.MOVING  -> combine(
                    karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
                        .map { state -> extractSeconds(state, DataType.Field.ELAPSED_TIME) },
                    karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)
                        .map { state -> extractSeconds(state, DataType.Field.PAUSED_TIME) },
                ) { elapsed, paused -> max(0L, elapsed - paused) }
            }

            combine(secondsFlow, context.streamTimeConfig()) { seconds, cfg ->
                FieldValue(
                    primary = formatTime(seconds, cfg.format),
                    unit = "",
                    label = kind.label,
                    color = FieldColor.Default,
                )
            }
                .sample(1000L)
                .collect { fieldValue ->
                    val composition = glance.compose(context, DpSize.Unspecified) {
                        SingleValueView(fieldValue, config.alignment)
                    }
                    emitter.updateView(composition.remoteViews)
                }
        }
    }

    private fun extractSeconds(state: StreamState, fieldKey: String): Long =
        (state as? StreamState.Streaming)
            ?.dataPoint?.values?.get(fieldKey)?.toLong()
            ?: 0L

    private fun previewTimeFlow() = flow {
        val steps = listOf(0L, 45L, 150L, 1665L, 5025L, 7384L) // 0s, 45s, 2'30", 27'45", 1h23'45", 2h03'04"
        var i = 0
        while (true) {
            emit(steps[i++ % steps.size])
            delay(Delay.PREVIEW.time)
        }
    }.flowOn(Dispatchers.IO)
}
