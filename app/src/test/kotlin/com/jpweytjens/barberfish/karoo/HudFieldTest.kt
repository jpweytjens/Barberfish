package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.saveHUDConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The HUD strip picks its layout from the configured column count and shows the sparkline area only
 * while a route or destination gives it an elevation profile to draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
class HudFieldTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    private val config = ViewConfig(gridSize = 60 to 30, viewSize = 480 to 200, textSize = 30)

    @Before
    fun startExtension() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        // The HUD's sparkline input only emits once navigation and distance-to-destination have
        // each been published at least once, so the whole strip waits on them.
        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)
        karoo.system.setStreamState(DataType.Type.DISTANCE_TO_DESTINATION, StreamState.NotAvailable)
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    /** Every slot on the same simple stream, so one data point lights the whole strip. */
    private fun distanceSlots(columns: Int) =
        HUDConfig(
            columns = columns,
            leftSlot = HUDSlotConfig(field = HUDSlotField.Distance),
            middleSlot = HUDSlotConfig(field = HUDSlotField.Distance),
            rightSlot = HUDSlotConfig(field = HUDSlotField.Distance),
            fourthSlot = HUDSlotConfig(field = HUDSlotField.Distance),
        )

    private fun distance(meters: Double) =
        karoo.system.setDataPoint(
            DataPoint(DataType.Type.DISTANCE, mapOf(DataType.Field.DISTANCE to meters))
        )

    private fun distanceRemaining(meters: Double) =
        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(
                    DataType.Field.DISTANCE_TO_DESTINATION to meters,
                    DataType.Field.ON_ROUTE to 1.0,
                ),
            )
        )

    private fun container(view: android.view.View) =
        view.findViewById<View>(R.id.hud_sparkline_container)

    @Test
    fun a_three_column_hud_reports_the_three_slot_layout() {
        runBlocking { karoo.app.saveHUDConfig(distanceSlots(columns = 3)) }
        distance(1_500.0)
        val frame = host.startView("three-column", config).awaitFrame(20_000)
        assertEquals(R.layout.barberfish_hud, frame.layoutId)
    }

    @Test
    fun a_four_column_hud_reports_the_four_slot_layout() {
        runBlocking { karoo.app.saveHUDConfig(distanceSlots(columns = 4)) }
        distance(1_500.0)
        val frame = host.startView("three-column", config).awaitFrame(20_000)
        assertEquals(R.layout.barberfish_hud_four, frame.layoutId)
    }

    @Test
    fun a_column_change_on_the_device_swaps_the_open_layout() {
        runBlocking { karoo.app.saveHUDConfig(distanceSlots(columns = 3)) }
        distance(1_500.0)
        val view = host.startView("three-column", config)
        assertEquals(R.layout.barberfish_hud, view.awaitFrame(20_000).layoutId)

        val seen = view.items.size
        RobolectricPump.advanceBy(2.seconds)
        runBlocking { karoo.app.saveHUDConfig(distanceSlots(columns = 4)) }
        assertEquals(R.layout.barberfish_hud_four, view.awaitFrame(20_000, after = seen).layoutId)
    }

    @Test
    fun the_hud_sparkline_area_appears_once_a_route_is_followed() {
        runBlocking { karoo.app.saveHUDConfig(distanceSlots(columns = 3)) }
        karoo.system.setNavigation(climbRoute().navigating())
        distance(1_500.0)
        distanceRemaining(4_000.0)

        val frame = host.startView("three-column", config).awaitFrame(20_000)
        val inflated = frame.inflate(karoo.app, config)
        assertEquals(View.VISIBLE, container(inflated).visibility)
        assertEquals(
            View.VISIBLE,
            inflated.findViewById<View>(R.id.hud_elevation_sparkline).visibility,
        )
    }

    @Test
    fun the_hud_sparkline_area_stays_away_without_a_route() {
        runBlocking { karoo.app.saveHUDConfig(distanceSlots(columns = 3)) }
        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)
        karoo.system.setStreamState(DataType.Type.DISTANCE_TO_DESTINATION, StreamState.NotAvailable)
        distance(1_500.0)

        val frame = host.startView("three-column", config).awaitFrame(20_000)
        val inflated = frame.inflate(karoo.app, config)
        assertEquals(View.GONE, container(inflated).visibility)
    }
}
