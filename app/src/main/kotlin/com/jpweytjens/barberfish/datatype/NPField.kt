package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.NPFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamNPFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
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
class NPField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "np") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamNPFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.NORMALIZED_POWER).map { state ->
                    toFieldState(state, profile, zones, cfg.colorMode)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamNPFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                flow {
                    val states = previewStates(cfg, profile, zones)
                    var i = 0
                    while (true) {
                        emit(states[i++ % states.size])
                        delay(Delay.PREVIEW.time)
                    }
                }.flowOn(Dispatchers.IO)
            }

    companion object {
        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
        ): FieldState {
            state.toErrorFieldState("NP")?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[DataType.Field.NORMALIZED_POWER]
                    ?: return FieldState.unavailable("NP")
            val zone = powerZone(raw, profile.powerZones)
            val color = zoneFieldColor(zone, colorMode, profile, zones, isHr = false)
            return FieldState(
                raw.toInt().toString(),
                label = "NP",
                color = color,
                iconRes = R.drawable.ic_col_power,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: NPFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> =
            listOf(240, 255, 247, 262, 238).map { watts ->
                val zone = powerZone(watts.toDouble(), profile.powerZones)
                val color = zoneFieldColor(zone, cfg.colorMode, profile, zones, isHr = false)
                FieldState(
                    watts.toString(),
                    label = "NP",
                    color = color,
                    iconRes = R.drawable.ic_col_power,
                    colorMode = cfg.colorMode,
                )
            }
    }
}
