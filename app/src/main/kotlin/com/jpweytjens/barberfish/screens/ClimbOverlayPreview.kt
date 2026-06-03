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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.datatype.shared.ClimbPreviewFixture
import com.jpweytjens.barberfish.datatype.shared.LemonYellow
import com.jpweytjens.barberfish.datatype.shared.buildClimbOverlaySpecs
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.mercatorBoundsAspect
import com.jpweytjens.barberfish.datatype.shared.projectToUnit
import com.jpweytjens.barberfish.datatype.shared.resolveClimbTuning
import com.jpweytjens.barberfish.extension.ClimberMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import kotlin.math.cos
import kotlin.math.sin

// Fixed preview "zoom": every coloured run gets a chevron so the toggle reads clearly.
private const val PREVIEW_CHEVRON_SPACING_M = 80.0

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

        val chevronSize = 6.dp.toPx()
        specs.chevrons.forEach { ch ->
            drawChevron(project(ch.lat, ch.lng), ch.bearingDeg, Color(ch.colorArgb), chevronSize)
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

private fun DrawScope.drawChevron(center: Offset, bearingDeg: Float, color: Color, sizePx: Float) {
    val a = bearingDeg * (Math.PI / 180.0)
    val fx = sin(a).toFloat()   // forward (travel) unit: screen east = +x
    val fy = -cos(a).toFloat()  // screen north = -y
    val px = -fy                // perpendicular unit
    val py = fx
    val tip = Offset(center.x + fx * sizePx, center.y + fy * sizePx)
    val baseX = center.x - fx * sizePx * 0.4f
    val baseY = center.y - fy * sizePx * 0.4f
    val left = Offset(baseX + px * sizePx * 0.8f, baseY + py * sizePx * 0.8f)
    val right = Offset(baseX - px * sizePx * 0.8f, baseY - py * sizePx * 0.8f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, color)
}
