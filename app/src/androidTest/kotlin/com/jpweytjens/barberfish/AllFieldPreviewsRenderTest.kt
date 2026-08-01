package com.jpweytjens.barberfish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpweytjens.barberfish.datatype.BarberfishBase
import com.jpweytjens.barberfish.datatype.ElevationSparklineField
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HUDDataType
import com.jpweytjens.barberfish.datatype.RouteRemainingField
import com.jpweytjens.barberfish.datatype.SparklineRender
import com.jpweytjens.barberfish.datatype.shared.GradeReading
import com.jpweytjens.barberfish.datatype.shared.overviewPreviewBitmap
import com.jpweytjens.barberfish.datatype.shared.previewElevationFixture
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.datatype.shared.rvvClimbsFixture
import com.jpweytjens.barberfish.datatype.shared.rvvPoisFixture
import com.jpweytjens.barberfish.datatype.shared.visvalingamWhyatt
import com.jpweytjens.barberfish.datatype.sparklineImageSize
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.RouteRemainingConfig
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.barberfishDataTypes
import com.jpweytjens.barberfish.extension.saveHUDConfig
import com.jpweytjens.barberfish.extension.streamGradeFieldConfig
import com.jpweytjens.barberfish.extension.streamHUDConfig
import com.jpweytjens.barberfish.extension.streamZoneConfig
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
 * Renders every registered field's preview the way the Karoo field picker does (previewFlow →
 * renderState → remoteViewsToBitmap) and writes one PNG per field to the app's external files dir
 * for adb pull:
 *
 * adb pull /sdcard/Android/data/com.jpweytjens.barberfish/files/previews
 *
 * Drive via `scripts/render_previews.sh` (manual `am instrument` flow — never
 * `connectedDebugAndroidTest`, which uninstalls the app and wipes DataStore). Renders are flattened
 * onto black (Karoo dark theme) so white header text stays visible in the PNGs.
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
            val types = barberfishDataTypes(karooSystem)
            // Preview flows cycle through the shared PreviewRide fixture at 1 Hz.
            // Sampling every field at the same cycle position keeps the sheet
            // coherent: index 4 is the ride's steep climb, so power, HR, cadence,
            // and grade all describe the same moment (3-entry duration lists wrap
            // to their mid-ride snapshot). Flows are collected concurrently so one
            // drop depth bounds the wall-clock, not the sum.
            val samples = runBlocking {
                types
                    .map { type ->
                        val config = if (type is HUDDataType) hudConfig else cellConfig
                        async { collectSample(type, drops = 4, config, context) }
                    }
                    .awaitAll()
            }
            for (sample in samples) {
                val config = if (sample.type is HUDDataType) hudConfig else cellConfig
                writePreviewPng(sample, sample.type.typeId, config, design, context, outDir)
            }

            // Second HUD render: flip the HUD setting to 4 columns so both variants
            // land in the contact sheet, then restore the rider's config.
            val hud = types.filterIsInstance<HUDDataType>().single()
            val originalHudConfig = runBlocking { context.streamHUDConfig().first() }
            try {
                runBlocking { context.saveHUDConfig(originalHudConfig.copy(columns = 4)) }
                // The 3-col strip samples the cycle at drops = 0; land elsewhere so the
                // two HUD renders show different values.
                val fourCol = runBlocking { collectSample(hud, drops = 3, hudConfig, context) }
                writePreviewPng(fourCol, "${hud.typeId}-4col", hudConfig, design, context, outDir)
            } finally {
                runBlocking { context.saveHUDConfig(originalHudConfig) }
            }

            // Doc renders: the three Grade statuses as single-cell crops for
            // docs/algorithms.md, written to a subdir so the contact-sheet grid
            // skips them. Fill mode matches the captions there. "Searching…" is
            // not part of the preview cycle, so build the states directly.
            val statesDir = File(outDir, "states").apply { mkdirs() }
            val grade = types.filterIsInstance<GradeField>().single()
            val gradeCfg = runBlocking {
                context.streamGradeFieldConfig().first()
            }
                .copy(colorMode = ZoneColorMode.BACKGROUND)
            val gradePalette = runBlocking { context.streamZoneConfig().first() }.gradePalette
            val gradeReadings =
                listOf(
                    "grade_searching" to GradeReading.Unavailable,
                    "grade_color" to GradeReading.Fresh(13.0f),
                    "grade_stale" to GradeReading.Stale(6.2f),
                )
            for ((name, reading) in gradeReadings) {
                val state = GradeField.toGradeFieldState(reading, gradeCfg, gradePalette)
                writePreviewPng(Sample(grade, state), name, cellConfig, design, context, statesDir)
            }

            // Threshold coloring in docs/data-fields.md is shown as generated SVG
            // strips (scripts/generate_threshold_legends.py), not device renders.

            // Pinned Profile renders for docs: fixed positions on the RvV fixture so
            // recaptures never move the windows. 3 km in, the default 5 km lookahead
            // frames the Muur and the second climb with their summit POIs.
            val sparkline = types.filterIsInstance<ElevationSparklineField>().single()
            val sparkCfg = SparklineConfig()
            val (spWidth, spHeight) = sparklineImageSize(cellConfig, context, sparkCfg.showHeader)
            val elevPoints =
                visvalingamWhyatt(previewElevationFixture(), sparkCfg.simplification.minAreaM2)
            val sparkEdges = sparkCfg.gradeEdges(gradePalette)
            fun profileRender(
                positionM: Float,
                showPois: Boolean,
                climbEdge: Double? = sparkEdges.first,
            ): Sample<SparklineRender> {
                val (spBitmap, _) =
                    renderElevationSparkline(
                        elevationPoints = elevPoints,
                        positionM = positionM,
                        widthPx = spWidth,
                        heightPx = spHeight,
                        density = context.resources.displayMetrics.density,
                        palette = gradePalette,
                        readable = false,
                        lookaheadM = sparkCfg.lookaheadKm * 1000f,
                        climbEdge = climbEdge,
                        descentEdge = sparkEdges.second,
                        minElevRangeM = sparkCfg.yZoom.minRangeM,
                        logWarpK = sparkCfg.warp.k,
                        positionFraction = sparkCfg.warp.positionFraction,
                        climbRanges = rvvClimbsFixture(),
                        showClimbs = sparkCfg.showClimbs,
                        poiDistances = rvvPoisFixture(),
                        showPois = showPois,
                    )
                return Sample(sparkline, SparklineRender(spBitmap, sparkCfg.showHeader))
            }
            // Dot-lifecycle renders for docs/elevation-profile.md: route start (dot at
            // the left edge), mid-ride anchor, and inside the final lookahead window
            // (window pinned to the route end, dot traversing). profile_pois_off pairs
            // with profile_poi. Fixture spans 97.1..20 000 m.
            val docProfileStates =
                listOf(
                    Triple("profile_poi", 3_000f, true),
                    Triple("profile_pois_off", 3_000f, false),
                    Triple("profile_dot_start", 100f, true),
                    Triple("profile_dot_anchor", 10_000f, true),
                    Triple("profile_dot_finish", 19_000f, true),
                )
            for ((name, positionM, showPois) in docProfileStates) {
                val render = profileRender(positionM, showPois)
                writePreviewPng(render, name, cellConfig, design, context, statesDir)
            }
            // Emphasis pair for docs: the route-start window with every band colored
            // vs the default one-band skip that keeps the gentle rises quiet. POIs off
            // so the fill is the only variable.
            writePreviewPng(
                profileRender(100f, false, climbEdge = 0.0),
                "profile_emphasis_off",
                cellConfig,
                design,
                context,
                statesDir,
            )
            writePreviewPng(
                profileRender(100f, false),
                "profile_emphasis_default",
                cellConfig,
                design,
                context,
                statesDir,
            )
            // The grid tile sampled from previewFlow lands wherever the clock-driven
            // sweep happens to be; overwrite it with the pinned render so the
            // all-fields overview shows bands and POIs on every recapture.
            writePreviewPng(
                profileRender(3_000f, true),
                sparkline.typeId,
                cellConfig,
                design,
                context,
                outDir,
            )

            // Overview pinned render for docs: the whole fixture route with the dot
            // at the default 45% position, at the field's default simplification.
            val overviewField = types.filterIsInstance<RouteRemainingField>().single()
            val routeCfg = RouteRemainingConfig()
            val (ovWidth, ovHeight) = sparklineImageSize(cellConfig, context, routeCfg.showHeader)
            val ovBitmap =
                checkNotNull(
                    overviewPreviewBitmap(
                        widthPx = ovWidth,
                        heightPx = ovHeight,
                        isNightMode = true,
                        targetCount = routeCfg.simplification.targetCount,
                    )
                ) {
                    "overview render produced no bitmap"
                }
            writePreviewPng(
                Sample(overviewField, SparklineRender(ovBitmap, routeCfg.showHeader)),
                "overview",
                cellConfig,
                design,
                context,
                statesDir,
            )
        } finally {
            karooSystem.disconnect()
        }
    }

    private fun <T> writePreviewPng(
        sample: Sample<T>,
        name: String,
        config: ViewConfig,
        design: DataFieldDesignConfig,
        context: Context,
        outDir: File,
    ) {
        val flattened = flattenOntoBlack(renderSample(sample, config, design, context))
        assertTrue("$name rendered fully black", hasNonBlackPixel(flattened))
        FileOutputStream(File(outDir, "$name.png")).use {
            flattened.compress(Bitmap.CompressFormat.PNG, 100, it)
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
        val flattened =
            Bitmap.createBitmap(rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
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
