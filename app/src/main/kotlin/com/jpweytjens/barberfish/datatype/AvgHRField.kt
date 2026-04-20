package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.HRFieldKind
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
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
class AvgHRField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "avg-hr") {

    companion object {
        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
            label: String,
            iconRes: Int,
            secondaryIconRes: Int? = null,
        ): FieldState {
            state.toErrorFieldState(label, iconRes)?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[DataType.Field.AVG_HR]
                    ?: return FieldState.unavailable(label, iconRes)
            val zone = hrZone(raw, profile.heartRateZones)
            val color = zoneFieldColor(zone, colorMode, profile, zones, isHr = true)
            return FieldState(
                raw.toInt().toString(),
                label = label,
                color = color,
                iconRes = iconRes,
                secondaryIconRes = secondaryIconRes,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: HRFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
            label: String = "Avg HR",
            iconRes: Int = R.drawable.ic_avg_hr,
            secondaryIconRes: Int? = null,
        ): List<FieldState> =
            listOf(85, 130, 152, 165, 172, 187, 145).map { bpm ->
                val zone = hrZone(bpm.toDouble(), profile.heartRateZones)
                val color = zoneFieldColor(zone, cfg.colorMode, profile, zones, isHr = true)
                FieldState(
                    bpm.toString(),
                    label = label,
                    color = color,
                    iconRes = iconRes,
                    secondaryIconRes = secondaryIconRes,
                    colorMode = cfg.colorMode,
                )
            }
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(HRFieldKind.AVG),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.AVERAGE_HR).map { state ->
                    toFieldState(state, profile, zones, cfg.colorMode, "Avg HR", R.drawable.ic_avg_hr)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(HRFieldKind.AVG),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                cyclePreview(previewStates(cfg, profile, zones))
            }
}
