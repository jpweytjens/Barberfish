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

// Debug-only. Reads/writes a named Barberfish config from adb so capture scripts can set each
// shot's config and snapshot/restore the user's own. Names: hud, zone (palettes), time
// (formatting) — add a config by adding one `when` branch. Never in release.
//
//   adb push shot.json /sdcard/bf_config.json
//   adb shell am broadcast -n com.jpweytjens.barberfish/.extension.ConfigReceiver \
//       -a com.jpweytjens.barberfish.SET_CONFIG -f 0x01000000 --es name hud --es file
// /sdcard/bf_config.json
//
//   adb shell am broadcast -n com.jpweytjens.barberfish/.extension.ConfigReceiver \
//       -a com.jpweytjens.barberfish.GET_CONFIG -f 0x01000000 --es name hud
//   adb pull /sdcard/Android/data/com.jpweytjens.barberfish/files/hud_config.json
class ConfigReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SET = "com.jpweytjens.barberfish.SET_CONFIG"
        const val ACTION_GET = "com.jpweytjens.barberfish.GET_CONFIG"
        const val EXTRA_NAME = "name"
        const val EXTRA_FILE = "file"
    }

    // Settings.kt's json is file-private; mirror its settings so blobs round-trip identically.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val filePath = intent.getStringExtra(EXTRA_FILE)
        val result = goAsync()
        val job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                withTimeout(5_000L) {
                    when (action) {
                        ACTION_SET ->
                            applyConfig(app, name, File(filePath ?: return@withTimeout).readText())
                        ACTION_GET ->
                            File(app.getExternalFilesDir(null), "${name}_config.json")
                                .writeText(readConfig(app, name) ?: return@withTimeout)
                    }
                }
            } finally {
                result.finish()
                job.cancel()
            }
        }
    }

    private suspend fun applyConfig(app: Context, name: String, text: String) {
        when (name) {
            "hud" -> {
                val cfg = json.decodeFromString<HUDConfig>(text)
                app.saveHUDConfig(cfg)
                app.saveHudSparklineConfig(cfg.sparkline)
            }
            "zone" -> app.saveZoneConfig(json.decodeFromString<ZoneConfig>(text))
            "time" -> app.saveTimeConfig(json.decodeFromString<TimeConfig>(text))
        }
    }

    private suspend fun readConfig(app: Context, name: String): String? =
        when (name) {
            "hud" ->
                json.encodeToString(
                    app.streamHUDConfig()
                        .first()
                        .copy(sparkline = app.streamHudSparklineConfig().first())
                )
            "zone" -> json.encodeToString(app.streamZoneConfig().first())
            "time" -> json.encodeToString(app.streamTimeConfig().first())
            else -> null
        }
}
