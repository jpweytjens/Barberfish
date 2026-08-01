package com.jpweytjens.barberfish.datatype.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// Cycle interval for preview animations (the config screen cycles through preview
// FieldStates at this rate so the user sees the field's appearance vary).
internal const val PREVIEW_DELAY_MS = 1000L

// HUD + sparkline render tick. The HUD and the live sparkline both throttle their
// upstream data flows to 1 Hz to avoid spending CPU on bitmap regeneration faster
// than the human eye registers, and the debug sweep emits at the same rate.
internal const val HUD_UPDATE_INTERVAL_MS = 1000L

fun <T> cyclePreview(states: List<T>): Flow<T> = flow {
    var i = 0
    while (true) {
        emit(states[i++ % states.size])
        delay(PREVIEW_DELAY_MS)
    }
}
    .flowOn(Dispatchers.IO)
