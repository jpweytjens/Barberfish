package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.jpweytjens.barberfish.datatype.shared.ClimbPreviewFixture
import com.jpweytjens.barberfish.datatype.shared.LemonYellow
import com.jpweytjens.barberfish.datatype.shared.buildGradeMapSpecs
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import com.jpweytjens.barberfish.datatype.shared.mercatorBoundsAspect
import com.jpweytjens.barberfish.datatype.shared.projectToUnit
import com.jpweytjens.barberfish.datatype.shared.resolveGradeMapTuning
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// Fixed preview "zoom": the fixture's macro band runs are curated to stay comfortably
// longer than this spacing, so at MEDIUM and HEAVY simplification every coloured run
// contains a cadence position and shows a chevron. At NONE and MILD the texture tiers
// cut in far shorter runs and some go bare (no cadence position lands inside them) —
// the same as how the field renders on the device, which carries no per-run guarantee
// either.
private const val PREVIEW_CHEVRON_SPACING_M = 150.0

// On-screen chevron width; the drawable's 25x17 viewport fixes the height ratio. Drawing
// the real ic_climber_chevron_* drawables (grade fill + black outline) matches the device,
// where chevrons sit above the route line as their own symbol layer.
private val CHEVRON_WIDTH = 12.dp
private const val CHEVRON_HEIGHT_RATIO = 17f / 25f

@Composable
internal fun GradeMapPreview(
    config: GradeMapConfig,
    sparklineConfig: SparklineConfig,
    gradePalette: GradePalette,
    modifier: Modifier = Modifier,
) {
    val specs =
        remember(config, sparklineConfig, gradePalette) {
            val eff = resolveGradeMapTuning(config, sparklineConfig, gradePalette)
            buildGradeMapSpecs(
                routePolyline = ClimbPreviewFixture.routePolyline,
                routeElevationPolyline = ClimbPreviewFixture.elevationPolyline,
                palette = gradePalette,
                readable = false,
                tuning = eff,
                // Always place chevrons; the toggle only changes their colour (grade vs native).
                includeChevrons = true,
                chevronSpacingM = PREVIEW_CHEVRON_SPACING_M,
            )
        }
    val routePoints = remember { decodeGpsPolyline(ClimbPreviewFixture.routePolyline) }
    val segmentPoints = remember(specs) { specs.polylines.map { decodeGpsPolyline(it.encoded) } }
    val bounds = ClimbPreviewFixture.bounds
    val aspect = remember { mercatorBoundsAspect(bounds).toFloat() }

    // Rasterise one chevron bitmap per colour we will draw: grade-band colours when the
    // chevron toggle is on, otherwise the single native LemonYellow chevron.
    val context = LocalContext.current
    val density = LocalDensity.current
    val chevW = with(density) { CHEVRON_WIDTH.toPx() }.roundToInt().coerceAtLeast(1)
    val chevH = (chevW * CHEVRON_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
    val chevronBitmaps: Map<Int, ImageBitmap> =
        remember(specs, config.showChevrons, chevW, chevH) {
            val argbs =
                if (config.showChevrons) {
                    specs.chevrons.map { it.colorArgb }.distinct()
                } else {
                    listOf(LemonYellow.toArgb())
                }
            argbs
                .mapNotNull { argb ->
                    val drawable =
                        ContextCompat.getDrawable(context, gradeChevronDrawable(argb))
                            ?: return@mapNotNull null
                    argb to drawable.toBitmap(width = chevW, height = chevH).asImageBitmap()
                }
                .toMap()
        }

    Canvas(modifier.fillMaxWidth().aspectRatio(aspect)) {
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
        // Pull each contiguous chain's outer ends in by half the stroke so the round cap
        // lands on the true endpoint, mirroring the device's metre-space cap trim.
        if (config.showPolylines) {
            specs.polylines.zip(segmentPoints).forEach { (spec, points) ->
                val px = points.map { project(it.lat, it.lng) }
                val trimmed =
                    trimEndsPx(
                        px,
                        startPx = if (spec.trimStart) routeWidth / 2f else 0f,
                        endPx = if (spec.trimEnd) routeWidth / 2f else 0f,
                    )
                drawConnected(trimmed, Color(spec.colorArgb), routeWidth)
            }
        }

        specs.chevrons.forEach { ch ->
            val argb = if (config.showChevrons) ch.colorArgb else LemonYellow.toArgb()
            val bmp = chevronBitmaps[argb] ?: return@forEach
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

// Map palette. Feature hues are sampled from the Karoo map: forest/wood #a8bc9a,
// water #6aabb8, road fill #ffffff over a #707070 casing. The base is shifted to an alpine meadow
// green and topo
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
// scree patch, corner forests, a stream from the col, stacked contour lines, and an
// alpine lake below the pass, so the route reads as an up-and-over pass on a topo map
// without being any real place. Deterministic; fractions of the (square) canvas.
private fun DrawScope.drawSyntheticMap() {
    val w = size.width
    val h = size.height

    drawRect(MAP_BG)

    // Scree/rock field on the upper-left climb flank, below the col.
    drawPath(
        Path().apply {
            moveTo(0f, 0.06f * h)
            cubicTo(0.14f * w, 0.10f * h, 0.22f * w, 0.28f * h, 0.14f * w, 0.44f * h)
            cubicTo(0.08f * w, 0.54f * h, 0.03f * w, 0.56f * h, 0f, 0.58f * h)
            lineTo(0f, 0.06f * h)
            close()
        },
        MAP_SCREE,
    )

    // Forest patches tucked into the corners, clear of the road.
    drawPath(
        Path().apply {
            moveTo(0.88f * w, 0f)
            lineTo(w, 0f)
            lineTo(w, 0.20f * h)
            cubicTo(0.96f * w, 0.16f * h, 0.92f * w, 0.08f * h, 0.88f * w, 0f)
            close()
        },
        MAP_FOREST,
    )
    drawPath(
        Path().apply {
            moveTo(0f, 0.74f * h)
            cubicTo(0.06f * w, 0.80f * h, 0.10f * w, 0.90f * h, 0.14f * w, h)
            lineTo(0f, h)
            close()
        },
        MAP_FOREST,
    )

    // A few contour lines bowing into the valley between the two flanks. Kept sparse
    // and within the square so they read as topo without crowding the route.
    val contourStroke = Stroke(width = 1.dp.toPx())
    val n = 8
    for (i in 0 until n) {
        val y = (i + 0.5f) / n * 0.92f + 0.04f
        val dip = 0.05f + 0.012f * ((i + 1) % 3)
        val bias = 0.55f + 0.03f * ((i % 4) - 1.5f)
        drawPath(
            Path().apply {
                moveTo(0f, y * h)
                cubicTo(
                    0.22f * w,
                    (y - 0.018f) * h,
                    (bias - 0.14f) * w,
                    (y + dip) * h,
                    bias * w,
                    (y + dip) * h,
                )
                cubicTo(
                    (bias + 0.14f) * w,
                    (y + dip) * h,
                    0.82f * w,
                    (y - 0.018f) * h,
                    w,
                    y * h,
                )
            },
            MAP_CONTOUR,
            style = contourStroke,
        )
    }

    // Stream from the col valley down to the lake snout.
    drawPath(
        Path().apply {
            moveTo(0.53f * w, 0.10f * h)
            cubicTo(0.60f * w, 0.30f * h, 0.52f * w, 0.52f * h, 0.60f * w, 0.68f * h)
            cubicTo(0.66f * w, 0.78f * h, 0.72f * w, 0.82f * h, 0.74f * w, 0.87f * h)
        },
        MAP_WATER,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    drawBarberfishLake(w, h)

    // Dashed trails (offline.xml draws footways as dashed lines).
    val trailStroke =
        Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
        )
    for (trail in
        listOf(
            Path().apply {
                moveTo(0.04f * w, 0.30f * h)
                cubicTo(0.16f * w, 0.44f * h, 0.10f * w, 0.62f * h, 0.20f * w, 0.80f * h)
            },
            Path().apply {
                moveTo(w, 0.30f * h)
                cubicTo(0.94f * w, 0.42f * h, 0.96f * w, 0.58f * h, 0.90f * w, 0.72f * h)
            },
        )) {
        drawPath(trail, MAP_TRAIL, style = trailStroke)
    }
}

// Alpine lake below the pass, its shoreline a barberfish (blacknose butterflyfish)
// silhouette with a small islet as the eye. Local coordinates (x right, y up), nose
// at -x, unit length 1.0 nose-to-tail; scaled by the lake length and rotated so the
// snout points at the route's end. Reads as a lake first; not a real place.
private val FISH_OUTLINE =
    listOf(
        -0.50f to -0.02f, // snout tip
        -0.42f to 0.06f,
        -0.30f to 0.17f, // steep forehead
        -0.12f to 0.26f,
        0.05f to 0.28f, // dorsal peak
        0.20f to 0.22f,
        0.30f to 0.10f, // upper peduncle
        0.36f to 0.06f,
        0.47f to 0.09f, // small truncate tail
        0.50f to 0.00f,
        0.47f to -0.09f,
        0.36f to -0.06f,
        0.28f to -0.13f,
        0.22f to -0.22f, // anal fin corner
        0.05f to -0.26f,
        -0.15f to -0.22f,
        -0.30f to -0.13f,
        -0.44f to -0.06f,
    )

private const val LAKE_ROTATION_DEG = 188.0

private fun DrawScope.drawBarberfishLake(w: Float, h: Float) {
    val cx = 0.63f * w
    val cy = 0.89f * h
    val len = 0.23f * w
    val cosT = cos(Math.toRadians(LAKE_ROTATION_DEG)).toFloat()
    val sinT = sin(Math.toRadians(LAKE_ROTATION_DEG)).toFloat()
    // Rotate in local y-up coordinates, then flip to canvas y-down.
    fun local(fx: Float, fy: Float): Offset {
        val rx = fx * cosT - fy * sinT
        val ry = fx * sinT + fy * cosT
        return Offset(cx + rx * len, cy - ry * len)
    }
    drawPath(
        Path().apply {
            val first = local(FISH_OUTLINE[0].first, FISH_OUTLINE[0].second)
            moveTo(first.x, first.y)
            for ((fx, fy) in FISH_OUTLINE.drop(1)) {
                val p = local(fx, fy)
                lineTo(p.x, p.y)
            }
            close()
        },
        MAP_WATER,
    )
    // Islet eye: small ellipse, stretched 1.3x horizontally. The 188-degree rotation is
    // near enough to 180 that an axis-aligned oval passes for the rotated ellipse.
    val eye = local(-0.30f, 0.07f)
    val eyeR = 0.035f * len
    drawOval(
        MAP_BG,
        topLeft = Offset(eye.x - eyeR * 1.3f, eye.y - eyeR),
        size = Size(eyeR * 2.6f, eyeR * 2f),
    )
}

private fun DrawScope.drawConnected(points: List<Offset>, color: Color, widthPx: Float) {
    if (points.size < 2) return
    val path =
        Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
    drawPath(
        path,
        color,
        style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Removes [startPx] from the front and [endPx] from the back of a projected polyline. */
private fun trimEndsPx(points: List<Offset>, startPx: Float, endPx: Float): List<Offset> {
    if (points.size < 2) return points
    var pts = points
    if (startPx > 0f) pts = dropFromStart(pts, startPx)
    if (endPx > 0f && pts.size >= 2) pts = dropFromStart(pts.asReversed(), endPx).asReversed()
    return pts
}

/** Drops [dist] pixels of length from the start of [points], interpolating a new first point. */
private fun dropFromStart(points: List<Offset>, dist: Float): List<Offset> {
    if (points.size < 2 || dist <= 0f) return points
    var remaining = dist
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val seg = hypot(b.x - a.x, b.y - a.y)
        if (seg == 0f) continue
        if (seg >= remaining) {
            val t = remaining / seg
            val moved = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            return listOf(moved) + points.subList(i + 1, points.size)
        }
        remaining -= seg
    }
    return listOf(points.last())
}
