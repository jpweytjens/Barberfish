package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.extension.AvgSpeedThresholdConfig
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDScope
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.updateHUDConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A HUD slot colours its value the way the standalone field does: against the slot's own threshold,
 * in the slot's own colour mode. Each case draws the left slot twice with one setting changed, so
 * only the colour can differ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudSlotColourTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    @Before
    fun startExtension() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        // The HUD's sparkline input only emits once navigation and distance-to-destination have
        // each been published at least once, so the whole strip waits on them.
        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)
        karoo.system.setStreamState(DataType.Type.DISTANCE_TO_DESTINATION, StreamState.NotAvailable)
        // 3 km in 6 minutes of moving time: an average of 30 km/h.
        point(DataType.Type.DISTANCE, DataType.Field.DISTANCE, 3_000.0)
        point(DataType.Type.ELAPSED_TIME, DataType.Field.ELAPSED_TIME, 360_000.0)
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    private fun point(typeId: String, fieldId: String, value: Double) =
        karoo.system.setDataPoint(DataPoint(typeId, mapOf(fieldId to value)))

    /** Draws a 3-column HUD with [left] beside two distance slots and returns the left value. */
    private fun leftValue(left: HUDSlotConfig): Bitmap {
        val distance = HUDSlotConfig(field = HUDSlotField.Distance)
        runBlocking {
            karoo.app.updateHUDConfig(HUDScope.Shared) {
                HUDConfig(slots = HUDSlots(3, left, distance, distance, distance))
            }
        }
        RobolectricPump.advanceBy(1.seconds)
        return host
            .renderField(karoo.app, "three-column", TALL_FIELD_CONFIG)
            .findViewById<View>(R.id.hud_slot_left)
            .fieldValue()
    }

    @Test
    fun an_avg_speed_slot_follows_the_slots_colour_mode() {
        val slot =
            HUDSlotConfig(
                field = HUDSlotField.AvgSpeed(includePaused = false),
                avgSpeedThreshold = AvgSpeedThresholdConfig(thresholdKph = 25.0),
            )
        val coloured = leftValue(slot.copy(colorMode = ZoneColorMode.TEXT))
        val plain = leftValue(slot.copy(colorMode = ZoneColorMode.NONE))
        assertFalse("colour mode None should not match Text", coloured.sameAs(plain))
        val colouredAgain = leftValue(slot.copy(colorMode = ZoneColorMode.TEXT))
        assertTrue("the same slot must draw the same value", coloured.sameAs(colouredAgain))
    }
}
