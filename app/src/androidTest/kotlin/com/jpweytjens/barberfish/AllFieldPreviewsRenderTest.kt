package com.jpweytjens.barberfish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpweytjens.barberfish.datatype.BarberfishBase
import com.jpweytjens.barberfish.datatype.HUDDataType
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.barberfishDataTypes
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every registered field's preview the way the Karoo field picker does
 * (previewFlow → renderState → remoteViewsToBitmap) and writes one PNG per field
 * to the app's external files dir for adb pull:
 *
 *   adb pull /sdcard/Android/data/com.jpweytjens.barberfish/files/previews
 *
 * Drive via `scripts/render_previews.sh` (manual `am instrument` flow — never
 * `connectedDebugAndroidTest`, which uninstalls the app and wipes DataStore).
 * Renders are flattened onto black (Karoo dark theme) so white header text
 * stays visible in the PNGs.
 */
@RunWith(AndroidJUnit4::class)
class AllFieldPreviewsRenderTest {

    // 2-col × 4-row cell on the 480-px K3 screen: gridSize (30, 15), value font
    // 94 px / 1.875 density = 50 sp (docs/sdk-findings.md § Native header and value sizing).
    private val cellConfig =
        ViewConfig(
            gridSize = 30 to 15,
            viewSize = 240 to 160,
            textSize = 50,
            alignment = ViewConfig.Alignment.RIGHT,
            boundariesEnabled = false,
            preview = true,
        )

    // The HUD is a full-width strip; give it the whole screen width.
    private val hudConfig = cellConfig.copy(gridSize = 60 to 15, viewSize = 480 to 160)

    @Test
    fun rendersAllFieldPreviewPngs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val karooSystem = KarooSystemService(context)
        val connected = CountDownLatch(1)
        karooSystem.connect { connected.countDown() }
        assertTrue("Karoo system did not connect", connected.await(15, TimeUnit.SECONDS))

        try {
            val design = DataFieldDesignConfig()
            val outDir = File(context.getExternalFilesDir(null), "previews").apply { mkdirs() }
            // Preview flows cycle through a fixture list at 1 Hz. Sampling a staggered
            // position per field keeps same-category neighbours (all power fields, all
            // time fields) from landing on near-identical values. Flows are collected
            // concurrently so the deepest drop bounds the wall-clock, not the sum.
            val samples =
                runBlocking {
                    barberfishDataTypes(karooSystem)
                        .mapIndexed { i, type ->
                            val config = if (type is HUDDataType) hudConfig else cellConfig
                            async { collectSample(type, drops = i % 5, config, context) }
                        }
                        .awaitAll()
                }
            for (sample in samples) {
                val config = if (sample.type is HUDDataType) hudConfig else cellConfig
                val rendered = renderSample(sample, config, design, context)
                val flattened = flattenOntoBlack(rendered)
                assertTrue(
                    "${sample.type.typeId} rendered fully black",
                    hasNonBlackPixel(flattened),
                )
                FileOutputStream(File(outDir, "${sample.type.typeId}.png")).use {
                    flattened.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            karooSystem.disconnect()
        }
    }

    private class Sample<T>(val type: BarberfishBase<T>, val state: T)

    private suspend fun <T> collectSample(
        type: BarberfishBase<T>,
        drops: Int,
        config: ViewConfig,
        context: Context,
    ): Sample<T> =
        Sample(
            type,
            withTimeout(20_000) { type.previewFlow(context, config).drop(drops).first() },
        )

    private fun <T> renderSample(
        sample: Sample<T>,
        config: ViewConfig,
        design: DataFieldDesignConfig,
        context: Context,
    ): Bitmap {
        var bitmap: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val rv = sample.type.renderState(sample.state, design, config, context)
            bitmap = remoteViewsToBitmap(rv, config.viewSize.first, config.viewSize.second, context)
        }
        return bitmap ?: error("render produced no bitmap for ${sample.type.typeId}")
    }

    private fun flattenOntoBlack(rendered: Bitmap): Bitmap {
        val flattened = Bitmap.createBitmap(rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.BLACK)
            drawBitmap(rendered, 0f, 0f, null)
        }
        return flattened
    }

    private fun hasNonBlackPixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { it != Color.BLACK }
    }
}
