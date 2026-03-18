package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldValue
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
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
class HRField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "hr") {

    override fun liveFlow(context: Context): Flow<FieldValue> =
        combine(
                context.streamHRFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.HEART_RATE).map { state ->
                    state.toErrorFieldValue()?.let {
                        return@map it
                    }
                    val raw =
                        (state as StreamState.Streaming).dataPoint.values[DataType.Field.HEART_RATE]
                            ?: return@map FieldValue("---", "bpm", color = FieldColor.Default)
                    val zone = hrZone(raw, profile.heartRateZones)
                    val color =
                        if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                        else
                            FieldColor.Zone(
                                zone,
                                profile.heartRateZones.size.coerceAtLeast(1),
                                zones.hrPalette,
                                isHr = true,
                            )
                    FieldValue(raw.toInt().toString(), "bpm", color = color)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldValue> =
        combine(
                context.streamHRFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                previewHRFlow().map { bpm ->
                    val zone = hrZone(bpm.toDouble(), profile.heartRateZones)
                    val color =
                        if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                        else
                            FieldColor.Zone(
                                zone,
                                profile.heartRateZones.size.coerceAtLeast(1),
                                zones.hrPalette,
                                isHr = true,
                            )
                    FieldValue(bpm.toString(), "bpm", color = color)
                }
            }

    private fun previewHRFlow() =
        flow {
                val steps = listOf(130, 152, 165, 172, 187, 145)
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)
}
