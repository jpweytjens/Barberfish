package com.jpweytjens.barberfish

import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jpweytjens.barberfish.datatype.applySparklineHeaderChrome
import com.jpweytjens.barberfish.datatype.sparklineImageSize
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A graphical field renders its image at [sparklineImageSize] and shows it with fitXY, so that size
 * must equal the image view's laid-out size in every layout, or the profile is stretched.
 */
@RunWith(AndroidJUnit4::class)
class ProfileImageSizeTest {

    // (gridSize, textSize) per K3 layout, cell sized from an assumed 480 x 704 px data area.
    private val layouts =
        listOf(
            (60 to 60) to 96,
            (60 to 30) to 96,
            (60 to 20) to 90,
            (60 to 15) to 69,
            (60 to 12) to 55,
            (30 to 30) to 50,
            (30 to 20) to 50,
            (30 to 15) to 50,
            (30 to 12) to 47,
        )

    @Test
    fun imageIsRenderedAtItsLaidOutSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val mismatches = mutableListOf<String>()
        for ((grid, textSize) in layouts) {
            val cellW = 480 * grid.first / 60
            val cellH = 704 * grid.second / 60
            val config =
                ViewConfig(
                    gridSize = grid,
                    viewSize = cellW to cellH,
                    textSize = textSize,
                    alignment = ViewConfig.Alignment.RIGHT,
                    boundariesEnabled = true,
                    preview = false,
                )
            val rendered = sparklineImageSize(config, context)
            var laidOut = 0 to 0
            instrumentation.runOnMainSync {
                val rv = RemoteViews(context.packageName, R.layout.barberfish_sparkline)
                applySparklineHeaderChrome(
                    rv,
                    context.getString(R.string.elevation_sparkline_name),
                    R.drawable.ic_grade,
                    config,
                    context,
                )
                val view = rv.apply(context, FrameLayout(context))
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(cellW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(cellH, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, cellW, cellH)
                val image = view.findViewById<View>(R.id.sparkline_image)
                laidOut = image.width to image.height
            }
            if (laidOut != rendered) mismatches += "$grid: rendered $rendered, laid out $laidOut"
        }
        assertEquals("", mismatches.joinToString("\n"))
    }
}
