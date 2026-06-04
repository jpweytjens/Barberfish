package com.jpweytjens.barberfish.datatype

import android.content.Context
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ConvertType
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.cyclePreview
import com.jpweytjens.barberfish.datatype.shared.formatFixed
import com.jpweytjens.barberfish.extension.streamDataFlow
import com.jpweytjens.barberfish.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

private const val LABEL = "Effort\nLeft"
private val ICON = R.drawable.ic_grade
private const val ASCENT_MARKER = "↗ "

@OptIn(ExperimentalCoroutinesApi::class)
class EffortField(
    private val karooSystem: KarooSystemService,
) : BarberfishDataType("barberfish", "remaining-effort") {

    override fun liveFlow(context: Context): Flow<FieldState> =
        karooSystem.streamUserProfile().flatMapLatest { profile ->
            combine(
                karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION),
                karooSystem.streamDataFlow(DataType.Type.ELEVATION_REMAINING),
            ) { distState, ascentState ->
                toFieldState(distState, ascentState, profile)
            }
        }

    override fun previewFlow(context: Context): Flow<FieldState> =
        karooSystem.streamUserProfile().flatMapLatest { profile ->
            cyclePreview(previewStates(profile))
        }

    companion object {
        private fun onRouteValue(state: StreamState, fieldId: String): Double? {
            val streaming = state as? StreamState.Streaming ?: return null
            val onRoute = streaming.dataPoint.values[DataType.Field.ON_ROUTE]
            if (onRoute != null && onRoute == 0.0) return null
            return streaming.dataPoint.values[fieldId]
        }

        fun toFieldState(distState: StreamState, ascentState: StreamState, profile: UserProfile): FieldState {
            val distM = onRouteValue(distState, DataType.Field.DISTANCE_TO_DESTINATION)
            val ascentM = onRouteValue(ascentState, DataType.Field.ASCENT_REMAINING)
            if (distM == null || ascentM == null) return FieldState.notAvailable(LABEL, ICON)
            return FieldState(
                primary = formatFixed(ConvertType.DISTANCE.apply(distM, profile), 1),
                secondary = ASCENT_MARKER + formatFixed(ConvertType.ELEVATION.apply(ascentM, profile), 0),
                label = LABEL, color = FieldColor.Default, iconRes = ICON,
            )
        }

        fun previewStates(profile: UserProfile): List<FieldState> {
            val samples = listOf(42_100.0 to 1240.0, 23_400.0 to 540.0, 4_800.0 to 80.0)
            return samples.map { (distM, ascentM) ->
                FieldState(
                    primary = formatFixed(ConvertType.DISTANCE.apply(distM, profile), 1),
                    secondary = ASCENT_MARKER + formatFixed(ConvertType.ELEVATION.apply(ascentM, profile), 0),
                    label = LABEL, color = FieldColor.Default, iconRes = ICON,
                )
            }
        }
    }
}
