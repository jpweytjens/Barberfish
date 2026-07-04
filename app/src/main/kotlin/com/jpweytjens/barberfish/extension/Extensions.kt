package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.FieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnGlobalPOIs
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val listenerId =
        addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
    awaitClose { removeConsumer(listenerId) }
}

inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> = callbackFlow {
    val listenerId = addConsumer<T> { trySend(it) }
    awaitClose { removeConsumer(listenerId) }
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> = consumerFlow()

fun KarooSystemService.streamNavigationState(): Flow<OnNavigationState> = consumerFlow()

fun KarooSystemService.streamRideState(): Flow<RideState> = consumerFlow()

/**
 * Global (saved) POIs, delivered independently of the active route. Uses the explicit
 * [OnGlobalPOIs.Params] consumer so the event is actually started, rather than the param-less
 * [consumerFlow] overload which may not begin emitting for this event.
 */
fun KarooSystemService.streamGlobalPOIs(): Flow<OnGlobalPOIs> = callbackFlow {
    val listenerId = addConsumer(OnGlobalPOIs.Params) { event: OnGlobalPOIs -> trySendBlocking(event) }
    awaitClose { removeConsumer(listenerId) }
}

/**
 * Returns a [FieldState] for non-Streaming states, or null if the state is [StreamState.Streaming].
 * [notAvailable] lets a field say what its NotAvailable means (e.g. [FieldState.noSensor] for
 * sensor fields, [FieldState.noGps] for GPS-derived fields); the default keeps the generic text.
 */
fun StreamState.toErrorFieldState(
    label: String = "",
    iconRes: Int? = null,
    notAvailable: FieldState = FieldState.notAvailable(label, iconRes),
): FieldState? =
    when (this) {
        is StreamState.Streaming -> null
        is StreamState.Searching -> FieldState.searching(label, iconRes)
        is StreamState.NotAvailable -> notAvailable
        else -> FieldState.idle(label, iconRes) // Idle: sensor stopped emitting data
    }

/** Current lap number from a LAP_NUMBER stream state; 0 if not streaming or missing. */
fun lapNumberFrom(state: StreamState): Int =
    (state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.LAP_NUMBER)?.toInt()
        ?: 0
