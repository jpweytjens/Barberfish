package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.PreviewRide
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
import com.jpweytjens.barberfish.extension.HRMaxPercentFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHRMaxPercentFieldConfig
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
class HRMaxPercentField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "hr-percent-max") {

    companion object {
        private const val LABEL = "%Max HR"

        fun toFieldState(
            percentState: StreamState,
            hrState: StreamState,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
        ): FieldState {
            val iconRes = R.drawable.ic_col_hr
            percentState
                .toErrorFieldState(
                    LABEL,
                    iconRes,
                    FieldState.noSensor(LABEL, iconRes),
                )
                ?.let {
                    return it
                }
            val percent =
                (percentState as StreamState.Streaming)
                    .dataPoint
                    .values[DataType.Field.PERCENT_MAX_HR]
                    ?: return FieldState.notAvailable(LABEL, iconRes)
            hrState.toErrorFieldState(LABEL, iconRes, FieldState.noSensor(LABEL, iconRes))?.let {
                return it
            }
            val bpm =
                (hrState as StreamState.Streaming).dataPoint.values[DataType.Field.HEART_RATE]
                    ?: return FieldState.notAvailable(LABEL, iconRes)
            val zone = hrZone(bpm, profile.heartRateZones)
            val color = zoneFieldColor(zone, colorMode, profile, zones, isHr = true)
            return FieldState(
                "${percent.toInt()}%",
                label = LABEL,
                color = color,
                iconRes = iconRes,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: HRMaxPercentFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> {
            val maxHr = profile.maxHr.takeIf { it > 0 } ?: 190
            return PreviewRide.hrBpm.map { bpm ->
                val zone = hrZone(bpm.toDouble(), profile.heartRateZones)
                val color = zoneFieldColor(zone, cfg.colorMode, profile, zones, isHr = true)
                val percent = (bpm * 100.0 / maxHr).toInt()
                FieldState(
                    "$percent%",
                    label = LABEL,
                    color = color,
                    iconRes = R.drawable.ic_col_hr,
                    colorMode = cfg.colorMode,
                )
            }
        }
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRMaxPercentFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                combine(
                    karooSystem.streamDataFlow(DataType.Type.PERCENT_MAX_HR),
                    karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
                ) { percentState, hrState ->
                    toFieldState(percentState, hrState, profile, zones, cfg.colorMode)
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRMaxPercentFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                cyclePreview(previewStates(cfg, profile, zones))
            }
}
