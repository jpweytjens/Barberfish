package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
                    toFieldState(state, profile, zones, cfg.colorMode)
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
                cyclePreview(previewStates(cfg, profile, zones))
            }

    companion object {
        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
        ): FieldState {
            state.toErrorFieldState("Avg Power", R.drawable.ic_avg_power)?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[DataType.Field.AVERAGE_POWER]
                    ?: return FieldState.unavailable("Avg Power", R.drawable.ic_avg_power)
            val zone = powerZone(raw, profile.powerZones)
            val color = zoneFieldColor(zone, colorMode, profile, zones, isHr = false)
            return FieldState(
                raw.toInt().toString(),
                label = "Avg Power",
                color = color,
                iconRes = R.drawable.ic_avg_power,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: AvgPowerFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> =
            listOf(195, 210, 220, 185, 230).map { watts ->
                val zone = powerZone(watts.toDouble(), profile.powerZones)
                val color = zoneFieldColor(zone, cfg.colorMode, profile, zones, isHr = false)
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
