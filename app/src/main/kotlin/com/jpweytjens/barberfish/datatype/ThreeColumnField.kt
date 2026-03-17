package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.PowerStream
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamThreeColumnConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalCoroutinesApi::class)
class ThreeColumnField(private val karooSystem: KarooSystemService) :
    DataTypeImpl("barberfish", "three-column") {

    private val glance = GlanceRemoteViews()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        if (config.preview) {
            val scope = CoroutineScope(Dispatchers.IO + Job())
            emitter.setCancellable { scope.cancel() }
            scope.launch {
                combine(
                        context.streamThreeColumnConfig(),
                        context.streamZoneConfig(),
                        karooSystem.streamUserProfile(),
                    ) { cfg, zones, profile ->
                        Triple(cfg, zones, profile)
                    }
                    .flatMapLatest { (cfg, zones, profile) ->
                        previewTripleFlow().map { (speedKph, hrBpm, powerW) ->
                            val hrZoneIdx = hrZone(hrBpm.toDouble(), profile.heartRateZones)
                            val pwrZoneIdx = powerZone(powerW.toDouble(), profile.powerZones)
                            val speed =
                                FieldValue(
                                    "%.1f".format(speedKph),
                                    "km/h",
                                    "Speed",
                                    FieldColor.Default,
                                    R.drawable.ic_col_speed,
                                )
                            val hr =
                                FieldValue(
                                    hrBpm.toString(),
                                    "bpm",
                                    "HR",
                                    FieldColor.Zone(
                                        hrZoneIdx,
                                        profile.heartRateZones.size.coerceAtLeast(1),
                                        zones.hrPalette,
                                        isHr = true,
                                    ),
                                    R.drawable.ic_col_hr,
                                )
                            val power =
                                FieldValue(
                                    powerW.toString(),
                                    "W",
                                    cfg.powerStream.label,
                                    FieldColor.Zone(
                                        pwrZoneIdx,
                                        profile.powerZones.size.coerceAtLeast(1),
                                        zones.powerPalette,
                                        isHr = false,
                                    ),
                                    R.drawable.ic_col_power,
                                )
                            Triple(speed, hr, power) to cfg.colorMode
                        }
                    }
                    .collect { (triple, colorMode) ->
                        val (speed, hr, power) = triple
                        val composition =
                            glance.compose(context, DpSize.Unspecified) {
                                ThreeColumnView(speed, hr, power, config.alignment, colorMode)
                            }
                        emitter.updateView(composition.remoteViews)
                    }
            }
            return
        }

        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }

        scope.launch {
            combine(
                    context.streamThreeColumnConfig(),
                    context.streamZoneConfig(),
                    karooSystem.streamUserProfile(),
                ) { cfg, zones, profile ->
                    Triple(cfg, zones, profile)
                }
                .flatMapLatest { (cfg, zones, profile) ->
                    combine(
                            karooSystem.streamDataFlow(DataType.Type.SPEED).map {
                                toSpeed(it, profile)
                            },
                            karooSystem.streamDataFlow(DataType.Type.HEART_RATE).map {
                                toHr(it, profile, zones)
                            },
                            karooSystem.streamDataFlow(cfg.powerStream.typeId).map {
                                toPower(it, cfg.powerStream, profile, zones)
                            },
                        ) { s, h, p ->
                            Triple(s, h, p)
                        }
                        .map { it to cfg.colorMode }
                }
                .sample(400L)
                .collect { (triple, colorMode) ->
                    val (speed, hr, power) = triple
                    val composition =
                        glance.compose(context, DpSize.Unspecified) {
                            ThreeColumnView(speed, hr, power, config.alignment, colorMode)
                        }
                    emitter.updateView(composition.remoteViews)
                }
        }
    }

    private fun toSpeed(state: StreamState, profile: UserProfile): FieldValue {
        val raw =
            (state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.SPEED)
                ?: return FieldValue.unavailable("Speed")
        val converted = ConvertType.SPEED.apply(raw, profile)
        return FieldValue(
            primary = "%.1f".format(converted),
            unit = ConvertType.SPEED.unit(profile),
            label = "Speed",
            color = FieldColor.Default,
            iconRes = R.drawable.ic_col_speed,
        )
    }

    private fun toHr(state: StreamState, profile: UserProfile, zones: ZoneConfig): FieldValue {
        val raw =
            (state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.HEART_RATE)
                ?: return FieldValue.unavailable("HR")
        val zone = hrZone(raw, profile.heartRateZones)
        return FieldValue(
            primary = raw.toInt().toString(),
            unit = "bpm",
            label = "HR",
            color =
                FieldColor.Zone(
                    zone,
                    profile.heartRateZones.size.coerceAtLeast(1),
                    zones.hrPalette,
                    isHr = true,
                ),
            iconRes = R.drawable.ic_col_hr,
        )
    }

    private fun toPower(
        state: StreamState,
        stream: PowerStream,
        profile: UserProfile,
        zones: ZoneConfig,
    ): FieldValue {
        val raw =
            (state as? StreamState.Streaming)?.dataPoint?.values?.get(stream.fieldId)
                ?: return FieldValue.unavailable("Power")
        val zone = powerZone(raw, profile.powerZones)
        return FieldValue(
            primary = raw.toInt().toString(),
            unit = "W",
            label = stream.label,
            color =
                FieldColor.Zone(
                    zone,
                    profile.powerZones.size.coerceAtLeast(1),
                    zones.powerPalette,
                    isHr = false,
                ),
            iconRes = R.drawable.ic_col_power,
        )
    }

    private fun previewTripleFlow() =
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
