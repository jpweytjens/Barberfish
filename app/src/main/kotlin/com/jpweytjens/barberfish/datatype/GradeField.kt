package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.GradeSmoother
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class GradeField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "grade") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(context.streamGradeFieldConfig(), context.streamZoneConfig()) { cfg, zones ->
                cfg to zones
            }
            .flatMapLatest { (cfg, zones) ->
                gradeOlsFlow(karooSystem)
                    .map { percent ->
                        toGradeFieldState(
                            percent?.toDouble(),
                            cfg,
                            zones.gradePalette,
                            zones.readableColors,
                        )
                    }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(context.streamGradeFieldConfig(), context.streamZoneConfig()) { cfg, zones ->
                cfg to zones
            }
            .flatMapLatest { (cfg, zones) ->
                cyclePreview(previewStates(cfg, zones))
            }

    companion object {
        fun gradeOlsFlow(karooSystem: KarooSystemService): Flow<Float?> {
            val elevFlow =
                karooSystem
                    .streamDataFlow(DataType.Type.PRESSURE_ELEVATION_CORRECTION)
                    .map { state ->
                        (state as? StreamState.Streaming)
                            ?.dataPoint?.values?.get(DataType.Field.PRESSURE_ELEVATION)
                            ?.toFloat()
                    }
            val distFlow =
                karooSystem
                    .streamDataFlow(DataType.Type.DISTANCE)
                    .map { state ->
                        (state as? StreamState.Streaming)
                            ?.dataPoint?.values?.get(DataType.Field.DISTANCE)
                            ?.toFloat()
                    }
            val speedFlow =
                karooSystem
                    .streamDataFlow(DataType.Type.SPEED)
                    .map { state ->
                        (state as? StreamState.Streaming)
                            ?.dataPoint?.values?.get(DataType.Field.SPEED)
                            ?.toFloat()
                    }
            val smoother = GradeSmoother()
            return combine(elevFlow, distFlow, speedFlow) { e, d, v ->
                if (e == null || d == null || v == null) null
                else smoother.update(e, d, v)
            }
        }

        fun previewStates(cfg: GradeFieldConfig, zones: ZoneConfig): List<FieldState> =
            listOf(2.0, 5.5, 9.2, 13.0, 6.2, 1.0, -5.2).map { percent ->
                toGradeFieldState(percent, cfg, zones.gradePalette, zones.readableColors)
            }

        fun toGradeFieldState(
            percent: Double?,
            cfg: GradeFieldConfig,
            palette: GradePalette,
            readable: Boolean = true,
        ): FieldState {
            if (percent == null) {
                return FieldState.notAvailable("Grade", R.drawable.ic_grade)
            }
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
