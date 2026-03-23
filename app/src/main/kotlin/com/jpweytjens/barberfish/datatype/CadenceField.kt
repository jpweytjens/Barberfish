package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.CadenceFieldConfig
import com.jpweytjens.barberfish.extension.CadenceSmoothingStream
import com.jpweytjens.barberfish.extension.streamCadenceFieldConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class CadenceField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "cadence") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        context.streamCadenceFieldConfig().flatMapLatest { cfg ->
            val label = cadenceLabel(cfg.smoothing)
            karooSystem.streamDataFlow(cfg.smoothing.typeId).map { state ->
                val raw =
                    (state as? StreamState.Streaming)
                        ?.dataPoint?.values?.get(cfg.smoothing.fieldId)
                        ?: return@map FieldState.unavailable(label)
                FieldState(
                    raw.toInt().toString(),
                    label = label,
                    color = FieldColor.Default,
                    iconRes = R.drawable.ic_cadence,
                )
            }
        }

    override fun previewFlow(context: Context): Flow<FieldState> =
        context.streamCadenceFieldConfig().flatMapLatest { cfg ->
            flow {
                val states = previewStates(cfg)
                var i = 0
                while (true) {
                    emit(states[i++ % states.size])
                    delay(Delay.PREVIEW.time)
                }
            }.flowOn(Dispatchers.IO)
        }

    companion object {
        fun cadenceLabel(smoothing: CadenceSmoothingStream) =
            if (smoothing == CadenceSmoothingStream.S0) "Cadence"
            else "${smoothing.label} Cad"

        fun previewStates(cfg: CadenceFieldConfig): List<FieldState> =
            listOf(82, 87, 91, 78, 95).map { rpm ->
                FieldState(
                    rpm.toString(),
                    label = cadenceLabel(cfg.smoothing),
                    color = FieldColor.Default,
                    iconRes = R.drawable.ic_cadence,
                )
            }
    }
}
