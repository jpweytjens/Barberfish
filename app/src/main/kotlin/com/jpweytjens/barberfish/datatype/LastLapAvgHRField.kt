package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.Delay
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.HRFieldConfig
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamHRFieldConfig
import com.jpweytjens.barberfish.extension.streamUserProfile
import com.jpweytjens.barberfish.extension.streamZoneConfig
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile
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
class LastLapAvgHRField(private val karooSystem: KarooSystemService) :
    BarberfishDataType("barberfish", "last-lap-avg-hr") {

    companion object {
        fun previewStates(
            cfg: HRFieldConfig,
            profile: UserProfile,
            zones: ZoneConfig,
        ): List<FieldState> = AvgHRField.previewStates(
            cfg, profile, zones,
            label = "LL Avg HR",
            iconRes = R.drawable.ic_last_lap,
            secondaryIconRes = R.drawable.ic_avg_hr,
        )
    }

    override fun liveFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                karooSystem.streamDataFlow(DataType.Type.AVERAGE_HR_LAST_LAP).map { state ->
                    AvgHRField.toFieldState(
                        state, profile, zones, cfg.colorMode,
                        "LL Avg HR", R.drawable.ic_last_lap, R.drawable.ic_avg_hr,
                    )
                }
            }

    override fun previewFlow(context: Context): Flow<FieldState> =
        combine(
                context.streamHRFieldConfig(),
                karooSystem.streamUserProfile(),
                context.streamZoneConfig(),
            ) { cfg, profile, zones ->
                Triple(cfg, profile, zones)
            }
            .flatMapLatest { (cfg, profile, zones) ->
                flow {
                        val states = previewStates(cfg, profile, zones)
                        var i = 0
                        while (true) {
                            emit(states[i++ % states.size])
                            delay(Delay.PREVIEW.time)
                        }
                    }
                    .flowOn(Dispatchers.IO)
            }
}
