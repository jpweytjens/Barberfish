package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.savePowerFieldConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A placed field registers Karoo stream consumers while it draws and releases them when the ride
 * app stops it, so the fake's live consumer count is the observable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
class FieldStreamLifecycleTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    @Before
    fun startExtension() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        runBlocking {
            app.savePowerFieldConfig(PowerFieldConfig(smoothing = PowerSmoothingStream.S0))
        }
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    private fun point(typeId: String, vararg values: Pair<String, Double>) =
        DataPoint(typeId, values.toMap())

    private fun awaitConsumersReleased(before: Int) {
        karoo.awaitValue { true.takeIf { karoo.system.consumerCount == before } }
    }

    @Test
    fun a_numeric_view_registers_its_sensor_consumer_and_releases_it_on_stop() {
        val before = karoo.system.consumerCount
        val view = host.startView("power", FIELD_CONFIG)
        karoo.system.setDataPoint(point(DataType.Type.POWER, DataType.Field.POWER to 200.0))
        view.awaitFrame(15_000)
        assertTrue(
            "the power field should hold a sensor consumer while open",
            karoo.system.consumerCount > before,
        )

        host.stopView(view)
        awaitConsumersReleased(before)
    }

    @Test
    fun a_combined_view_releases_every_consumer_it_registered() {
        val before = karoo.system.consumerCount
        val view = host.startView("remaining-effort", FIELD_CONFIG)
        karoo.system.setDataPoint(
            point(
                DataType.Type.DISTANCE_TO_DESTINATION,
                DataType.Field.DISTANCE_TO_DESTINATION to 2_000.0,
                DataType.Field.ON_ROUTE to 1.0,
            )
        )
        karoo.system.setDataPoint(
            point(
                DataType.Type.ELEVATION_REMAINING,
                DataType.Field.ASCENT_REMAINING to 300.0,
            )
        )
        view.awaitFrame(15_000)
        assertTrue(karoo.system.consumerCount > before)

        host.stopView(view)
        awaitConsumersReleased(before)
    }

    @Test
    fun a_sparkline_view_releases_its_route_consumers_on_stop() {
        karoo.system.setNavigation(climbRoute().navigating())
        val before = karoo.system.consumerCount
        val config = ViewConfig(gridSize = 60 to 30, viewSize = 480 to 200, textSize = 30)
        val view = host.startView("elevation-sparkline", config)
        karoo.system.setDataPoint(
            point(
                DataType.Type.DISTANCE_TO_DESTINATION,
                DataType.Field.DISTANCE_TO_DESTINATION to 4_000.0,
                DataType.Field.ON_ROUTE to 1.0,
            )
        )
        view.awaitFrame(20_000)
        assertTrue(karoo.system.consumerCount > before)

        host.stopView(view)
        awaitConsumersReleased(before)
    }

    @Test
    fun a_view_that_only_sees_an_error_state_still_releases_its_consumer() {
        karoo.system.setStreamState(DataType.Type.POWER, StreamState.NotAvailable)
        val before = karoo.system.consumerCount
        val view = host.startView("power", FIELD_CONFIG)
        view.awaitFrame(15_000)
        assertTrue(karoo.system.consumerCount > before)

        host.stopView(view)
        awaitConsumersReleased(before)
    }
}
