package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jpweytjens.barberfish.datatype.shared.FlatGrey
import com.jpweytjens.barberfish.datatype.shared.gradeBands
import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import com.jpweytjens.barberfish.extension.GradePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChevronDrawablesTest {

    // Every colour the map overlay can paint a run with: the palette's own bands under each
    // readable/night combination, plus the neutral buildGradeMapSpecs passes for a segment
    // inside the edges. A chevron sitting on that run is drawn in the same colour, so each
    // one needs its own pre-baked drawable.
    private fun paintableColors(palette: GradePalette): Set<Color> = buildSet {
        add(FlatGrey)
        listOf(false, true).forEach { readable ->
            listOf(false, true).forEach { isNightMode ->
                gradeBands(palette, readable, isNightMode).forEach { add(it.color) }
            }
        }
    }

    @Test
    fun every_paintable_colour_has_its_own_chevron_drawable() {
        GradePalette.entries.forEach { palette ->
            paintableColors(palette).forEach { color ->
                assertNotEquals(
                    "$palette: #${Integer.toHexString(color.toArgb())} falls back to the grey chevron",
                    R.drawable.ic_climber_chevron,
                    gradeChevronDrawable(color.toArgb()),
                )
            }
        }
    }

    @Test
    fun distinct_paintable_colours_get_distinct_chevron_drawables() {
        val colors = GradePalette.entries.flatMap { paintableColors(it) }.toSet()
        val drawables = colors.map { gradeChevronDrawable(it.toArgb()) }
        assertEquals(
            "each colour must resolve to its own drawable",
            colors.size,
            drawables.toSet().size,
        )
    }
}
