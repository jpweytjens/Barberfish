package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.AvgSpeedPrior
import com.jpweytjens.barberfish.datatype.shared.ETAInput
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.ETAState
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.computeRidingETA
import com.jpweytjens.barberfish.datatype.shared.initETAState
import com.jpweytjens.barberfish.datatype.shared.updateETAState
import com.jpweytjens.barberfish.extension.ETAConfig
import com.jpweytjens.barberfish.extension.TimeFormat
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamETAConfig
import com.jpweytjens.barberfish.extension.streamTimeConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import java.util.Calendar

enum class ETAKind(val typeId: String, val label: String, val iconRes: Int) {
    REMAINING_RIDE_TIME("remaining-ride-time", "Ride\nTime", R.drawable.ic_time_to_dest),
    TIME_TO_DESTINATION("time-to-destination", "To\nDest", R.drawable.ic_time_to_dest),
    TIME_OF_ARRIVAL("time-of-arrival", "ETA", R.drawable.ic_time_to_dest),
}

private data class RawETA(
    val distRiddenM: Double,
    val elapsedMs: Double,
    val distToDestM: Double?,
    val pausedMs: Double,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ETAField(
    private val karooSystem: KarooSystemService,
    private val kind: ETAKind,
) : BarberfishDataType("barberfish", kind.typeId) {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
            context.streamETAConfig(),
            context.streamTimeConfig(),
        ) { etaCfg, timeCfg -> etaCfg to timeCfg }
            .flatMapLatest { (etaCfg, timeCfg) ->
                streamFlow(karooSystem, kind, etaCfg, timeCfg.format)
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
            context.streamETAConfig(),
            context.streamTimeConfig(),
        ) { etaCfg, timeCfg -> etaCfg to timeCfg }
            .flatMapLatest { (_, timeCfg) ->
                cyclePreview(previewStates(kind, timeCfg.format))
            }

    companion object {
        fun streamFlow(
            karooSystem: KarooSystemService,
            kind: ETAKind,
            etaCfg: ETAConfig,
            format: TimeFormat,
        ): Flow<FieldState> {
            val prior = AvgSpeedPrior(speedKph = etaCfg.priorSpeedKph)

            val rawFlow = combine(
                karooSystem.streamDataFlow(DataType.Type.DISTANCE)
                    .map { extractRawDouble(it, DataType.Field.DISTANCE) },
                karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME)
                    .map { extractRawDouble(it, DataType.Field.ELAPSED_TIME) },
                karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)
                    .map { extractDistToDest(it) },
                karooSystem.streamDataFlow(DataType.Type.PAUSED_TIME)
                    .map { extractRawDouble(it, DataType.Field.PAUSED_TIME) },
            ) { distM, elapsedMs, distToDest, pausedMs ->
                RawETA(distM, elapsedMs, distToDest, pausedMs)
            }

            return rawFlow
                .runningFold(initETAState(prior) to null as RawETA?) { (state, _), raw ->
                    val input = ETAInput(
                        distanceRiddenM = raw.distRiddenM,
                        elapsedTimeMs = raw.elapsedMs,
                        distanceToDestM = raw.distToDestM ?: 0.0,
                        pausedTimeMs = raw.pausedMs,
                        prior = prior,
                    )
                    updateETAState(input, state) to raw
                }
                .map { (state, raw) ->
                    if (raw == null || raw.distToDestM == null) {
                        return@map FieldState.unavailable(kind.label)
                    }

                    val input = ETAInput(
                        distanceRiddenM = raw.distRiddenM,
                        elapsedTimeMs = raw.elapsedMs,
                        distanceToDestM = raw.distToDestM,
                        pausedTimeMs = raw.pausedMs,
                        prior = prior,
                    )

                    val ridingEtaSec = computeRidingETA(input, state)
                    if (ridingEtaSec < 0) {
                        return@map FieldState(
                            primary = "--",
                            label = kind.label,
                            color = FieldColor.Default,
                            iconRes = kind.iconRes,
                        )
                    }

                    val pausedSec = (raw.pausedMs / 1000.0).toLong()

                    val displayValue = when (kind) {
                        ETAKind.REMAINING_RIDE_TIME ->
                            formatTime(ridingEtaSec, format)
                        ETAKind.TIME_TO_DESTINATION ->
                            formatTime(ridingEtaSec + pausedSec, format)
                        ETAKind.TIME_OF_ARRIVAL ->
                            formatClockTime(ridingEtaSec + pausedSec)
                    }

                    FieldState(
                        primary = displayValue,
                        label = kind.label,
                        color = FieldColor.Default,
                        iconRes = kind.iconRes,
                    )
                }
        }

        fun previewStates(kind: ETAKind, format: TimeFormat): List<FieldState> {
            val durations = listOf(1665L, 5025L, 37425L)
            return durations.map { sec ->
                val displayValue = when (kind) {
                    ETAKind.REMAINING_RIDE_TIME -> formatTime(sec, format)
                    ETAKind.TIME_TO_DESTINATION -> formatTime(sec, format)
                    ETAKind.TIME_OF_ARRIVAL -> formatClockTime(sec)
                }
                FieldState(
                    primary = displayValue,
                    label = kind.label,
                    color = FieldColor.Default,
                    iconRes = kind.iconRes,
                )
            }
        }

        private fun extractRawDouble(state: StreamState, fieldKey: String): Double =
            (state as? StreamState.Streaming)?.dataPoint?.values?.get(fieldKey) ?: 0.0

        private fun extractDistToDest(state: StreamState): Double? {
            val streaming = state as? StreamState.Streaming ?: return null
            val onRoute = streaming.dataPoint.values[DataType.Field.ON_ROUTE]
            if (onRoute != null && onRoute == 0.0) return null
            return streaming.dataPoint.values[DataType.Field.DISTANCE_TO_DESTINATION]
        }

        private fun formatClockTime(secondsFromNow: Long): String {
            val cal = Calendar.getInstance().apply {
                timeInMillis += secondsFromNow * 1000
            }
            return "%d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
    }
}
