package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.toFieldConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldSparklineConfigTest {

    @Test
    fun `unset field config falls back to default options forced ON`() {
        assertEquals(SparklineConfig(mode = SparklineMode.ON), null.toFieldConfig())
    }

    @Test
    fun `unset field config is ON`() {
        assertEquals(SparklineMode.ON, null.toFieldConfig().hudMode)
    }

    @Test
    fun `CLIMBS mode is coerced to ON`() {
        assertEquals(SparklineMode.ON, SparklineConfig(mode = SparklineMode.CLIMBS).toFieldConfig().hudMode)
    }

    @Test
    fun `OFF mode is coerced to ON`() {
        assertEquals(SparklineMode.ON, SparklineConfig(mode = SparklineMode.OFF).toFieldConfig().hudMode)
    }

    @Test
    fun `coercing mode preserves the other options`() {
        val stored = SparklineConfig(mode = SparklineMode.CLIMBS, lookaheadKm = 10, showClimbs = false)
        val field = stored.toFieldConfig()
        assertEquals(10, field.lookaheadKm)
        assertEquals(false, field.showClimbs)
        assertEquals(SparklineMode.ON, field.hudMode)
    }
}
