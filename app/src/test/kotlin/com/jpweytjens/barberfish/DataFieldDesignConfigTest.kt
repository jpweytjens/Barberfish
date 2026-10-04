package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.LabelSize
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataFieldDesignConfigTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `defaults are icons on and Small`() {
        val cfg = DataFieldDesignConfig()
        assertTrue(cfg.showIcons)
        assertEquals(LabelSize.SMALL, cfg.labelSize)
    }

    @Test
    fun `round-trips non-default values`() {
        val cfg = DataFieldDesignConfig(showIcons = false, labelSize = LabelSize.LARGE)
        val decoded = json.decodeFromString<DataFieldDesignConfig>(json.encodeToString(cfg))
        assertEquals(cfg, decoded)
    }

    @Test
    fun `tolerates unknown keys from future versions`() {
        val decoded =
            json.decodeFromString<DataFieldDesignConfig>(
                """{"showIcons":false,"labelSize":"LARGE","futureKey":42}"""
            )
        assertEquals(false, decoded.showIcons)
        assertEquals(LabelSize.LARGE, decoded.labelSize)
    }
}
