package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HudState
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHUDConfig
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class HUDField(private val karooSystem: KarooSystemService) :
    HUDDataType("barberfish", "three-column") {

    override fun liveFlow(context: Context) =
        combine(
                context.streamHUDConfig(),
                context.streamZoneConfig(),
                karooSystem.streamUserProfile(),
            ) { cfg, zones, profile ->
                Triple(cfg, zones, profile)
            }
            .flatMapLatest { (cfg, zones, profile) ->
                combine(
                    karooSystem.streamDataFlow(DataType.Type.SPEED).map { toSpeed(it, profile) },
                    karooSystem.streamDataFlow(DataType.Type.HEART_RATE).map {
                        toHr(it, profile, zones)
                    },
                    karooSystem.streamDataFlow(cfg.powerStream.typeId).map {
                        toPower(it, cfg.powerStream, profile, zones)
                    },
                ) { s, h, p ->
                    HudState(s, h, p, cfg.colorMode)
                }
            }

    override fun previewFlow(context: Context) =
        combine(
                context.streamHUDConfig(),
                context.streamZoneConfig(),
                karooSystem.streamUserProfile(),
            ) { cfg, zones, profile ->
                Triple(cfg, zones, profile)
            }
            .flatMapLatest { (cfg, zones, profile) ->
                previewHudFlow().map { (speedKph, hrBpm, powerW) ->
                    val hrZoneIdx = hrZone(hrBpm.toDouble(), profile.heartRateZones)
                    val pwrZoneIdx = powerZone(powerW.toDouble(), profile.powerZones)
                    HudState(
                        speed =
                            FieldState(
                                "%.1f".format(speedKph),
                                "Speed",
                                FieldColor.Default,
                                R.drawable.ic_col_speed,
                            ),
                        hr =
                            FieldState(
                                hrBpm.toString(),
                                "HR",
                                FieldColor.Zone(
                                    hrZoneIdx,
                                    profile.heartRateZones.size.coerceAtLeast(1),
                                    zones.hrPalette,
                                    isHr = true
                                ),
                                R.drawable.ic_col_hr,
                            ),
                        power =
                            FieldState(
                                powerW.toString(),
                                if (cfg.powerStream == PowerSmoothingStream.S0) "Power"
                                else "${cfg.powerStream.label} Power",
                                FieldColor.Zone(
                                    pwrZoneIdx,
                                    profile.powerZones.size.coerceAtLeast(1),
                                    zones.powerPalette,
                                    isHr = false
                                ),
                                R.drawable.ic_col_power,
                            ),
                        colorMode = cfg.colorMode,
                    )
                }
            }

    private fun toSpeed(state: StreamState, profile: UserProfile): FieldState {
        state.toErrorFieldState("Speed")?.let {
            return it
        }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[DataType.Field.SPEED]
                ?: return FieldState.unavailable("Speed")
        return FieldState(
            primary = "%.1f".format(ConvertType.SPEED.apply(raw, profile)),
            label = "Speed",
            color = FieldColor.Default,
            iconRes = R.drawable.ic_col_speed,
        )
    }

    private fun toHr(state: StreamState, profile: UserProfile, zones: ZoneConfig): FieldState {
        state.toErrorFieldState("HR")?.let {
            return it
        }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[DataType.Field.HEART_RATE]
                ?: return FieldState.unavailable("HR")
        return FieldState(
            primary = raw.toInt().toString(),
            label = "HR",
            color =
                FieldColor.Zone(
                    hrZone(raw, profile.heartRateZones),
                    profile.heartRateZones.size.coerceAtLeast(1),
                    zones.hrPalette,
                    isHr = true
                ),
            iconRes = R.drawable.ic_col_hr,
        )
    }

    private fun toPower(
        state: StreamState,
        stream: PowerSmoothingStream,
        profile: UserProfile,
        zones: ZoneConfig
    ): FieldState {
        val label = if (stream == PowerSmoothingStream.S0) "Power" else "${stream.label} Power"
        state.toErrorFieldState(label)?.let {
            return it
        }
        val raw =
            (state as StreamState.Streaming).dataPoint.values[stream.fieldId]
                ?: return FieldState.unavailable(label)
        return FieldState(
            primary = raw.toInt().toString(),
            label = label,
            color =
                FieldColor.Zone(
                    powerZone(raw, profile.powerZones),
                    profile.powerZones.size.coerceAtLeast(1),
                    zones.powerPalette,
                    isHr = false
                ),
            iconRes = R.drawable.ic_col_power,
        )
    }

    private fun previewHudFlow() =
        flow {
                val steps =
                    listOf(
                        Triple(28.5, 130, 180),
                        Triple(35.2, 152, 240),
                        Triple(42.1, 168, 320),
                        Triple(58.7, 187, 1247),
                        Triple(31.0, 145, 200),
                    )
                var i = 0
                while (true) {
                    emit(steps[i++ % steps.size])
                    delay(Delay.PREVIEW.time)
                }
            }
            .flowOn(Dispatchers.IO)
}
