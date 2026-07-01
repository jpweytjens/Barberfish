package com.jpweytjens.barberfish.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// Debug-only. Sets the global DataFieldDesignConfig (showIcons / labelSize) from adb so the
// layout sweep can mirror the Karoo OS "Data Field Design" toggle without opening the app:
//
//   adb shell am broadcast -a com.jpweytjens.barberfish.SET_DESIGN --es icons off --es label large
//
// Each extra is optional; an absent axis keeps its current value. Because every field combines
// its state with streamDataFieldDesignConfig(), the DataStore edit re-renders live fields with
// no ride restart (same mechanism as SparklineTapReceiver). Never ships in release: this class
// and its manifest entry live in src/debug only.
class DataFieldDesignReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.jpweytjens.barberfish.SET_DESIGN"
        const val EXTRA_ICONS = "icons"
        const val EXTRA_LABEL = "label"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val icons = intent.getStringExtra(EXTRA_ICONS)?.let(::parseBool)
        val label = intent.getStringExtra(EXTRA_LABEL)?.let(::parseLabel)
        val result = goAsync()
        val job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                withTimeout(5_000L) {
                    val cfg = context.streamDataFieldDesignConfig().first()
                    context.saveDataFieldDesignConfig(
                        cfg.copy(
                            showIcons = icons ?: cfg.showIcons,
                            labelSize = label ?: cfg.labelSize,
                        )
                    )
                }
            } finally {
                result.finish()
                job.cancel()
            }
        }
    }
}

private fun parseBool(value: String): Boolean? =
    when (value.lowercase()) {
        "on", "true", "1", "yes" -> true
        "off", "false", "0", "no" -> false
        else -> null
    }

private fun parseLabel(value: String): LabelSize? =
    when (value.lowercase()) {
        "small" -> LabelSize.SMALL
        "large" -> LabelSize.LARGE
        else -> null
    }
