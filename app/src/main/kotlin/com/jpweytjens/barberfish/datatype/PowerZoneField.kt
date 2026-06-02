package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.MAX_POWER_ZONES
import com.jpweytjens.barberfish.datatype.shared.zoneFieldColor
import com.jpweytjens.barberfish.datatype.shared.zoneFieldLiveFlow
import com.jpweytjens.barberfish.datatype.shared.zoneFieldPreviewFlow
import com.jpweytjens.barberfish.extension.PowerZoneFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.ZoneDisplayMode
import com.jpweytjens.barberfish.extension.streamPowerZoneFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.Flow

class PowerZoneField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "power-zone") {

    companion object {
        private const val LABEL = "Power Zone"

        fun toFieldState(
            state: StreamState,
            profile: UserProfile,
            zones: ZoneConfig,
            colorMode: ZoneColorMode,
            displayMode: ZoneDisplayMode,
        ): FieldState {
            val iconRes = R.drawable.ic_col_power
            state.toErrorFieldState(LABEL, iconRes)?.let { return it }
            val raw =
                (state as StreamState.Streaming).dataPoint.values[DataType.Field.POWER_ZONE]
                    ?: return FieldState.notAvailable(LABEL, iconRes)
            val zoneInt = raw.toInt().coerceIn(1, MAX_POWER_ZONES)
            val value = when (displayMode) {
                ZoneDisplayMode.INTEGER -> zoneInt.toString()
                ZoneDisplayMode.FLOAT -> "%.1f".format(raw)
            }
            val color = zoneFieldColor(zoneInt, colorMode, profile, zones, isHr = false)
            return FieldState(
                value,
                label = LABEL,
                color = color,
                iconRes = iconRes,
                colorMode = colorMode,
            )
        }

        fun previewStates(
            cfg: PowerZoneFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> =
            listOf(1.3, 2.4, 3.1, 4.5, 5.2, 6.0, 6.8).map { raw ->
                val zoneInt = raw.toInt().coerceIn(1, MAX_POWER_ZONES)
                val value = when (cfg.zoneDisplayMode) {
                    ZoneDisplayMode.INTEGER -> zoneInt.toString()
                    ZoneDisplayMode.FLOAT -> "%.1f".format(raw)
                }
                val color = zoneFieldColor(zoneInt, cfg.colorMode, profile, zones, isHr = false)
                FieldState(
                    value,
                    label = LABEL,
                    color = color,
                    iconRes = R.drawable.ic_col_power,
                    colorMode = cfg.colorMode,
                )
            }
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        zoneFieldLiveFlow(
            configFlow = context.streamPowerZoneFieldConfig(),
            profile = karooSystem.streamUserProfile(),
            zones = context.streamZoneConfig(),
            sdkType = DataType.Type.POWER_ZONE,
            karooSystem = karooSystem,
        ) { state, profile, zones, cfg ->
            toFieldState(state, profile, zones, cfg.colorMode, cfg.zoneDisplayMode)
        }

    override fun previewFlow(context: Context): Flow<FieldState> =
        zoneFieldPreviewFlow(
            configFlow = context.streamPowerZoneFieldConfig(),
            profile = karooSystem.streamUserProfile(),
            zones = context.streamZoneConfig(),
            toPreviews = ::previewStates,
        )
}
