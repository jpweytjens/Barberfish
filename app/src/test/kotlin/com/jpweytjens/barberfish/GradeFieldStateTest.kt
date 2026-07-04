package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.GradeReading
import com.jpweytjens.barberfish.extension.GradeFieldConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneDisplayMode
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
        assertEquals("Searching…", s.primary)
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

    @Test fun fresh_integerPrecision_roundsAndKeepsPercent() {
        val cfg = GradeFieldConfig(ZoneColorMode.TEXT, ZoneDisplayMode.INTEGER, showPercentSign = true)
        val s = state(GradeReading.Fresh(7.4f), cfg)
        assertEquals("7%", s.primary)
    }

    @Test fun fresh_decimalPrecision_noPercentSign() {
        val cfg = GradeFieldConfig(ZoneColorMode.TEXT, ZoneDisplayMode.FLOAT, showPercentSign = false)
        val s = state(GradeReading.Fresh(7.0f), cfg)
        assertTrue("expected no % sign, got '${s.primary}'", s.primary.matches(Regex("7[.,]0")))
    }

    @Test fun stale_integerNoPercent_holdsRoundedValue() {
        val cfg = GradeFieldConfig(ZoneColorMode.TEXT, ZoneDisplayMode.INTEGER, showPercentSign = false)
        val s = state(GradeReading.Stale(7.6f), cfg)
        assertEquals("8", s.primary)
    }

    // Exact .5 ties round HALF_UP (nearest, ties away from zero) via java.util.Formatter.
    // 6.5 / -6.5 / 6.25 are all exactly representable as floats, so the tie is genuine.
    @Test fun fresh_integerHalfUp_roundsTieAwayFromZero() {
        val cfg = GradeFieldConfig(ZoneColorMode.TEXT, ZoneDisplayMode.INTEGER, showPercentSign = true)
        assertEquals("7%", state(GradeReading.Fresh(6.5f), cfg).primary)
        // Descents round symmetrically, away from zero.
        assertEquals("-7%", state(GradeReading.Fresh(-6.5f), cfg).primary)
    }

    @Test fun fresh_decimalHalfUp_roundsTieUp() {
        val cfg = GradeFieldConfig(ZoneColorMode.TEXT, ZoneDisplayMode.FLOAT, showPercentSign = true)
        val s = state(GradeReading.Fresh(6.25f), cfg)
        assertTrue("expected 6.3%, got '${s.primary}'", s.primary.matches(Regex("6[.,]3%")))
    }
}
