package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.datatype.shared.MovingState
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDScope
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.dataStore
import com.jpweytjens.barberfish.extension.loadMovingState
import com.jpweytjens.barberfish.extension.saveFieldSparklineConfig
import com.jpweytjens.barberfish.extension.saveMovingState
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamConfigSnapshot
import com.jpweytjens.barberfish.extension.streamFieldSparklineConfig
import com.jpweytjens.barberfish.extension.streamZoneConfig
import com.jpweytjens.barberfish.extension.updateHUDConfig
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun moving_state_round_trips() {
        val app = karoo.app
        val state = MovingState(1_000.0, 2_500.0, 3_000.0, 4_000.0)
        runBlocking { app.saveMovingState(state) }
        assertEquals(state, runBlocking { app.loadMovingState() })
    }

    @Test
    fun saving_null_removes_the_moving_state() {
        val app = karoo.app
        runBlocking {
            app.saveMovingState(MovingState(1.0, 2.0, 3.0, 4.0))
            app.saveMovingState(null)
        }
        assertNull(runBlocking { app.loadMovingState() })
    }

    @Test
    fun saving_the_moving_state_does_not_emit_on_the_settings_file() {
        val app = karoo.app
        val emissions = AtomicInteger()
        runBlocking {
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    app.dataStore.data.onEach { emissions.incrementAndGet() }.collect {}
                }
            // The first emission is the current value.
            withTimeout(5_000) { while (emissions.get() < 1) yield() }
            app.saveMovingState(MovingState(1.0, 2.0, 3.0, 4.0))
            // Let a leaked write reach the collector before the control write can merge with it.
            delay(500)
            val afterMovingSave = emissions.get()
            app.saveZoneConfig(ZoneConfig(hrPalette = ZonePalette.WAHOO))
            // The control write proves the collector is live; a bounded wait fails instead of
            // hangs.
            withTimeout(5_000) { while (emissions.get() == afterMovingSave) yield() }
            collector.cancel()
            assertEquals(1, afterMovingSave)
        }
    }
}
