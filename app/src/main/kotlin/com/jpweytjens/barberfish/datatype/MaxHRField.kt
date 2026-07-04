package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
import com.jpweytjens.barberfish.datatype.shared.zoneFieldLiveFlow
import com.jpweytjens.barberfish.datatype.shared.zoneFieldPreviewFlow
import com.jpweytjens.barberfish.extension.MaxHRFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamMaxHRFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.Flow

class MaxHRField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "max-hr") {

    companion object {
        private const val LABEL = "Max HR"

        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
        ): FieldState {
            val iconRes = R.drawable.ic_col_hr
            state.toErrorFieldState(LABEL, iconRes, FieldState.noSensor(LABEL, iconRes))?.let {
                return it
            }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[DataType.Field.MAX_HR]
                    ?: return FieldState.notAvailable(LABEL, iconRes)
            if (raw <= 0.0) return FieldState.notAvailable(LABEL, iconRes)
            val zone = hrZone(raw, profile.heartRateZones)
            val color = zoneFieldColor(zone, colorMode, profile, zones, isHr = true)
            return FieldState(
                raw.toInt().toString(),
                label = LABEL,
                color = color,
                iconRes = iconRes,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: MaxHRFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> =
            listOf(165, 172, 178, 184, 187, 190, 193).map { bpm ->
                val zone = hrZone(bpm.toDouble(), profile.heartRateZones)
                val color = zoneFieldColor(zone, cfg.colorMode, profile, zones, isHr = true)
                FieldState(
                    bpm.toString(),
                    label = LABEL,
                    color = color,
                    iconRes = R.drawable.ic_col_hr,
                    colorMode = cfg.colorMode,
                )
            }
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        zoneFieldLiveFlow(
            configFlow = context.streamMaxHRFieldConfig(),
            profile = karooSystem.streamUserProfile(),
            zones = context.streamZoneConfig(),
            sdkType = DataType.Type.MAX_HR,
            karooSystem = karooSystem,
        ) { state, profile, zones, cfg ->
            toFieldState(state, profile, zones, cfg.colorMode)
        }

    override fun previewFlow(context: Context): Flow<FieldState> =
        zoneFieldPreviewFlow(
            configFlow = context.streamMaxHRFieldConfig(),
            profile = karooSystem.streamUserProfile(),
            zones = context.streamZoneConfig(),
            toPreviews = ::previewStates,
        )
}
