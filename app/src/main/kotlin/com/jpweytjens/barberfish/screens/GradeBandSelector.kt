package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpweytjens.barberfish.datatype.shared.GradeBand
import com.jpweytjens.barberfish.datatype.shared.Grey200
import com.jpweytjens.barberfish.datatype.shared.Grey400
import com.jpweytjens.barberfish.datatype.shared.TextDark
import com.jpweytjens.barberfish.datatype.shared.bestTextOnBackground
import com.jpweytjens.barberfish.datatype.shared.gradeBandColor
import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.gradeFloor
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

/** One paintable run of the merged bar: contiguous cells resolving to one colour. Null = groove. */
internal data class BarRun(val weight: Float, val color: Color?)

/**
 * Cells resolved through [gradeBandColor] and merged into runs. A null [neutral] marks filtered
 * cells as groove runs (the surface paints nothing there); adjacent same-colour cells merge, so the
 * filtered middle is always a single run.
 */
internal fun barRuns(
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
    neutral: Color?,
): List<BarRun> {
    val cells = gradeCells(gradeBands(palette, readable = false))
    val runs = mutableListOf<BarRun>()
    cells.forEach { cell ->
        val resolved =
            gradeBandColor(
                grade = (cell.lo + cell.hi) / 2.0,
                palette = palette,
                climbEdge = climbEdge,
                descentEdge = descentEdge,
                neutral = neutral ?: Color.Unspecified,
                readable = false,
            )
        val color = resolved.takeIf { it != Color.Unspecified }
        val last = runs.lastOrNull()
        if (last != null && last.color == color) {
            runs[runs.size - 1] = BarRun(last.weight + cell.weight, color)
        } else {
            runs.add(BarRun(cell.weight, color))
        }
    }
    return runs
}

// An edge parked past every stop, so that side colours nothing. Double.MAX_VALUE rather than
// POSITIVE_INFINITY because the edge is persisted and JSON has no infinity literal.
internal const val GRADE_EDGE_OFF = Double.MAX_VALUE

/** One snap position: where the thumb sits on the axis and the edge selecting it stores. */
internal data class EdgeStop(val axisGrade: Double, val edge: Double)

// The palette's true zero boundary, if it has one: a band starting at 0 (Turbo's flattest
// climb band) or a floor at 0 (the one-sided palettes). There an edge of 0.0 means no
// filtering on that side, so the handle gets a stop for it. A band merely straddling zero
// (Barberfish's flat band) has no zero boundary: it is the neutral, never a highlight.
private fun hasClimbZeroStop(palette: GradePalette): Boolean =
    gradeBands(palette, readable = false).any { it.lo == 0.0 } ||
        gradeFloor(palette, readable = false) == 0.0

private fun hasDescentZeroStop(palette: GradePalette): Boolean =
    gradeBands(palette, readable = false).any { band ->
        band.hi == 0.0 && (band.lo ?: Double.NEGATIVE_INFINITY) < 0.0
    }

// The climb slider's positions: fully-on at 0 where the palette has a real zero edge, then
// the palette's climb stops, then Off at the axis end.
internal fun climbEdgeStops(palette: GradePalette): List<EdgeStop> =
    (if (hasClimbZeroStop(palette)) listOf(EdgeStop(0.0, 0.0)) else emptyList()) +
        gradeBandStops(palette).climb.map { EdgeStop(it, it) } +
        EdgeStop(GRADE_AXIS_MAX, GRADE_EDGE_OFF)

// The descent slider's positions: Off at the axis end, the palette's descent stops, then
// fully-on at 0 where the palette has a real zero edge.
internal fun descentEdgeStops(palette: GradePalette): List<EdgeStop> =
    listOf(EdgeStop(GRADE_AXIS_MIN, -GRADE_EDGE_OFF)) +
        gradeBandStops(palette).descent.sorted().map { EdgeStop(it, it) } +
        (if (hasDescentZeroStop(palette)) listOf(EdgeStop(0.0, 0.0)) else emptyList())

// The position a stored edge lands on: nearest stop by axis distance, so a stale edge (a
// retired stop, a parked sentinel) snaps rather than strands the thumb. A null edge means
// that side colours nothing, which is the Off position.
internal fun nearestEdgeStop(stops: List<EdgeStop>, edge: Double?): EdgeStop {
    if (edge == null) return stops.first { abs(it.edge) == GRADE_EDGE_OFF }
    val clamped = edge.coerceIn(GRADE_AXIS_MIN, GRADE_AXIS_MAX)
    return stops.minBy { abs(it.axisGrade - clamped) }
}

/**
 * The merged emphasis instrument: the band bar is the slider. Handles sit on the bar, snap to the
 * palette's stops, and park at the end caps for Off. [neutral] is what the surface being configured
 * paints inside the edges; null means it paints nothing (the Profile), rendered as an outlined
 * groove. [enabled] false draws no handles and attaches no gesture: pure display for the map card's
 * Sync branch. One-sided palettes have no descent handle and pass [descentEdge] through
 * [onEdgesChange] unchanged. [ground] is the card body behind the bar; the handle halo reads from
 * it.
 */
@Composable
internal fun GradeBandSlider(
    palette: GradePalette,
    climbEdge: Double?,
    descentEdge: Double?,
    onEdgesChange: (climbEdge: Double, descentEdge: Double?) -> Unit,
    neutral: Color?,
    enabled: Boolean = true,
    ground: Color = Grey200,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowHeightDp = 48.dp
    val barHeightDp = 24.dp
    val capRadiusDp = 12.dp
    val handleWidthDp = 4.dp
    val handleHeightDp = 40.dp
    val haloDp = 2.dp
    val grooveStrokeDp = 1.dp
    val chevronSizeDp = 8.dp
    val chevronGapDp = 14.dp

    val bands = gradeBands(palette, readable = false)
    val cells = gradeCells(bands)
    val climbStops = climbEdgeStops(palette)
    val descentStops = descentEdgeStops(palette).takeIf { it.size > 1 }
    val climbSel = nearestEdgeStop(climbStops, climbEdge)
    val descentSel = descentStops?.let { nearestEdgeStop(it, descentEdge) }
    val coincident = descentSel != null && descentSel.axisGrade == climbSel.axisGrade
    val runs = barRuns(palette, climbSel.edge, descentSel?.edge, neutral)

    // The gesture handler is keyed on the palette only, so a drag survives the recompositions
    // its own updates cause; these keep its captures current.
    val currentClimbSel by rememberUpdatedState(climbSel)
    val currentDescentSel by rememberUpdatedState(descentSel)
    val currentClimbEdge by rememberUpdatedState(climbEdge)
    val currentDescentEdge by rememberUpdatedState(descentEdge)
    val currentOnEdgesChange by rememberUpdatedState(onEdgesChange)

    val gesture =
        if (!enabled) Modifier
        else
            Modifier.pointerInput(palette) {
                val widthPx = size.width.toFloat()
                fun gradeAt(x: Float) =
                    GRADE_AXIS_MIN + (x / widthPx) * (GRADE_AXIS_MAX - GRADE_AXIS_MIN)
                fun select(onDescentSide: Boolean, x: Float) {
                    val grade = gradeAt(x)
                    if (onDescentSide) {
                        val stops = descentStops ?: return
                        val hit = stops.minBy { abs(it.axisGrade - grade) }
                        if (hit.edge != currentDescentSel?.edge) {
                            // The climb side didn't move: report its raw incoming edge
                            // unchanged, not the snapped display value, so a caller can tell
                            // "unmoved" from "moved to a value that happens to match a stop".
                            currentOnEdgesChange(currentClimbEdge ?: currentClimbSel.edge, hit.edge)
                        }
                    } else {
                        val hit = climbStops.minBy { abs(it.axisGrade - grade) }
                        if (hit.edge != currentClimbSel.edge) {
                            // Same for the descent side here: pass its raw edge through.
                            currentOnEdgesChange(hit.edge, currentDescentEdge)
                        }
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val grade = gradeAt(down.position.x)
                    // Coincident handles (both at the shared 0 stop) make a press that would
                    // snap both sides to that stop ambiguous: it cannot name its handle, so
                    // the first horizontal movement does. Any other press locks its side by
                    // sign at the press, so a drag across the flat gap keeps adjusting the
                    // handle it grabbed.
                    val descSel = currentDescentSel
                    val ambiguous =
                        descSel != null &&
                            descSel.axisGrade == currentClimbSel.axisGrade &&
                            climbStops.minBy { abs(it.axisGrade - grade) }.axisGrade ==
                                currentClimbSel.axisGrade &&
                            descentStops?.minBy { abs(it.axisGrade - grade) }?.axisGrade ==
                                currentClimbSel.axisGrade
                    var onDescentSide: Boolean? =
                        if (ambiguous) null else descentStops != null && grade < 0.0
                    onDescentSide?.let { select(it, down.position.x) }
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val change = event.changes.firstOrNull() ?: break
                        change.consume()
                        if (onDescentSide == null) {
                            val dx = change.position.x - down.position.x
                            if (abs(dx) > viewConfiguration.touchSlop) onDescentSide = dx < 0f
                        }
                        onDescentSide?.let { select(it, change.position.x) }
                        event = awaitPointerEvent()
                    }
                }
            }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(rowHeightDp).then(gesture)) {
            val widthPx = constraints.maxWidth.toFloat()
            fun xOf(grade: Double) = axisFraction(grade) * widthPx

            // The bar: a lead spacer for one-sided palettes, then the runs, clipped to the
            // rounded caps. A groove run draws its outline with a shape matching its position
            // so the hairline follows the cap curve at a terminal run.
            val lead = (cells.first().lo - GRADE_AXIS_MIN).toFloat()
            Row(
                modifier = Modifier.fillMaxWidth().height(barHeightDp).align(Alignment.CenterStart)
            ) {
                if (lead > 0f) Spacer(modifier = Modifier.weight(lead))
                Row(
                    modifier =
                        Modifier.weight(cells.sumOf { it.weight.toDouble() }.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(capRadiusDp))
                ) {
                    runs.forEachIndexed { i, run ->
                        val start = if (i == 0) capRadiusDp else 0.dp
                        val end = if (i == runs.lastIndex) capRadiusDp else 0.dp
                        val shape =
                            RoundedCornerShape(
                                topStart = start,
                                bottomStart = start,
                                topEnd = end,
                                bottomEnd = end,
                            )
                        Box(
                            modifier =
                                Modifier.weight(run.weight).fillMaxHeight().let { m ->
                                    val color = run.color
                                    if (color != null) m.background(color)
                                    else m.border(grooveStrokeDp, Grey400, shape)
                                }
                        )
                    }
                }
            }

            // A handle centred at [cx]: a ground-colour halo ring under a TextDark bar, so the
            // handle separates from every band colour without cutting the band. This replaces
            // Material's track gap.
            @Composable
            fun Handle(cx: Float) {
                val haloW = handleWidthDp + haloDp * 2
                val haloH = handleHeightDp + haloDp * 2
                val haloWPx = with(density) { haloW.toPx() }
                val handleWPx = with(density) { handleWidthDp.toPx() }
                Box(
                    modifier =
                        Modifier.width(haloW)
                            .height(haloH)
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((cx - haloWPx / 2).toInt(), 0) }
                            .clip(RoundedCornerShape(50))
                            .background(ground)
                )
                Box(
                    modifier =
                        Modifier.width(handleWidthDp)
                            .height(handleHeightDp)
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((cx - handleWPx / 2).toInt(), 0) }
                            .clip(RoundedCornerShape(50))
                            .background(TextDark)
                )
            }

            // A small triangle beside a coincident handle: the cue that two directions live
            // there. [direction] -1 points left (descent), +1 points right (climb).
            @Composable
            fun Chevron(cx: Float, direction: Int) {
                val sizePx = with(density) { chevronSizeDp.toPx() }
                val gapPx = with(density) { chevronGapDp.toPx() }
                Canvas(
                    modifier =
                        Modifier.size(chevronSizeDp).align(Alignment.CenterStart).offset {
                            IntOffset((cx + direction * gapPx - sizePx / 2).toInt(), 0)
                        }
                ) {
                    val path =
                        Path().apply {
                            if (direction < 0) {
                                moveTo(size.width, 0f)
                                lineTo(0f, size.height / 2)
                                lineTo(size.width, size.height)
                            } else {
                                moveTo(0f, 0f)
                                lineTo(size.width, size.height / 2)
                                lineTo(0f, size.height)
                            }
                            close()
                        }
                    drawPath(path, TextDark)
                }
            }

            if (enabled) {
                if (coincident) {
                    Chevron(cx = xOf(climbSel.axisGrade), direction = -1)
                    Chevron(cx = xOf(climbSel.axisGrade), direction = +1)
                }
                if (descentSel != null && !coincident) Handle(cx = xOf(descentSel.axisGrade))
                Handle(cx = xOf(climbSel.axisGrade))
            }
        }
        GradeTickAxis(stops = gradeTickStops(bands))
    }
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
