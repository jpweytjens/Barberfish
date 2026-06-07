package com.jpweytjens.barberfish

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
}
