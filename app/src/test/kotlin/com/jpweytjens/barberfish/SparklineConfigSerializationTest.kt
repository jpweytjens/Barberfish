package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.SparklineConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineConfigSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `decodes legacy enabled wire key into hudEnabled`() {
        val legacy = """{"enabled":false,"lookaheadKm":10}"""
        val cfg = json.decodeFromString<SparklineConfig>(legacy)
        assertFalse(cfg.hudEnabled)
        assertEquals(10, cfg.lookaheadKm)
    }

    @Test
    fun `encodes hudEnabled back to the enabled wire key`() {
        val encoded = json.encodeToString<SparklineConfig>(SparklineConfig(hudEnabled = false))
        assertTrue("expected legacy wire key", encoded.contains("\"enabled\":false"))
        assertFalse("must not leak the new symbol name", encoded.contains("hudEnabled"))
    }

    @Test
    fun `default hudEnabled is true`() {
        assertTrue(SparklineConfig().hudEnabled)
    }
}
