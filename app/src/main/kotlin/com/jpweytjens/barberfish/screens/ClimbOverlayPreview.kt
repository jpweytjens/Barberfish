package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ClimbPreviewFixture
import com.jpweytjens.barberfish.datatype.shared.LemonYellow
import com.jpweytjens.barberfish.datatype.shared.buildClimbOverlaySpecs
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import com.jpweytjens.barberfish.datatype.shared.mercatorBoundsAspect
import com.jpweytjens.barberfish.datatype.shared.projectToUnit
import com.jpweytjens.barberfish.datatype.shared.resolveClimbTuning
import com.jpweytjens.barberfish.extension.ClimberMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import kotlin.math.roundToInt

// Fixed preview "zoom": every coloured run gets a chevron so the toggle reads clearly.
// Spaced wide enough that chevrons don't overlap at the middle-section crop's zoom.
private const val PREVIEW_CHEVRON_SPACING_M = 150.0

// On-screen chevron width; the drawable's 25x17 viewport fixes the height ratio. Drawing
// the real ic_climber_chevron_* drawables (grade fill + black outline) matches the device,
// where chevrons sit above the route line as their own symbol layer.
private val CHEVRON_WIDTH = 12.dp
private const val CHEVRON_HEIGHT_RATIO = 17f / 25f

@Composable
internal fun ClimbOverlayPreview(
    config: ClimberMapConfig,
    sparklineConfig: SparklineConfig,
    gradePalette: GradePalette,
    modifier: Modifier = Modifier,
) {
    val specs = remember(config, sparklineConfig, gradePalette) {
        val eff = resolveClimbTuning(config, sparklineConfig)
        val cfg = config.copy(
            skipBands = eff.skipBands,
            simplification = eff.simplification,
            syncWithSparkline = false,
        )
        buildClimbOverlaySpecs(
            routePolyline = ClimbPreviewFixture.routePolyline,
            routeElevationPolyline = ClimbPreviewFixture.elevationPolyline,
            palette = gradePalette,
            readable = false,
            cfg = cfg,
            climbRanges = ClimbPreviewFixture.climbRanges,
            includeChevrons = config.showChevrons,
            chevronSpacingM = PREVIEW_CHEVRON_SPACING_M,
            chevronGuaranteePerRun = true,
        )
    }
    val routePoints = remember { decodeGpsPolyline(ClimbPreviewFixture.routePolyline) }
    val segmentPoints = remember(specs) { specs.polylines.map { decodeGpsPolyline(it.encoded) } }
    val bounds = ClimbPreviewFixture.bounds
    val aspect = remember { mercatorBoundsAspect(bounds).toFloat() }

    val tile = painterResource(
        if (isSystemInDarkTheme()) R.drawable.preview_climb_map_dark
        else R.drawable.preview_climb_map_light,
    )

    // Rasterise one bitmap per distinct grade colour from the real chevron drawables.
    val context = LocalContext.current
    val density = LocalDensity.current
    val chevW = with(density) { CHEVRON_WIDTH.toPx() }.roundToInt().coerceAtLeast(1)
    val chevH = (chevW * CHEVRON_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
    val chevronBitmaps: Map<Int, ImageBitmap> = remember(specs, chevW, chevH) {
        specs.chevrons.map { it.colorArgb }.distinct().mapNotNull { argb ->
            val drawable = ContextCompat.getDrawable(context, gradeChevronDrawable(argb))
                ?: return@mapNotNull null
            argb to drawable.toBitmap(width = chevW, height = chevH).asImageBitmap()
        }.toMap()
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect),
    ) {
        with(tile) { draw(size) }

        fun project(lat: Double, lng: Double): Offset {
            val (u, v) = projectToUnit(bounds, lat, lng)
            return Offset((u * size.width).toFloat(), (v * size.height).toFloat())
        }

        val routeWidth = 5.dp.toPx()
        drawConnected(routePoints.map { project(it.lat, it.lng) }, LemonYellow, routeWidth)

        specs.polylines.zip(segmentPoints).forEach { (spec, points) ->
            drawConnected(points.map { project(it.lat, it.lng) }, Color(spec.colorArgb), routeWidth)
        }

        specs.chevrons.forEach { ch ->
            val bmp = chevronBitmaps[ch.colorArgb] ?: return@forEach
            val center = project(ch.lat, ch.lng)
            // Drawable points up (tip = north); rotate clockwise by the travel bearing.
            rotate(degrees = ch.bearingDeg, pivot = center) {
                drawImage(
                    image = bmp,
                    topLeft = Offset(center.x - bmp.width / 2f, center.y - bmp.height / 2f),
                )
            }
        }
    }
}

private fun DrawScope.drawConnected(points: List<Offset>, color: Color, widthPx: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, color, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
