package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.HRFieldKind
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class LapAvgHRField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "lap-avg-hr") {

    companion object {
        fun previewStates(
            cfg: HRFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> = AvgHRField.previewStates(
            cfg, profile, zones,
            label = "Lap Avg HR",
            iconRes = R.drawable.ic_lap,
            secondaryIconRes = R.drawable.ic_avg_hr,
        )
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(HRFieldKind.LAP_AVG),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.AVERAGE_LAP_HR).map { state ->
                    AvgHRField.toFieldState(
                        state, profile, zones, cfg.colorMode,
                        "Lap Avg HR", R.drawable.ic_lap, R.drawable.ic_avg_hr,
                    )
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(HRFieldKind.LAP_AVG),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                cyclePreview(previewStates(cfg, profile, zones))
            }
}
