package com.jpweytjens.barberfish.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpweytjens.barberfish.datatype.shared.GradeBand
import com.jpweytjens.barberfish.datatype.shared.bestTextOnBackground
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.extension.GradePalette
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
