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
import com.jpweytjens.barberfish.extension.toErrorFieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The four thin numeric fields. Each streams one native datatype, converts the raw value, and emits
 * a FieldState. Route-gated kinds report "Not available" off-route.
 */
enum class ValueKind(
    val typeId: String,
    val sourceType: String, // DataType.Type.*
    val fieldId: String, // DataType.Field.*
    val convert: ConvertType,
    val decimals: Int,
    val label: String,
    val iconRes: Int,
    val requiresRoute: Boolean,
    val previewRaw: List<Double>,
) {
    DISTANCE(
        "distance",
        DataType.Type.DISTANCE,
        DataType.Field.DISTANCE,
        ConvertType.DISTANCE,
        1,
        "Distance",
        R.drawable.ic_route,
        false,
        listOf(12_300.0, 47_200.0, 103_800.0),
    ),
    DISTANCE_REMAINING(
        "distance-remaining",
        DataType.Type.DISTANCE_TO_DESTINATION,
        DataType.Field.DISTANCE_TO_DESTINATION,
        ConvertType.DISTANCE,
        1,
        "Dist\nRemaining",
        R.drawable.ic_finish_flag,
        true,
        listOf(42_100.0, 23_400.0, 4_800.0),
    ),
    ELEVATION_REMAINING(
        "elevation-remaining",
        DataType.Type.ELEVATION_REMAINING,
        DataType.Field.ASCENT_REMAINING,
        ConvertType.ELEVATION,
        0,
        "Ascent\nRemaining",
        R.drawable.ic_arrow_outward,
        true,
        listOf(1240.0, 540.0, 80.0),
    ),
    DESCENT_REMAINING(
        "descent-remaining",
        DataType.Type.DESCENT_REMAINING,
        DataType.Field.DESCENT_REMAINING,
        ConvertType.ELEVATION,
        0,
        "Descent\nRemaining",
        R.drawable.ic_arrow_outward_down,
        true,
        listOf(1310.0, 610.0, 95.0),
    ),
}

@OptIn(ExperimentalCoroutinesApi::class)
class ValueField(
    private val karooSystem: KarooSystemService,
    private val kind: ValueKind,
) : BarberfishDataType("barberfish", kind.typeId) {

    override fun liveFlow(context: Context): Flow<FieldState> =
        karooSystem.streamUserProfile().flatMapLatest { profile ->
            karooSystem.streamDataFlow(kind.sourceType).map { state ->
                toFieldState(state, kind, profile)
            }
        }

    override fun previewFlow(context: Context): Flow<FieldState> =
        karooSystem.streamUserProfile().flatMapLatest { profile ->
            cyclePreview(previewStates(kind, profile))
        }

    companion object {
        fun toFieldState(state: StreamState, kind: ValueKind, profile: UserProfile): FieldState {
            val notAvailable =
                if (kind.requiresRoute) FieldState.noRoute(kind.label, kind.iconRes)
                else FieldState.notAvailable(kind.label, kind.iconRes)
            state.toErrorFieldState(kind.label, kind.iconRes, notAvailable)?.let {
                return it
            }
            val streaming = state as? StreamState.Streaming ?: return notAvailable
            if (kind.requiresRoute) {
                val onRoute = streaming.dataPoint.values[DataType.Field.ON_ROUTE]
                if (onRoute != null && onRoute == 0.0) {
                    return FieldState.offRoute(kind.label, kind.iconRes)
                }
            }
            val raw = streaming.dataPoint.values[kind.fieldId] ?: return notAvailable
            val converted = kind.convert.apply(raw, profile)
            return FieldState(
                primary = formatFixed(converted, kind.decimals),
                label = kind.label,
                color = FieldColor.Default,
                iconRes = kind.iconRes,
            )
        }

        fun previewStates(kind: ValueKind, profile: UserProfile): List<FieldState> =
            kind.previewRaw.map { raw ->
                FieldState(
                    primary = formatFixed(kind.convert.apply(raw, profile), kind.decimals),
                    label = kind.label,
                    color = FieldColor.Default,
                    iconRes = kind.iconRes,
                )
            }
    }
}
