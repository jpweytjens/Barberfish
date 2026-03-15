package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceRemoteViews
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.PowerStream
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamThreeColumnConfig
import com.jpweytjens.barberfish.extension.streamUserProfile

class ThreeColumnField(private val karooSystem: KarooSystemService) :
    DataTypeImpl("barberfish", "three-column") {

    private val glance = GlanceRemoteViews()

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        emitter.setCancellable { scope.cancel() }

        scope.launch {
            combine(
                context.streamThreeColumnConfig(),
                karooSystem.streamUserProfile(),
            ) { cfg, profile -> cfg to profile }
                .flatMapLatest { (cfg, profile) ->
                    combine(
                        karooSystem.streamDataFlow(DataType.Type.SPEED).map { toSpeed(it, profile) },
                        karooSystem.streamDataFlow(DataType.Type.HEART_RATE).map { toHr(it, profile) },
                        karooSystem.streamDataFlow(cfg.powerStream.typeId).map { toPower(it, cfg.powerStream, profile) },
                    ) { s, h, p -> Triple(s, h, p) }
                }
                .sample(400L)
                .collect { (speed, hr, power) ->
                    val composition = glance.compose(context, DpSize.Unspecified) {
                        ThreeColumnView(speed, hr, power)
                    }
                    emitter.updateView(composition.remoteViews)
                }
        }
    }

    private fun toSpeed(state: StreamState, profile: UserProfile): FieldValue {
        val raw = (state as? StreamState.Streaming)
            ?.dataPoint?.values?.get(DataType.Field.SPEED)
            ?: return FieldValue.unavailable("Speed")
        val converted = ConvertType.SPEED.apply(raw, profile)
        return FieldValue(
            primary = "%.1f".format(converted),
            unit = ConvertType.SPEED.unit(profile),
            label = "Speed",
            color = FieldColor.Default,
        )
    }

    private fun toHr(state: StreamState, profile: UserProfile): FieldValue {
        val raw = (state as? StreamState.Streaming)
            ?.dataPoint?.values?.get(DataType.Field.HEART_RATE)
            ?: return FieldValue.unavailable("HR")
        val zone = hrZone(raw, profile.heartRateZones)
        return FieldValue(
            primary = raw.toInt().toString(),
            unit = "bpm",
            label = "HR",
            color = FieldColor.Zone(zone, profile.heartRateZones.size.coerceAtLeast(1), ZonePalette.KAROO, isHr = true),
        )
    }

    private fun toPower(state: StreamState, stream: PowerStream, profile: UserProfile): FieldValue {
        val raw = (state as? StreamState.Streaming)
            ?.dataPoint?.values?.get(stream.fieldId)
            ?: return FieldValue.unavailable("Power")
        val zone = powerZone(raw, profile.powerZones)
        return FieldValue(
            primary = raw.toInt().toString(),
            unit = "W",
            label = stream.label,
            color = FieldColor.Zone(zone, profile.powerZones.size.coerceAtLeast(1), ZonePalette.KAROO, isHr = false),
        )
    }
}
