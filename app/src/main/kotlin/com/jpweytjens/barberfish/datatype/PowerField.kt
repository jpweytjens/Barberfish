package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class PowerField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "power") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamPowerFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(cfg.smoothing.typeId).map { state ->
                    toFieldState(state, cfg.smoothing, profile, zones, cfg.colorMode)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamPowerFieldConfig(),
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
            smoothing: PowerSmoothingStream,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
        ): FieldState {
            val label =
                if (smoothing == PowerSmoothingStream.S0) "Power"
                else "${smoothing.label} Power"
            state.toErrorFieldState(label, R.drawable.ic_col_power)?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[smoothing.fieldId]
                    ?: return FieldState.unavailable(label, R.drawable.ic_col_power)
            val zone = powerZone(raw, profile.powerZones)
            val color = zoneFieldColor(zone, colorMode, profile, zones, isHr = false)
            return FieldState(
                raw.toInt().toString(),
                label = label,
                color = color,
                iconRes = R.drawable.ic_col_power,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: PowerFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> {
            val label =
                if (cfg.smoothing == PowerSmoothingStream.S0) "Power"
                else "${cfg.smoothing.label} Power"
            return listOf(180, 240, 320, 400, 451, 511, 1234).map { watts ->
                val zone = powerZone(watts.toDouble(), profile.powerZones)
                val color = zoneFieldColor(zone, cfg.colorMode, profile, zones, isHr = false)
                FieldState(
                    watts.toString(),
                    label = label,
                    color = color,
                    iconRes = R.drawable.ic_col_power,
                    colorMode = cfg.colorMode,
                )
            }
        }
    }
}
