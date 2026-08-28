package com.jpweytjens.barberfish.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.PerformHardwareAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Debug-only. Presses a Karoo hardware button from adb so capture scripts can drive the
// in-ride flow (pause, resume, lap, end), which ignores injected keyevents:
//
//   adb shell am broadcast -a com.jpweytjens.barberfish.PRESS_BUTTON --es button bottom_right
//
// Buttons: top_left, top_right, bottom_left, bottom_right, control_center, drawer_action.
// Never ships in release: this class and its manifest entry live in src/debug only.
class HardwareActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.jpweytjens.barberfish.PRESS_BUTTON"
        const val EXTRA_BUTTON = "button"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_BUTTON)?.let(::parseButton) ?: return
        val result = goAsync()
        val job = Job()
        val karooSystem = KarooSystemService(context.applicationContext)
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                withTimeout(5_000L) {
                    val connected = CompletableDeferred<Unit>()
                    karooSystem.connect { if (it) connected.complete(Unit) }
                    connected.await()
                    karooSystem.dispatch(action)
                }
            } finally {
                karooSystem.disconnect()
                result.finish()
                job.cancel()
            }
        }
    }
}

private fun parseButton(value: String): PerformHardwareAction? =
    when (value.lowercase()) {
        "top_left" -> PerformHardwareAction.TopLeftPress
        "top_right" -> PerformHardwareAction.TopRightPress
        "bottom_left" -> PerformHardwareAction.BottomLeftPress
        "bottom_right" -> PerformHardwareAction.BottomRightPress
        "control_center" -> PerformHardwareAction.ControlCenterComboPress
        "drawer_action" -> PerformHardwareAction.DrawerActionComboPress
        else -> null
    }
