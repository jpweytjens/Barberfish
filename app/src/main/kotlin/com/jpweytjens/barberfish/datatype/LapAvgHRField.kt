package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
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
class LapAvgHRField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "lap-avg-hr") {

    companion object {
        fun previewStates(
            cfg: HRFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> =
            listOf(85, 130, 152, 165, 172, 187, 145).map { bpm ->
                val zone = hrZone(bpm.toDouble(), profile.heartRateZones)
                val color =
                    if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                    else
                        FieldColor.Zone(
                            zone,
                            profile.heartRateZones.size.coerceAtLeast(1),
                            zones.hrPalette,
                            isHr = true,
                            readable = zones.readableColors,
                        )
                FieldState(
                    bpm.toString(),
                    label = "Lap Avg HR",
                    color = color,
                    iconRes = R.drawable.ic_lap,
                    secondaryIconRes = R.drawable.ic_avg_hr,
                    colorMode = cfg.colorMode,
                )
            }
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.AVERAGE_LAP_HR).map { state ->
                    state.toErrorFieldState("Lap Avg HR")?.let {
                        return@map it
                    }
                    val raw =
                        (state as StreamState.Streaming).dataPoint.values[DataType.Field.AVG_HR]
                            ?: return@map FieldState.unavailable("Lap Avg HR")
                    val zone = hrZone(raw, profile.heartRateZones)
                    val color =
                        if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                        else
                            FieldColor.Zone(
                                zone,
                                profile.heartRateZones.size.coerceAtLeast(1),
                                zones.hrPalette,
                                isHr = true,
                                readable = zones.readableColors,
                            )
                    FieldState(
                        raw.toInt().toString(),
                        label = "Lap Avg HR",
                        color = color,
                        iconRes = R.drawable.ic_lap,
                        secondaryIconRes = R.drawable.ic_avg_hr,
                        colorMode = cfg.colorMode,
                    )
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(),
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
                    }
                    .flowOn(Dispatchers.IO)
            }
}
