package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.PowerFieldConfig
import com.jpweytjens.barberfish.extension.PowerSmoothingStream
import com.jpweytjens.barberfish.extension.savePowerFieldConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The vocabulary a live field shows when its source has no value, and the value once it has one.
 * The SDK drops a view frame within 900 ms of the last, read from the real clock, so that clock is
 * virtualized and advanced between updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
class LiveFieldStatesTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    @Before
    fun startExtension() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        // The power field follows the 3 s smoothed stream by default; pin it to raw power so the
        // tests drive one datatype id.
        runBlocking {
            app.savePowerFieldConfig(PowerFieldConfig(smoothing = PowerSmoothingStream.S0))
        }
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    private fun point(typeId: String, vararg values: Pair<String, Double>) =
        DataPoint(typeId, values.toMap())

    private fun render(typeId: String) = host.renderField(karoo.app, typeId)

    @Test
    fun a_sensor_field_says_searching_while_the_sensor_wakes_up() {
        karoo.system.setStreamState(DataType.Type.POWER, StreamState.Searching)
        assertEquals("Searching…", render("power").fieldStateText())
    }

    @Test
    fun an_unpaired_sensor_field_reads_no_sensor() {
        karoo.system.setStreamState(DataType.Type.POWER, StreamState.NotAvailable)
        assertEquals("No sensor", render("power").fieldStateText())

        karoo.system.setStreamState(DataType.Type.HEART_RATE, StreamState.NotAvailable)
        assertEquals("No sensor", render("hr").fieldStateText())

        karoo.system.setStreamState(DataType.Type.CADENCE, StreamState.NotAvailable)
        assertEquals("No sensor", render("cadence").fieldStateText())
    }

    @Test
    fun a_stopped_sensor_reads_no_data() {
        karoo.system.setStreamState(DataType.Type.POWER, StreamState.Idle)
        assertEquals("No data", render("power").fieldStateText())
    }

    @Test
    fun a_searched_sensor_field_swaps_its_message_for_the_first_value() {
        karoo.system.setStreamState(DataType.Type.POWER, StreamState.Searching)
        val view = host.startView("power", FIELD_CONFIG)
        val searching = view.awaitFrame(15_000).inflate(karoo.app, FIELD_CONFIG)
        assertEquals("Searching…", searching.fieldStateText())

        val seen = view.items.size
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setDataPoint(point(DataType.Type.POWER, DataType.Field.POWER to 210.0))
        val live = view.awaitFrame(15_000, after = seen).inflate(karoo.app, FIELD_CONFIG)
        assertEquals(null, live.fieldStateText())
        assertTrue(live.fieldValueVisible())
    }

    @Test
    fun a_plain_value_field_without_a_route_reads_not_available() {
        karoo.system.setStreamState(DataType.Type.DISTANCE, StreamState.NotAvailable)
        assertEquals("Not available", render("distance").fieldStateText())
    }

    @Test
    fun a_route_gated_field_walks_from_no_route_to_a_value() {
        karoo.system.setStreamState(DataType.Type.DISTANCE_TO_DESTINATION, StreamState.NotAvailable)
        val view = host.startView("distance-remaining", FIELD_CONFIG)
        val noRoute = view.awaitFrame(15_000).inflate(karoo.app, FIELD_CONFIG)
        assertEquals("No route", noRoute.fieldStateText())

        var seen = view.items.size
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setDataPoint(
            point(
                DataType.Type.DISTANCE_TO_DESTINATION,
                DataType.Field.DISTANCE_TO_DESTINATION to 2_500.0,
                DataType.Field.ON_ROUTE to 0.0,
            )
        )
        val offRoute = view.awaitFrame(15_000, after = seen).inflate(karoo.app, FIELD_CONFIG)
        assertEquals("Off route", offRoute.fieldStateText())

        seen = view.items.size
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setDataPoint(
            point(
                DataType.Type.DISTANCE_TO_DESTINATION,
                DataType.Field.DISTANCE_TO_DESTINATION to 2_500.0,
                DataType.Field.ON_ROUTE to 1.0,
            )
        )
        val onRoute = view.awaitFrame(15_000, after = seen).inflate(karoo.app, FIELD_CONFIG)
        assertEquals(null, onRoute.fieldStateText())
        assertTrue(onRoute.fieldValueVisible())
    }

    @Test
    fun a_last_lap_field_reports_no_laps_until_the_second_lap() {
        karoo.system.setDataPoint(point(DataType.Type.LAP_NUMBER, DataType.Field.LAP_NUMBER to 1.0))
        karoo.system.setDataPoint(
            point(DataType.Type.ELAPSED_TIME_LAST_LAP, DataType.Field.ELAPSED_TIME to 90.0)
        )
        val view = host.startView("time-last-lap", FIELD_CONFIG)
        val first = view.awaitFrame(15_000).inflate(karoo.app, FIELD_CONFIG)
        assertEquals("No laps yet", first.fieldStateText())

        val seen = view.items.size
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setDataPoint(point(DataType.Type.LAP_NUMBER, DataType.Field.LAP_NUMBER to 2.0))
        val second = view.awaitFrame(15_000, after = seen).inflate(karoo.app, FIELD_CONFIG)
        assertEquals(null, second.fieldStateText())
        assertTrue(second.fieldValueVisible())
    }

    @Test
    fun a_plain_time_field_shows_its_value_without_a_lap_stream() {
        karoo.system.setDataPoint(
            point(DataType.Type.RIDE_TIME, DataType.Field.RIDE_TIME to 3661.0)
        )
        val view = render("time-elapsed")
        assertEquals(null, view.fieldStateText())
        assertTrue(view.fieldValueVisible())
    }
}
