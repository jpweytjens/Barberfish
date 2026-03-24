package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamGradeFieldConfig
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

private const val EMA_ALPHA = 0.15

@OptIn(ExperimentalCoroutinesApi::class)
class GradeField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "grade") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamGradeFieldConfig(), context.streamZoneConfig()) { cfg, zones ->
                cfg to zones
            }
            .flatMapLatest { (cfg, zones) ->
                karooSystem
                    .streamDataFlow(DataType.Type.ELEVATION_GRADE)
                    .map { state ->
                        (state as? StreamState.Streaming)
                            ?.dataPoint
                            ?.values
                            ?.get(DataType.Field.ELEVATION_GRADE)
                    }
                    .filterNotNull()
                    .scan(0.0) { ema, raw -> EMA_ALPHA * raw + (1 - EMA_ALPHA) * ema }
                    .map { emaPercent -> toGradeFieldState(emaPercent, cfg, zones.gradePalette, zones.readableColors) }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamGradeFieldConfig(), context.streamZoneConfig()) { cfg, zones ->
                cfg to zones
            }
            .flatMapLatest { (cfg, zones) ->
                flow {
                        val states = previewStates(cfg, zones)
                        var i = 0
                        while (true) {
                            emit(states[i++ % states.size])
                            delay(Delay.PREVIEW.time)
                        }
                    }
                    .flowOn(Dispatchers.IO)
            }

    companion object {
        fun previewStates(cfg: GradeFieldConfig, zones: ZoneConfig): List<FieldState> =
            listOf(2.0, 5.5, 9.2, 13.0, 6.2, 1.0, -5.2).map { percent ->
                toGradeFieldState(percent, cfg, zones.gradePalette, zones.readableColors)
            }

        fun toGradeFieldState(
            percent: Double,
            cfg: GradeFieldConfig,
            palette: GradePalette,
            readable: Boolean = true,
        ): FieldState {
            val color =
                if (cfg.colorMode == ZoneColorMode.NONE) FieldColor.Default
                else FieldColor.Grade(percent, palette, readable)
            return FieldState(
                "%.1f%%".format(percent),
                label = "Grade",
                color = color,
                iconRes = R.drawable.ic_grade,
                colorMode = cfg.colorMode,
            )
        }
    }
}
