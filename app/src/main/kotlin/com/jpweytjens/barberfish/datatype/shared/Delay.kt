package com.jpweytjens.barberfish.datatype.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

enum class Delay(val time: Long) {
    PREVIEW(1000L)
}

fun <T> cyclePreview(states: List<T>): Flow<T> = flow {
    var i = 0
    while (true) {
        emit(states[i++ % states.size])
        delay(Delay.PREVIEW.time)
    }
}.flowOn(Dispatchers.IO)
