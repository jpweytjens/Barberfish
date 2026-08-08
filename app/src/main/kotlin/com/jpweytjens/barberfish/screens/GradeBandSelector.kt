package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpweytjens.barberfish.datatype.shared.GradeBand
import com.jpweytjens.barberfish.datatype.shared.Grey400
import com.jpweytjens.barberfish.datatype.shared.TextDark
import com.jpweytjens.barberfish.datatype.shared.bestTextOnBackground
import com.jpweytjens.barberfish.datatype.shared.gradeBandColor
import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.extension.GradePalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// The clamped axis every proportional grade visualization shares.
// Terminal open bands run to these edges; see the 2026-08-01 spec, Decision 2.
internal const val GRADE_AXIS_MIN = -15.0
internal const val GRADE_AXIS_MAX = 25.0

/** One renderable cell: a band clamped to the axis, with its exemplar reading. */
internal data class GradeCell(
    val lo: Double,
    val hi: Double,
    val exemplar: String,
    val band: GradeBand,
) {
    val weight: Float
        get() = (hi - lo).toFloat()
}

/**
 * Bands clamped to the axis. A palette with no descent bands starts at 0 rather than the clamp, so
 * missing descent coverage reads as absence.
 */
internal fun gradeCells(bands: List<GradeBand>): List<GradeCell> {
    val hasDescent = bands.any { (it.hi ?: 1.0) <= 0.0 }
    val floor = if (hasDescent) GRADE_AXIS_MIN else 0.0
    return bands.mapNotNull { band ->
        val lo = maxOf(band.lo ?: floor, floor)
        val hi = minOf(band.hi ?: GRADE_AXIS_MAX, GRADE_AXIS_MAX)
        if (hi <= lo) return@mapNotNull null
        GradeCell(lo = lo, hi = hi, exemplar = exemplarLabel((lo + hi) / 2.0), band = band)
    }
}

/** Real thresholds for the tick axis: every band edge, never the clamp. */
internal fun gradeTickStops(bands: List<GradeBand>): List<Double> {
    val cells = gradeCells(bands)
    val inner = cells.drop(1).map { it.lo }
    val lead = cells.first().lo.takeIf { it >= 0.0 }
    return (listOfNotNull(lead) + inner)
}

/** Fraction of the axis at [grade], for tick placement. */
internal fun axisFraction(grade: Double): Float =
    ((grade - GRADE_AXIS_MIN) / (GRADE_AXIS_MAX - GRADE_AXIS_MIN)).toFloat()

/** Round half away from zero, so -12.5 reads -13 and 22.5 reads 23. */
private fun exemplarLabel(mid: Double): String {
    val rounded = if (mid >= 0.0) floor(mid + 0.5) else ceil(mid - 0.5)
    return rounded.toInt().toString()
}

/**
 * The A1 proportional palette preview: a text-mode row, a fill-mode row, and a tick axis carrying
 * the band edges. Cell widths are proportional to grade span on the shared clamped axis; each cell
 * shows an exemplar reading, hidden when the cell is too narrow for it.
 */
@Composable
internal fun ProportionalGradePreview(palette: GradePalette) {
    val isNightMode = isSystemInDarkTheme()
    val textRowBg = if (isNightMode) Color.Black else Color.White
    val textCells = gradeCells(gradeBands(palette, readable = true, isNightMode = isNightMode))
    val fillCells = gradeCells(gradeBands(palette, readable = false))
    Column(modifier = Modifier.fillMaxWidth()) {
        PreviewStrip(cells = textCells, cellBg = { textRowBg }, cellText = { it.band.color })
        PreviewStrip(
            cells = fillCells,
            cellBg = { it.band.color },
            cellText = { bestTextOnBackground(it.band.color) },
        )
        GradeTickAxis(stops = gradeTickStops(gradeBands(palette, readable = false)))
    }
}

/**
 * The proportional band bar: every band of [palette] on the shared clamped axis, painted as the
 * surface being configured will paint it, so bands outside the edges show that surface's [neutral].
 */
@Composable
internal fun GradeBandBar(
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
    neutral: Color,
    modifier: Modifier = Modifier,
) {
    val bands = gradeBands(palette, readable = false)
    val cells = gradeCells(bands)
    Column(modifier = modifier.fillMaxWidth()) {
        val lead = (cells.first().lo - GRADE_AXIS_MIN).toFloat()
        Row(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            if (lead > 0f) Spacer(modifier = Modifier.weight(lead))
            cells.forEach { cell ->
                val mid = (cell.lo + cell.hi) / 2.0
                Box(
                    modifier =
                        Modifier.weight(cell.weight)
                            .fillMaxHeight()
                            .background(
                                gradeBandColor(
                                    grade = mid,
                                    palette = palette,
                                    climbEdge = climbEdge,
                                    descentEdge = descentEdge,
                                    neutral = neutral,
                                    readable = false,
                                )
                            )
                )
            }
        }
        GradeTickAxis(stops = gradeTickStops(bands))
    }
}

// An edge parked past every stop, so that side colours nothing. Double.MAX_VALUE rather than
// POSITIVE_INFINITY because the edge is persisted and JSON has no infinity literal.
internal const val GRADE_EDGE_OFF = Double.MAX_VALUE

/** One snap position: where the thumb sits on the axis and the edge selecting it stores. */
private data class EdgeStop(val axisGrade: Double, val edge: Double)

// The climb slider's positions: the palette's climb stops, then Off at the axis end.
private fun climbEdgeStops(palette: GradePalette): List<EdgeStop> =
    gradeBandStops(palette).climb.map { EdgeStop(it, it) } +
        EdgeStop(GRADE_AXIS_MAX, GRADE_EDGE_OFF)

// The descent slider's positions: Off at the axis end, then the palette's descent stops.
private fun descentEdgeStops(palette: GradePalette): List<EdgeStop> =
    listOf(EdgeStop(GRADE_AXIS_MIN, -GRADE_EDGE_OFF)) +
        gradeBandStops(palette).descent.sorted().map { EdgeStop(it, it) }

// The position a stored edge lands on: nearest stop by axis distance, so a stale edge (a
// retired stop, a legacy 0.0, a parked sentinel) snaps rather than strands the thumb. A null
// edge means that side colours nothing, which is the Off position.
private fun nearestEdgeStop(stops: List<EdgeStop>, edge: Double?): EdgeStop {
    if (edge == null) return stops.first { abs(it.edge) == GRADE_EDGE_OFF }
    val clamped = edge.coerceIn(GRADE_AXIS_MIN, GRADE_AXIS_MAX)
    return stops.minBy { abs(it.axisGrade - clamped) }
}

/**
 * Two single-thumb sliders on the shared clamped axis: descent over -15..-2, climb over +2..+25,
 * the flat band between them a gap with no control. Each track fills from its extreme end inward
 * toward the thumb, so filled always means coloured and dragging toward flat includes more bands.
 * Thumbs snap to [palette]'s stops; the far end of a track is that side's Off. One-sided palettes
 * hide the descent slider and pass [descentEdge] through [onEdgesChange] unchanged.
 */
@Composable
internal fun GradeEdgeSliders(
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
    onEdgesChange: (climbEdge: Double, descentEdge: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumbSizeDp = 40.dp
    val dotSizeDp = 10.dp
    val trackHeightDp = 18.dp
    val climbStops = climbEdgeStops(palette)
    val descentStops = descentEdgeStops(palette).takeIf { it.size > 1 }
    val climbSel = nearestEdgeStop(climbStops, climbEdge)
    val descentSel = descentStops?.let { nearestEdgeStop(it, descentEdge) }

    // The gesture handler is keyed on the palette only, so a drag survives the recompositions
    // its own updates cause; these keep its captures current.
    val currentClimbSel by rememberUpdatedState(climbSel)
    val currentDescentSel by rememberUpdatedState(descentSel)
    val currentDescentEdge by rememberUpdatedState(descentEdge)
    val currentOnEdgesChange by rememberUpdatedState(onEdgesChange)

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxWidth().height(thumbSizeDp).pointerInput(palette) {
                    val widthPx = size.width.toFloat()
                    fun gradeAt(x: Float) =
                        GRADE_AXIS_MIN + (x / widthPx) * (GRADE_AXIS_MAX - GRADE_AXIS_MIN)
                    fun select(onDescentSide: Boolean, x: Float) {
                        val grade = gradeAt(x)
                        if (onDescentSide) {
                            val stops = descentStops ?: return
                            val hit = stops.minBy { abs(it.axisGrade - grade) }
                            if (hit.edge != currentDescentSel?.edge) {
                                currentOnEdgesChange(currentClimbSel.edge, hit.edge)
                            }
                        } else {
                            val hit = climbStops.minBy { abs(it.axisGrade - grade) }
                            if (hit.edge != currentClimbSel.edge) {
                                currentOnEdgesChange(
                                    hit.edge,
                                    currentDescentSel?.edge ?: currentDescentEdge,
                                )
                            }
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // The side is locked at the press, so a drag across the flat gap keeps
                        // adjusting the thumb it grabbed.
                        val onDescentSide = descentStops != null && gradeAt(down.position.x) < 0.0
                        select(onDescentSide, down.position.x)
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            select(onDescentSide, change.position.x)
                            event = awaitPointerEvent()
                        }
                    }
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val thumbSizePx = with(density) { thumbSizeDp.toPx() }
            val dotSizePx = with(density) { dotSizeDp.toPx() }
            fun xOf(grade: Double) = axisFraction(grade) * widthPx

            @Composable
            fun TrackPill(fromX: Float, toX: Float, color: Color) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .offset { IntOffset(fromX.toInt(), 0) }
                            .width(with(density) { (toX - fromX).toDp() })
                            .height(trackHeightDp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                )
            }

            @Composable
            fun Thumb(sel: EdgeStop) {
                val cx = xOf(sel.axisGrade).coerceIn(thumbSizePx / 2, widthPx - thumbSizePx / 2)
                Box(
                    modifier =
                        Modifier.size(thumbSizeDp)
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((cx - thumbSizePx / 2).toInt(), 0) }
                            .clip(CircleShape)
                            .background(Grey400)
                )
            }

            // White pill per side, dots at the snap positions, then the inward fill over the
            // dots it has passed: a covered dot reads as an included band.
            if (descentStops != null) {
                TrackPill(fromX = xOf(GRADE_AXIS_MIN), toX = xOf(-2.0), color = Color.White)
            }
            TrackPill(fromX = xOf(2.0), toX = xOf(GRADE_AXIS_MAX), color = Color.White)
            (climbStops + descentStops.orEmpty()).forEach { stop ->
                val cx = xOf(stop.axisGrade).coerceIn(dotSizePx / 2, widthPx - dotSizePx / 2)
                Box(
                    modifier =
                        Modifier.size(dotSizeDp)
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((cx - dotSizePx / 2).toInt(), 0) }
                            .clip(CircleShape)
                            .background(Grey400)
                )
            }
            if (descentSel != null && abs(descentSel.edge) != GRADE_EDGE_OFF) {
                TrackPill(
                    fromX = xOf(GRADE_AXIS_MIN),
                    toX = xOf(descentSel.axisGrade),
                    color = Grey400,
                )
            }
            if (abs(climbSel.edge) != GRADE_EDGE_OFF) {
                TrackPill(
                    fromX = xOf(climbSel.axisGrade),
                    toX = xOf(GRADE_AXIS_MAX),
                    color = Grey400,
                )
            }
            if (descentSel != null) Thumb(descentSel)
            Thumb(climbSel)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            if (descentSel != null) {
                EdgeLabel(sel = descentSel, prefix = "<=")
            }
            Spacer(modifier = Modifier.weight(1f))
            EdgeLabel(sel = climbSel, prefix = ">=")
        }
    }
}

@Composable
private fun EdgeLabel(sel: EdgeStop, prefix: String) {
    val text =
        if (abs(sel.edge) == GRADE_EDGE_OFF) "Off" else "$prefix ${formatGradePct(sel.edge)}%"
    Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
}

@Composable
private fun PreviewStrip(
    cells: List<GradeCell>,
    cellBg: (GradeCell) -> Color,
    cellText: (GradeCell) -> Color,
) {
    // Uncovered range left of a one-sided palette stays card background: absence
    // of coverage reads as absence.
    val lead = (cells.first().lo - GRADE_AXIS_MIN).toFloat()
    Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        if (lead > 0f) Spacer(modifier = Modifier.weight(lead))
        cells.forEach { cell ->
            Box(
                modifier = Modifier.weight(cell.weight).fillMaxHeight().background(cellBg(cell)),
                contentAlignment = Alignment.Center,
            ) {
                CellLabel(text = cell.exemplar, color = cellText(cell))
            }
        }
    }
}

// Shows [text] only when it fits the cell with 4 dp to spare; the tick axis
// still carries the edges, so a hidden exemplar loses nothing essential.
@Composable
private fun CellLabel(text: String, color: Color) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
    BoxWithConstraints {
        val textWidth =
            with(LocalDensity.current) {
                measurer.measure(text, style).size.width.toDp()
            }
        if (textWidth <= maxWidth - 4.dp) {
            Text(text = text, style = style, color = color)
        }
    }
}

@Composable
private fun GradeTickAxis(stops: List<Double>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(18.dp)) {
        stops.forEach { stop ->
            Box(
                modifier =
                    Modifier.offset(x = maxWidth * axisFraction(stop)).width(0.dp).fillMaxHeight(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                ) {
                    Box(
                        Modifier.width(1.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        text = formatGradePct(stop),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
