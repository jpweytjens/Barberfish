package com.jpweytjens.barberfish.extension

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
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

        // Views render every second, and each getBroadcast is a call into the system for a token
        // that never changes, so each (surface, requestCode) builds its intent once.
        private val tapIntents = ConcurrentHashMap<Pair<String, Int>, PendingIntent>()

        /** The tap intent for [surface]; [requestCode] keeps the HUD's layouts apart. */
        fun tapIntent(context: Context, surface: String, requestCode: Int): PendingIntent =
            tapIntents.getOrPut(surface to requestCode) {
                val intent =
                    Intent(context, SparklineTapReceiver::class.java).apply {
                        action = ACTION
                        putExtra(EXTRA_SURFACE, surface)
                    }
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
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
