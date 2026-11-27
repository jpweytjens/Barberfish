package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradeMapWidth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapWidthTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a stored config without width reads as medium`() {
        val stored = """{"enabled":true,"chevronBlend":1.0}"""
        assertEquals(GradeMapWidth.MEDIUM, json.decodeFromString<GradeMapConfig>(stored).width)
    }

    @Test
    fun `medium keeps the band as it was before the setting existed`() {
        assertEquals(18, GradeMapWidth.MEDIUM.fillDp)
        assertEquals(21, GradeMapWidth.MEDIUM.casingDp)
    }

    @Test
    fun `only narrow drops the chevrons`() {
        assertEquals(
            listOf(GradeMapWidth.NARROW),
            GradeMapWidth.entries.filterNot { it.drawsChevrons },
        )
    }

    @Test
    fun `narrow has no casing and every other casing is wider than its fill`() {
        assertEquals(0, GradeMapWidth.NARROW.casingDp)
        (GradeMapWidth.entries - GradeMapWidth.NARROW).forEach {
            assertTrue(it.name, it.casingDp > it.fillDp)
        }
    }

    @Test
    fun `the rejoin casing takes medium's margin at narrow and the width's own elsewhere`() {
        assertEquals(10, GradeMapWidth.NARROW.rejoinCasingDp)
        assertEquals(21, GradeMapWidth.MEDIUM.rejoinCasingDp)
        assertEquals(28, GradeMapWidth.WIDE.rejoinCasingDp)
    }
}
