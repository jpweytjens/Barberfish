package com.jpweytjens.barberfish.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Debug-only. Reads/writes the global HUDConfig (and its separately-keyed sparkline) from adb so
// capture scripts can set each shot's HUD and snapshot/restore the user's own. Two actions:
//
//   adb push shot.json /sdcard/bf_hud.json
//   adb shell am broadcast -n com.jpweytjens.barberfish/.extension.HudConfigReceiver \
//       -a com.jpweytjens.barberfish.SET_HUD -f 0x01000000 --es file /sdcard/bf_hud.json
//
//   adb shell am broadcast -n com.jpweytjens.barberfish/.extension.HudConfigReceiver \
//       -a com.jpweytjens.barberfish.GET_HUD -f 0x01000000
//   adb pull /sdcard/Android/data/com.jpweytjens.barberfish/files/hud_config.json
//
// Never ships in release: this class and its manifest entry live in src/debug only.
class HudConfigReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SET = "com.jpweytjens.barberfish.SET_HUD"
        const val ACTION_GET = "com.jpweytjens.barberfish.GET_HUD"
        const val EXTRA_FILE = "file"
        const val OUT_NAME = "hud_config.json"
    }

    // Settings.kt's json is file-private; mirror its settings so blobs round-trip identically.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        val filePath = intent.getStringExtra(EXTRA_FILE)
        val result = goAsync()
        val job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                withTimeout(5_000L) {
                    when (action) {
                        ACTION_SET -> {
                            val path = filePath ?: return@withTimeout
                            val cfg = json.decodeFromString<HUDConfig>(File(path).readText())
                            app.saveHUDConfig(cfg)
                            app.saveHudSparklineConfig(cfg.sparkline)
                        }
                        ACTION_GET -> {
                            val cfg =
                                app.streamHUDConfig()
                                    .first()
                                    .copy(sparkline = app.streamHudSparklineConfig().first())
                            File(app.getExternalFilesDir(null), OUT_NAME)
                                .writeText(json.encodeToString(cfg))
                        }
                    }
                }
            } finally {
                result.finish()
                job.cancel()
            }
        }
    }
}
