package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDScope
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.saveFieldSparklineConfig
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamConfigSnapshot
import com.jpweytjens.barberfish.extension.streamFieldSparklineConfig
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.updateHUDConfig
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The DataStore-backed settings streams and their snapshot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {
    @get:Rule val karoo = FakeKarooRule()

    @Before
    fun clean() = clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun the_config_snapshot_reflects_a_written_key() {
        val app = karoo.app
        runBlocking {
            app.updateHUDConfig(HUDScope.Shared) {
                HUDConfig(
                    slots = HUDSlots(columns = 4, leftSlot = HUDSlotConfig(HUDSlotField.Distance))
                )
            }
        }
        val snapshot = runBlocking { app.streamConfigSnapshot().first() }
        assertEquals(4, snapshot.hud.columns)
        assertEquals(HUDSlotField.Distance, snapshot.hud.leftSlot.field)
    }

    @Test
    fun the_field_sparkline_forces_its_mode_on() {
        val app = karoo.app
        runBlocking {
            app.saveFieldSparklineConfig(
                SparklineConfig(mode = SparklineMode.OFF, lookaheadKm = 20)
            )
        }
        val config = runBlocking { app.streamFieldSparklineConfig().first() }
        assertEquals(SparklineMode.ON, config.hudMode)
        assertEquals(20, config.lookaheadKm)
    }

    @Test
    fun a_zone_palette_write_reaches_the_zone_stream() {
        val app = karoo.app
        runBlocking { app.saveZoneConfig(ZoneConfig(hrPalette = ZonePalette.WAHOO)) }
        assertEquals(ZonePalette.WAHOO, runBlocking { app.streamZoneConfig().first() }.hrPalette)
    }

    @Test
    fun the_snapshot_carries_the_same_palette_the_single_key_does() {
        val app = karoo.app
        runBlocking { app.saveZoneConfig(ZoneConfig(powerPalette = ZonePalette.ZWIFT)) }
        assertEquals(
            ZonePalette.ZWIFT,
            runBlocking { app.streamConfigSnapshot().first() }.zone.powerPalette,
        )
    }
}
