package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamSpeedFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
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
class SpeedField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "speed") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) ->
                karooSystem.streamDataFlow(cfg.smoothing.typeId).map { state ->
                    toFieldState(state, profile, cfg.smoothing)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) ->
                flow {
                        val states = previewStates(cfg, profile)
                        var i = 0
                        while (true) {
                            emit(states[i++ % states.size])
                            delay(Delay.PREVIEW.time)
                        }
                    }
                    .flowOn(Dispatchers.IO)
            }

    companion object {
        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            smoothing: SpeedSmoothingStream,
        ): FieldState {
            val label =
                if (smoothing == SpeedSmoothingStream.S0) "Speed"
                else "${smoothing.label} Speed"
            state.toErrorFieldState(label)?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[smoothing.fieldId]
                    ?: return FieldState.unavailable(label)
            val converted = ConvertType.SPEED.apply(raw, profile)
            return FieldState(
                "%.1f".format(converted),
                label = label,
                color = FieldColor.Default,
                iconRes = R.drawable.ic_col_speed,
            )
        }

        fun previewStates(cfg: SpeedFieldConfig, profile: UserProfile): List<FieldState> {
            val label =
                if (cfg.smoothing == SpeedSmoothingStream.S0) "Speed"
                else "${cfg.smoothing.label} Speed"
            // values in m/s: 30, 35, 40, 27, 38, 45, 123.4 km/h
            return listOf(8.33, 9.72, 11.11, 7.50, 10.56, 12.50, 34.28).map { rawMs ->
                FieldState(
                    "%.1f".format(ConvertType.SPEED.apply(rawMs, profile)),
                    label = label,
                    color = FieldColor.Default,
                    iconRes = R.drawable.ic_col_speed,
                )
            }
        }
    }
}
