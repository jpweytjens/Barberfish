package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamSpeedFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
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
                val label =
                    if (cfg.smoothing == SpeedSmoothingStream.S0) "Speed"
                    else "${cfg.smoothing.label} Speed"
                karooSystem.streamDataFlow(cfg.smoothing.typeId).map { state ->
                    val raw =
                        (state as? StreamState.Streaming)
                            ?.dataPoint
                            ?.values
                            ?.get(cfg.smoothing.fieldId)
                            ?: return@map FieldState.unavailable(label)
                    val converted = ConvertType.SPEED.apply(raw, profile)
                    FieldState(
                        "%.1f".format(converted),
                        label = label,
                        color = FieldColor.Default,
                        iconRes = R.drawable.ic_col_speed
                    )
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamSpeedFieldConfig(), karooSystem.streamUserProfile()) { cfg, profile ->
                cfg to profile
            }
            .flatMapLatest { (cfg, profile) ->
                previewSpeedFlow().map { rawMs ->
                    val converted = ConvertType.SPEED.apply(rawMs, profile)
                    FieldState(
                        "%.1f".format(converted),
                        label =
                            if (cfg.smoothing == SpeedSmoothingStream.S0) "Speed"
                            else "${cfg.smoothing.label} Speed",
                        color = FieldColor.Default,
                        iconRes = R.drawable.ic_col_speed
                    )
                }
            }

    private fun previewSpeedFlow() =
        flow {
                // values in m/s
                val steps = listOf(8.33, 9.72, 11.11, 7.50, 10.56) // 30, 35, 40, 27, 38 km/h
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)
}
