package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.LapPowerFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamLapPowerFieldConfig
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
class LapPowerField(
    private val karooSystem: KarooSystemService,
    private val isLastLap: Boolean,
) : BarberfishDataType("barberfish", if (isLastLap) "last-lap-power" else "lap-power") {

    private val label = if (isLastLap) "LL Avg Power" else "Lap Avg Power"
    private val sdkType = if (isLastLap) DataType.Type.AVERAGE_POWER_LAST_LAP else DataType.Type.POWER_LAP
    private val iconRes = if (isLastLap) R.drawable.ic_last_lap else R.drawable.ic_lap
    private val secondaryIconRes = R.drawable.ic_avg_power

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamLapPowerFieldConfig(isLastLap),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(sdkType).map { state ->
                    toFieldState(state, profile, zones, cfg.colorMode, isLastLap)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamLapPowerFieldConfig(isLastLap),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                flow {
                    val states = previewStates(cfg, profile, zones, isLastLap)
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
            isLastLap: Boolean,
        ): FieldState {
            val label = if (isLastLap) "LL Avg Power" else "Lap Avg Power"
            val iconRes = if (isLastLap) R.drawable.ic_last_lap else R.drawable.ic_lap
            state.toErrorFieldState(label)?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[DataType.Field.AVERAGE_POWER]
                    ?: return FieldState.unavailable(label)
            val zone = powerZone(raw, profile.powerZones)
            val color =
                if (colorMode == ZoneColorMode.NONE) FieldColor.Default
                else
                    FieldColor.Zone(
                        zone,
                        profile.powerZones.size.coerceAtLeast(1),
                        zones.powerPalette,
                        isHr = false,
                        readable = zones.readableColors,
                    )
            return FieldState(
                raw.toInt().toString(),
                label = label,
                color = color,
                iconRes = iconRes,
                secondaryIconRes = R.drawable.ic_avg_power,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: LapPowerFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
            isLastLap: Boolean,
        ): List<FieldState> {
            val label = if (isLastLap) "LL Avg Power" else "Lap Avg Power"
            val iconRes = if (isLastLap) R.drawable.ic_last_lap else R.drawable.ic_lap
            return listOf(195, 210, 220, 185, 230).map { watts ->
                val zone = powerZone(watts.toDouble(), profile.powerZones)
                val color =
                    if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                    else
                        FieldColor.Zone(
                            zone,
                            profile.powerZones.size.coerceAtLeast(1),
                            zones.powerPalette,
                            isHr = false,
                            readable = zones.readableColors,
                        )
                FieldState(
                    watts.toString(),
                    label = label,
                    color = color,
                    iconRes = iconRes,
                    secondaryIconRes = R.drawable.ic_avg_power,
                    colorMode = cfg.colorMode,
                )
            }
        }
    }
}
