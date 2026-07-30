package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineConfigSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `legacy enabled=false migrates to OFF mode`() {
        val legacy = """{"enabled":false,"lookaheadKm":10}"""
        val cfg = json.decodeFromString<SparklineConfig>(legacy)
        assertEquals(SparklineMode.OFF, cfg.hudMode)
        assertEquals(10, cfg.lookaheadKm)
    }

    @Test
    fun `legacy enabled=true migrates to ON mode`() {
        val cfg = json.decodeFromString<SparklineConfig>("""{"enabled":true}""")
        assertEquals(SparklineMode.ON, cfg.hudMode)
    }

    @Test
    fun `explicit mode takes precedence over absent legacy key`() {
        val cfg = json.decodeFromString<SparklineConfig>("""{"mode":"CLIMBS"}""")
        assertEquals(SparklineMode.CLIMBS, cfg.hudMode)
    }

    @Test
    fun `default mode is ON`() {
        assertEquals(SparklineMode.ON, SparklineConfig().hudMode)
    }

    @Test
    fun `encodes mode to the mode wire key`() {
        val encoded =
            json.encodeToString<SparklineConfig>(SparklineConfig(mode = SparklineMode.CLIMBS))
        assertTrue("expected mode wire key", encoded.contains("\"mode\":\"CLIMBS\""))
    }

    // Grade edges, all four key-presence cases. Turbo stops: climbs 3/6/9/12/15,
    // descents -3/-6/-9.

    @Test
    fun `legacy skip counts alone migrate to edges`() {
        val cfg = json.decodeFromString<SparklineConfig>("""{"skipBands":2,"skipBandsDescent":2}""")
        assertEquals(6.0 to -6.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `stored edges alone are used as-is`() {
        val cfg = json.decodeFromString<SparklineConfig>("""{"climbEdge":9.0,"descentEdge":-9.0}""")
        assertEquals(9.0 to -9.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `stored edges win over legacy skip counts`() {
        val both = """{"skipBands":2,"skipBandsDescent":2,"climbEdge":9.0,"descentEdge":-9.0}"""
        val cfg = json.decodeFromString<SparklineConfig>(both)
        assertEquals(9.0 to -9.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `neither key present falls back to the default counts`() {
        val cfg = json.decodeFromString<SparklineConfig>("{}")
        assertEquals(3.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    // A stored count of 0 is the "Off" option in both selectors, and 0 is the shipped default
    // for skipBandsDescent. It has to keep meaning "colour this whole side", not "snap to the
    // flattest stop", or every install on defaults is silently recoloured by the migration.

    @Test
    fun `a stored climb count of zero still colours every climb`() {
        val cfg = json.decodeFromString<SparklineConfig>("""{"skipBands":0}""")
        assertEquals(0.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `a stored descent count of zero still colours every descent`() {
        val cfg = json.decodeFromString<SparklineConfig>("""{"skipBandsDescent":0}""")
        assertEquals(3.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `the grade map's skip count of zero pins both climb and descent edges at zero`() {
        val cfg = json.decodeFromString<GradeMapConfig>("""{"skipBands":0}""")
        assertEquals(0.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `edges survive an encode-decode round trip`() {
        val encoded =
            json.encodeToString<SparklineConfig>(
                SparklineConfig(climbEdge = 12.0, descentEdge = -6.0)
            )
        assertTrue("expected climbEdge wire key", encoded.contains("\"climbEdge\":12.0"))
        assertTrue("expected descentEdge wire key", encoded.contains("\"descentEdge\":-6.0"))
        val cfg = json.decodeFromString<SparklineConfig>(encoded)
        assertEquals(12.0 to -6.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `grade map legacy skip count alone migrates to edges`() {
        val cfg = json.decodeFromString<GradeMapConfig>("""{"skipBands":3}""")
        assertEquals(9.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `grade map stored edges alone are used as-is`() {
        val cfg = json.decodeFromString<GradeMapConfig>("""{"climbEdge":12.0,"descentEdge":-6.0}""")
        assertEquals(12.0 to -6.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `grade map stored edges win over the legacy skip count`() {
        val cfg = json.decodeFromString<GradeMapConfig>("""{"skipBands":3,"climbEdge":12.0}""")
        assertEquals(12.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `grade map with neither key falls back to the default count`() {
        val cfg = json.decodeFromString<GradeMapConfig>("{}")
        assertEquals(3.0 to 0.0, cfg.gradeEdges(GradePalette.TURBO))
    }

    @Test
    fun `a one-sided palette keeps both configs uncoloured on descents`() {
        val spark =
            json.decodeFromString<SparklineConfig>("""{"skipBands":2,"skipBandsDescent":2}""")
        val map = json.decodeFromString<GradeMapConfig>("""{"skipBands":2}""")
        assertEquals(5.0 to null, spark.gradeEdges(GradePalette.KAROO))
        assertEquals(5.0 to null, map.gradeEdges(GradePalette.KAROO))
    }
}
