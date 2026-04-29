package com.jpweytjens.barberfish.datatype.shared

import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

private val fontMetricsLogged = AtomicBoolean(false)

/**
 * One-shot logger for `Paint.getFontMetrics()` on the fonts we use for
 * value (`relative` monospace) and header (`ibm-plex-sans-condensed`).
 *
 * Verifies the hardcoded `GLYPH_ASCENT_RATIO = 0.756` /
 * `GLYPH_DESCENT_RATIO = 0.244` constants in `ViewSizeConfig.kt` against
 * what the device's font rasteriser actually reports. Filter logcat with
 * `adb logcat -s Barberfish:* | grep FONT_METRICS`.
 *
 * Logs once per process — call from any render hot path; AtomicBoolean
 * compare-and-set prevents repeats.
 */
fun logFontMetricsOnce(density: Float) {
    if (!fontMetricsLogged.compareAndSet(false, true)) return

    val testSp = 50f
    val testPx = testSp * density

    val fonts = listOf(
        "relative" to Typeface.create("relative", Typeface.NORMAL),
        "ibm-plex-sans-condensed" to Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL),
        "MONOSPACE" to Typeface.MONOSPACE,
        "DEFAULT" to Typeface.DEFAULT,
    )

    Log.d("Barberfish", "FONT_METRICS probe: testSp=$testSp testPx=$testPx density=$density")
    for ((name, tf) in fonts) {
        val paint = Paint().apply {
            typeface = tf
            textSize = testPx
            isAntiAlias = true
        }
        val fm = paint.fontMetrics
        val height = fm.descent - fm.ascent
        val ascentRatio = -fm.ascent / testPx
        val descentRatio = fm.descent / testPx
        Log.d(
            "Barberfish",
            "FONT_METRICS $name: ascent=${"%.2f".format(fm.ascent)} " +
                "descent=${"%.2f".format(fm.descent)} " +
                "top=${"%.2f".format(fm.top)} bottom=${"%.2f".format(fm.bottom)} " +
                "leading=${"%.2f".format(fm.leading)} " +
                "height=${"%.2f".format(height)} " +
                "ascent_ratio=${"%.4f".format(ascentRatio)} " +
                "descent_ratio=${"%.4f".format(descentRatio)} " +
                "typeface_eq_default=${tf === Typeface.DEFAULT}"
        )
    }
}
