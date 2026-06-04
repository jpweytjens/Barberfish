package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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
        drawSyntheticMap()

        fun project(lat: Double, lng: Double): Offset {
            val (u, v) = projectToUnit(bounds, lat, lng)
            return Offset((u * size.width).toFloat(), (v * size.height).toFloat())
        }

        // Lay the route on its own road (casing + fill) so the grade colours read as
        // painted on tarmac, like the highlighted route on the device map.
        val routePx = routePoints.map { project(it.lat, it.lng) }
        drawConnected(routePx, MAP_ROAD_CASING, 9.dp.toPx())
        drawConnected(routePx, MAP_ROAD_FILL, 6.dp.toPx())

        val routeWidth = 5.dp.toPx()
        drawConnected(routePx, LemonYellow, routeWidth)

        // Grade-coloured segments overlay the native yellow line only when polylines are on.
        if (config.showPolylines) {
            specs.polylines.zip(segmentPoints).forEach { (spec, points) ->
                drawConnected(points.map { project(it.lat, it.lng) }, Color(spec.colorArgb), routeWidth)
            }
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

// Map palette. Feature hues are from the Karoo render theme
// (docs/ride_decompiled/.../offline.xml): forest/wood #a8bc9a, water #6aabb8, road fill
// #ffffff over a #707070 casing. The base is shifted to an alpine meadow green and topo
// contour lines are added to evoke the reference (a Stelvio-style topo map). The Karoo map
// stays light even in system dark mode (see docs/hud_sparkline.jpg) and the config screen
// is always light, so the preview map is light in both modes.
private val MAP_BG = Color(0xFFD7E1C4)
private val MAP_FOREST = Color(0xFFA8BC9A)
private val MAP_SCREE = Color(0xFFD8D3C9)
private val MAP_WATER = Color(0xFF6AABB8)
private val MAP_CONTOUR = Color(0x73B0926E)
private val MAP_TRAIL = Color(0xFF5E7D4E)
private val MAP_ROAD_CASING = Color(0xFF707070)
private val MAP_ROAD_FILL = Color(0xFFFFFFFF)

// Schematic alpine-topo backdrop drawn entirely in Compose: a meadow-green fill with a
// scree patch, forest, a valley stream, dashed trails, and stacked contour lines, so the
// route reads as a hairpin climb on a topo map without being any real place. Deterministic;
// fractions of the (square) canvas.
private fun DrawScope.drawSyntheticMap() {
    val w = size.width
    val h = size.height

    drawRect(MAP_BG)

    // Scree/rock field on the left flank.
    drawPath(
        Path().apply {
            moveTo(0f, 0.18f * h)
            cubicTo(0.2f * w, 0.26f * h, 0.26f * w, 0.55f * h, 0.16f * w, 0.78f * h)
            cubicTo(0.1f * w, 0.92f * h, 0.04f * w, 0.96f * h, 0f, h)
            lineTo(0f, 0.18f * h); close()
        },
        MAP_SCREE,
    )

    // Forest patches.
    drawPath(
        Path().apply {
            moveTo(0.62f * w, 0f); lineTo(w, 0f); lineTo(w, 0.34f * h)
            cubicTo(0.86f * w, 0.3f * h, 0.74f * w, 0.16f * h, 0.62f * w, 0f); close()
        },
        MAP_FOREST,
    )
    drawPath(
        Path().apply {
            moveTo(0.74f * w, h); lineTo(w, h); lineTo(w, 0.66f * h)
            cubicTo(0.9f * w, 0.74f * h, 0.8f * w, 0.86f * h, 0.74f * w, h); close()
        },
        MAP_FOREST,
    )

    // A few contour lines bowing into the central valley the hairpins climb. Kept sparse
    // and within the square so they read as topo without crowding the route.
    val contourStroke = Stroke(width = 1.dp.toPx())
    val n = 8
    for (i in 0 until n) {
        val y = (i + 0.5f) / n * 0.92f + 0.04f
        val dip = 0.04f + 0.012f * ((i + 1) % 3)
        val bias = 0.5f + 0.04f * ((i % 4) - 1.5f)
        drawPath(
            Path().apply {
                moveTo(0f, y * h)
                cubicTo(
                    0.28f * w, (y - 0.012f) * h,
                    (bias - 0.1f) * w, (y + dip) * h,
                    bias * w, (y + dip) * h,
                )
                cubicTo(
                    (bias + 0.12f) * w, (y + dip) * h,
                    0.78f * w, (y - 0.015f) * h,
                    w, y * h,
                )
            },
            MAP_CONTOUR,
            style = contourStroke,
        )
    }

    // Valley stream snaking up the corridor.
    drawPath(
        Path().apply {
            moveTo(0.34f * w, h)
            cubicTo(0.3f * w, 0.8f * h, 0.42f * w, 0.66f * h, 0.36f * w, 0.5f * h)
            cubicTo(0.32f * w, 0.36f * h, 0.4f * w, 0.2f * h, 0.34f * w, 0f)
        },
        MAP_WATER,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Dashed trails (offline.xml draws footways as dashed lines).
    val trailStroke = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
    )
    for (trail in listOf(
        Path().apply {
            moveTo(0.04f * w, 0.2f * h); cubicTo(0.22f * w, 0.34f * h, 0.16f * w, 0.6f * h, 0.26f * w, 0.82f * h)
        },
        Path().apply {
            moveTo(w, 0.5f * h); cubicTo(0.84f * w, 0.56f * h, 0.78f * w, 0.74f * h, 0.66f * w, 0.9f * h)
        },
    )) {
        drawPath(trail, MAP_TRAIL, style = trailStroke)
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
