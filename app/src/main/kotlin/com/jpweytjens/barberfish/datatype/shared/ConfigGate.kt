package com.jpweytjens.barberfish.datatype.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [flow] while [isOn] holds for the latest config, else just [off] with nothing subscribed behind
 * it: switching off cancels [flow] and every stream it holds, switching on starts it afresh. Only a
 * change of [isOn] switches, so other config edits reach [flow] only if it reads them itself.
 *
 * A feature that is switched off must not keep its Karoo streams, service bindings or timers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <C, T> Flow<C>.whileOn(isOn: (C) -> Boolean, off: T, flow: () -> Flow<T>): Flow<T> =
    map(isOn).distinctUntilChanged().flatMapLatest { on -> if (on) flow() else flowOf(off) }
