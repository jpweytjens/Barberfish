package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneConfigSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `beta SURGEONFISH palettes decode as Barberfish`() {
        val beta =
            """{"hrPalette":"SURGEONFISH","powerPalette":"WAHOO","gradePalette":"SURGEONFISH"}"""
        assertEquals(
            ZoneConfig(
                hrPalette = ZonePalette.BARBERFISH,
                powerPalette = ZonePalette.WAHOO,
                gradePalette = GradePalette.BARBERFISH,
            ),
            json.decodeFromString<ZoneConfig>(beta),
        )
    }
}
