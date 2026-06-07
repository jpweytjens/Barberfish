package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.GradeReading
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the GradeReading -> displayed FieldState mapping, closing the loop between the
 * scan reducer (GradeReadingScanTest) and what the user sees. Value strings are matched
 * separator-agnostically so the test holds regardless of the JVM's default locale.
 */
class GradeFieldStateTest {

    private val cfg = GradeFieldConfig(ZoneColorMode.TEXT)
    private val palette = GradePalette.KAROO

    private fun state(reading: GradeReading, c: GradeFieldConfig = cfg) =
        GradeField.toGradeFieldState(reading, c, palette)

    @Test fun unavailable_showsSearching() {
        // Cold start and the first 30 m of warm-up: no trusted value yet.
        val s = state(GradeReading.Unavailable)
        assertEquals("Searching...", s.primary)
        assertEquals(FieldColor.StreamState, s.color)
        assertEquals("Grade", s.label)
    }

    @Test fun stale_holdsLastValueInGrey() {
        // Pause / stop / stream gap after a trusted value: grey, holding the last reading.
        val s = state(GradeReading.Stale(7.0f))
        assertTrue("expected a value, got '${s.primary}'", s.primary.matches(Regex("7[.,]0%")))
        assertEquals(FieldColor.Muted, s.color)
        assertEquals("Grade", s.label)
    }

    @Test fun fresh_showsValueWithGradeColor() {
        // Moving with >= 30 m accumulated: current value, grade-coloured.
        val s = state(GradeReading.Fresh(7.0f))
        assertTrue("expected a value, got '${s.primary}'", s.primary.matches(Regex("7[.,]0%")))
        assertTrue(s.color is FieldColor.Grade)
        assertEquals(ZoneColorMode.TEXT, s.colorMode)
    }

    @Test fun fresh_withColorModeNone_usesDefaultColor() {
        val s = state(GradeReading.Fresh(7.0f), GradeFieldConfig(ZoneColorMode.NONE))
        assertEquals(FieldColor.Default, s.color)
    }
}
