package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.AvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamAvgPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
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
class AvgPowerField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "avg-power") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamAvgPowerFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.AVERAGE_POWER).map { state ->
                    state.toErrorFieldState("Avg Power")?.let { return@map it }
                    val raw =
                        (state as StreamState.Streaming).dataPoint.values[DataType.Field.AVERAGE_POWER]
                            ?: return@map FieldState.unavailable("Avg Power")
                    val zone = powerZone(raw, profile.powerZones)
                    val color =
                        if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                        else
                            FieldColor.Zone(
                                zone,
                                profile.powerZones.size.coerceAtLeast(1),
                                zones.powerPalette,
                                isHr = false,
                            )
                    FieldState(
                        raw.toInt().toString(),
                        label = "Avg Power",
                        color = color,
                        iconRes = R.drawable.ic_avg_power,
                        colorMode = cfg.colorMode,
                    )
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamAvgPowerFieldConfig(),
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
        fun previewStates(
            cfg: AvgPowerFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> =
            listOf(195, 210, 220, 185, 230).map { watts ->
                val zone = powerZone(watts.toDouble(), profile.powerZones)
                val color =
                    if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                    else
                        FieldColor.Zone(
                            zone,
                            profile.powerZones.size.coerceAtLeast(1),
                            zones.powerPalette,
                            isHr = false,
                        )
                FieldState(
                    watts.toString(),
                    label = "Avg Power",
                    color = color,
                    iconRes = R.drawable.ic_avg_power,
                    colorMode = cfg.colorMode,
                )
            }
    }
}
