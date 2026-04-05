package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.datatype.shared.FieldState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
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

/**
 * Returns a [FieldState] for non-Streaming states, or null if the state is [StreamState.Streaming].
 */
fun StreamState.toErrorFieldState(label: String = ""): FieldState? =
    when (this) {
        is StreamState.Streaming -> null
        is StreamState.Searching -> FieldState.searching(label)
        is StreamState.NotAvailable -> FieldState.notAvailable(label)
        else -> FieldState.idle(label) // Idle: sensor stopped emitting data
    }
