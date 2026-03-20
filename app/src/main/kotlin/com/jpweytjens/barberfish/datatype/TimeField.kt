package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamTimeConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

fun formatTime(seconds: Long, format: TimeFormat): String {
    val t = maxOf(0L, seconds)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return when (format) {
        TimeFormat.COMPACT -> if (h > 0) "${h}h${m}'%02d\"".format(s) else "${m}'%02d\"".format(s)
        TimeFormat.CLOCK -> "%d:%02d:%02d".format(h, m, s)
        TimeFormat.HM_S ->
            when {
                h > 0 -> "${h}h ${m}m ${s}s"
                m > 0 -> "${m}m ${s}s"
                else -> "${s}s"
            }
    }
}

fun formatClockTime(secondsSinceMidnight: Long): String {
    val t = secondsSinceMidnight % 86400
    val h = t / 3600
    val m = (t % 3600) / 60
    return "%02d:%02d".format(h, m)
}

enum class TimeKind(val typeId: String, val label: String, val iconRes: Int) {
    TOTAL("time-elapsed", "Elapsed\ntime", R.drawable.ic_time_to_dest),
    RIDING("time-moving", "Moving\ntime", R.drawable.ic_time_to_dest),
    PAUSED("time-paused", "Paused\ntime", R.drawable.ic_time_to_dest),
    TIME_TO_DESTINATION("time-to-destination", "To\nDest", R.drawable.ic_time_to_dest),
    TIME_OF_ARRIVAL("time-of-arrival", "Arrival", R.drawable.ic_time_to_dest),
    TIME_TO_SUNRISE("time-to-sunrise", "Sunrise", R.drawable.ic_sunrise),
    TIME_TO_SUNSET("time-to-sunset", "Sunset", R.drawable.ic_sunset),
    TIME_TO_CIVIL_DAWN("time-to-civil-dawn", "Dawn", R.drawable.ic_sunrise),
    TIME_TO_CIVIL_DUSK("time-to-civil-dusk", "Dusk", R.drawable.ic_sunset),
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimeField(private val karooSystem: KarooSystemService, private val kind: TimeKind) :
    BarberfishDataType("barberfish", kind.typeId) {

    override val sampleMs = 1000L

    override fun liveFlow(context: Context): Flow<FieldState> {
        if (kind == TimeKind.TIME_OF_ARRIVAL) {
            return karooSystem.streamDataFlow(DataType.Type.TIME_OF_ARRIVAL).map { state ->
                FieldState(
                    primary =
                        formatClockTime(extractSeconds(state, DataType.Field.TIME_OF_ARRIVAL)),
                    label = kind.label,
                    color = FieldColor.Default,
                    iconRes = kind.iconRes,
                )
            }
        }

        val secondsFlow =
            when (kind) {
                TimeKind.TOTAL ->
                    karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME).map { state ->
                        extractSeconds(state, DataType.Field.ELAPSED_TIME)
                    }
                TimeKind.PAUSED ->
                    karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME).map { state ->
                        extractSeconds(state, DataType.Field.PAUSED_TIME)
                    }
                TimeKind.RIDING ->
                    combine(
                        karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME).map { state ->
                            extractSeconds(state, DataType.Field.ELAPSED_TIME)
                        },
                        karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME).map { state ->
                            extractSeconds(state, DataType.Field.PAUSED_TIME)
                        },
                    ) { elapsed, paused ->
                        max(0L, elapsed - paused)
                    }
                TimeKind.TIME_TO_DESTINATION ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_DESTINATION).map { state ->
                        extractSeconds(state, DataType.Field.TIME_TO_DESTINATION)
                    }
                TimeKind.TIME_OF_ARRIVAL -> error("handled above")
                TimeKind.TIME_TO_SUNRISE ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_SUNRISE).map { state ->
                        extractSeconds(state, DataType.Field.TIME_TO_SUNRISE)
                    }
                TimeKind.TIME_TO_SUNSET ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_SUNSET).map { state ->
                        extractSeconds(state, DataType.Field.TIME_TO_SUNSET)
                    }
                TimeKind.TIME_TO_CIVIL_DAWN ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_CIVIL_DAWN).map { state ->
                        extractSeconds(state, DataType.Field.TIME_TO_CIVIL_DAWN)
                    }
                TimeKind.TIME_TO_CIVIL_DUSK ->
                    karooSystem.streamDataFlow(DataType.Type.TIME_TO_CIVIL_DUSK).map { state ->
                        extractSeconds(state, DataType.Field.TIME_TO_CIVIL_DUSK)
                    }
            }
        return combine(secondsFlow, context.streamTimeConfig()) { seconds, cfg ->
            FieldState(
                primary = formatTime(seconds, cfg.format),
                label = kind.label,
                color = FieldColor.Default,
                iconRes = kind.iconRes,
            )
        }
    }

    override fun previewFlow(context: Context): Flow<FieldState> =
        context.streamTimeConfig().flatMapLatest { cfg ->
            previewTimeFlow().map { seconds ->
                FieldState(
                    primary = formatTime(seconds, cfg.format),
                    label = kind.label,
                    color = FieldColor.Default,
                    iconRes = kind.iconRes,
                )
            }
        }

    private fun extractSeconds(state: StreamState, fieldKey: String): Long =
        (state as? StreamState.Streaming)?.dataPoint?.values?.get(fieldKey)?.toLong() ?: 0L

    private fun previewTimeFlow() =
        flow {
                val steps = listOf(0L, 45L, 150L, 1665L, 5025L, 36234L)
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)
}
