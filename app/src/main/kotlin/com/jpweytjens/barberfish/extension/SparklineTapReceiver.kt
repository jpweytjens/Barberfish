package com.jpweytjens.barberfish.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SparklineTapReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.jpweytjens.barberfish.SPARKLINE_TAP"
        const val EXTRA_SURFACE = "surface"
        const val SURFACE_HUD = "hud"
        const val SURFACE_FIELD = "field"
        // Pair<tapTimestamp, nextLookaheadKm>; only the HUD strip listens (transition overlay).
        // Timestamp is always unique (ms), preventing MutableStateFlow deduplication.
        val tapSignal = MutableStateFlow(0L to 10)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isField = intent.getStringExtra(EXTRA_SURFACE) == SURFACE_FIELD
        val result = goAsync()
        val job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                withTimeout(5_000L) {
                    if (isField) {
                        val cfg = context.streamFieldSparklineConfig().first()
                        context.saveFieldSparklineConfig(
                            cfg.copy(lookaheadKm = nextLookahead(cfg.lookaheadKm))
                        )
                    } else {
                        val cfg = context.streamHudSparklineConfig().first()
                        val next = nextLookahead(cfg.lookaheadKm)
                        tapSignal.value = System.currentTimeMillis() to next
                        context.saveHudSparklineConfig(cfg.copy(lookaheadKm = next))
                    }
                }
            } finally {
                result.finish()
                job.cancel()
            }
        }
    }
}

private fun nextLookahead(current: Int): Int =
    when (current) {
        5 -> 10
        10 -> 20
        else -> 5
    }
