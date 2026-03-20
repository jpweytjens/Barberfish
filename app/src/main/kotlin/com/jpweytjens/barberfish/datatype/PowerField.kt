package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamPowerFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.StreamState
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
                    state.toErrorFieldState("Power")?.let {
                        return@map it
                    }
                    val raw =
                        (state as StreamState.Streaming).dataPoint.values[cfg.smoothing.fieldId]
                            ?: return@map FieldState.unavailable("Power")
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
                        label =
                            if (cfg.smoothing == PowerSmoothingStream.S0) "Power"
                            else "${cfg.smoothing.label} Power",
                        color = color,
                        iconRes = R.drawable.ic_col_power,
                        colorMode = cfg.colorMode
                    )
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
                previewPowerFlow().map { watts ->
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
                        label =
                            if (cfg.smoothing == PowerSmoothingStream.S0) "Power"
                            else "${cfg.smoothing.label} Power",
                        color = color,
                        iconRes = R.drawable.ic_col_power,
                        colorMode = cfg.colorMode
                    )
                }
            }

    private fun previewPowerFlow() =
        flow {
                val steps = listOf(180, 240, 320, 400, 247, 120)
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)
}
