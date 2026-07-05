package com.jpweytjens.barberfish

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpweytjens.barberfish.datatype.barberfishFieldRemoteViews
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.datatype.shared.remoteViewsToBitmap
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Smoke test for the docs preview pipeline: render a field the same way the
 * config screen does (barberfishFieldRemoteViews + remoteViewsToBitmap) and
 * write PNGs to the app's external files dir for adb pull:
 *
 *   adb pull /sdcard/Android/data/com.jpweytjens.barberfish/files/previews
 */
@RunWith(AndroidJUnit4::class)
class FieldPreviewRenderSmokeTest {

    @Test
    fun rendersFieldPreviewPngs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val widthPx = 240
        val heightPx = 160
        val sizeConfig = ViewSizeConfig.STANDARD.copy(cellWidthPxOverride = widthPx.toFloat())

        val powerZone5 =
            FieldState(
                primary = "213",
                label = "3s Power",
                color = FieldColor.Zone(zone = 5, total = 7, palette = ZonePalette.KAROO, isHr = false),
                iconRes = R.drawable.ic_col_power,
            )
        val cases =
            listOf(
                "power_zone_text" to ZoneColorMode.TEXT,
                "power_zone_fill" to ZoneColorMode.BACKGROUND,
            )

        val outDir = File(context.getExternalFilesDir(null), "previews").apply { mkdirs() }
        for ((name, colorMode) in cases) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val rv =
                    barberfishFieldRemoteViews(
                        field = powerZone5,
                        alignment = ViewConfig.Alignment.RIGHT,
                        colorMode = colorMode,
                        sizeConfig = sizeConfig,
                        preview = true,
                        context = context,
                    )
                bitmap = remoteViewsToBitmap(rv, widthPx, heightPx, context)
            }
            val rendered = bitmap ?: error("render produced no bitmap for $name")

            val pixels = IntArray(widthPx * heightPx)
            rendered.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
            assertTrue("$name rendered fully transparent", pixels.any { it != 0 })

            FileOutputStream(File(outDir, "$name.png")).use {
                rendered.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
