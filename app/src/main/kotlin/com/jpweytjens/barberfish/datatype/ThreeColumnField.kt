package com.jpweytjens.barberfish.datatype

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldValue
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.datatype.shared.hrZone
import com.jpweytjens.barberfish.datatype.shared.powerZone
import com.jpweytjens.barberfish.extension.PowerStream
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamThreeColumnConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
                val composition = glance.compose(context, DpSize.Unspecified) {
                    ThreeColumnView(
                        left   = FieldValue("42.1", "km/h", "Speed", FieldColor.Default, iconRes = R.drawable.ic_col_speed),
                        center = FieldValue("187",  "bpm",  "HR",    FieldColor.Zone(5, 5, ZonePalette.KAROO, isHr = true),  iconRes = R.drawable.ic_col_hr),
                        right  = FieldValue("1247", "W",    "Power", FieldColor.Zone(7, 7, ZonePalette.KAROO, isHr = false), iconRes = R.drawable.ic_col_power),
                        alignment = config.alignment,
                        colorMode = ZoneColorMode.TEXT,
                    )
                }
                emitter.updateView(composition.remoteViews)
            }
            return
        }

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
                    ) { s, h, p -> Triple(s, h, p) }.map { it to cfg.colorMode }
                }
                .sample(400L)
                .collect { (triple, colorMode) ->
                    val (speed, hr, power) = triple
                    val composition = glance.compose(context, DpSize.Unspecified) {
                        ThreeColumnView(speed, hr, power, config.alignment, colorMode)
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
            iconRes = R.drawable.ic_col_speed,
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
            iconRes = R.drawable.ic_col_hr,
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
            iconRes = R.drawable.ic_col_power,
        )
    }
}
