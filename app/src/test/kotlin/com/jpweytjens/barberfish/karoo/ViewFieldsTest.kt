package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.extension.BarberfishExtension
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
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
 * Field values are drawn into a bitmap, so a reading is compared by pixels: two readings that print
 * the same text match, and a different unit is checked against a negative control. That needs real
 * text rendering, hence native graphics.
 *
 * The SDK drops a frame within 900 ms of the last, read from System.currentTimeMillis, so its clock
 * is virtualized and advanced where a test expects a second frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewFieldsTest {
    @get:Rule val karoo = FakeKarooRule()

    private val config = ViewConfig(gridSize = 60 to 15, viewSize = 480 to 200, textSize = 30)
    private lateinit var host: FakeKarooHost

    @Before
    fun startExtension() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    private fun setUnits(profile: UserProfile) = karoo.system.setUserProfile(profile)

    private fun distance(meters: Double) =
        karoo.system.setDataPoint(
            DataPoint(DataType.Type.DISTANCE, mapOf(DataType.Field.DISTANCE to meters))
        )

    private fun ascentRemaining(meters: Double) =
        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.ELEVATION_REMAINING,
                mapOf(DataType.Field.ASCENT_REMAINING to meters),
            )
        )

    /** What a field shows once it has its first frame, under its own session. */
    private fun render(typeId: String): View =
        host.startView(typeId, config).awaitFrame(10_000).inflate(karoo.app, config)

    private fun View.value(): Bitmap =
        (findViewById<ImageView>(R.id.field_value).drawable as BitmapDrawable).bitmap

    private fun View.stateText(): String? =
        findViewById<TextView>(R.id.stream_state_tv)
            ?.takeIf { it.visibility == View.VISIBLE }
            ?.text
            ?.toString()

    @Test
    fun distance_reads_the_same_in_km_and_in_miles_for_the_same_ride() {
        setUnits(FakeKarooSystem.metricProfile())
        distance(1_000.0)
        val km = render("distance").value()

        setUnits(FakeKarooSystem.imperialProfile())
        distance(1_609.344)
        val miles = render("distance").value()
        assertTrue("1 km and 1 mi both read 1.0", km.sameAs(miles))

        distance(1_000.0)
        val shortOfAMile = render("distance").value()
        assertFalse("0.6 mi must not read like 1.0", km.sameAs(shortOfAMile))
    }

    @Test
    fun ascent_remaining_is_shown_in_feet_for_an_imperial_rider() {
        setUnits(FakeKarooSystem.metricProfile())
        ascentRemaining(1_000.0)
        val thousandMetres = render("elevation-remaining").value()
        ascentRemaining(304.8)
        val metresForTheRoad = render("elevation-remaining").value()

        setUnits(FakeKarooSystem.imperialProfile())
        ascentRemaining(304.8)
        val thousandFeet = render("elevation-remaining").value()

        assertTrue("304.8 m and 1000 m both read 1000", thousandFeet.sameAs(thousandMetres))
        assertFalse("304.8 m reads 305 in metres", thousandFeet.sameAs(metresForTheRoad))
    }

    @Test
    fun a_unit_change_on_the_device_re_renders_the_open_field() {
        setUnits(FakeKarooSystem.metricProfile())
        distance(1_000.0)
        val view = host.startView("distance", config)
        val metricFrame = view.awaitFrame(10_000)
        val kmBitmap = metricFrame.inflate(karoo.app, config).value()

        RobolectricPump.advanceBy(1.seconds)
        val seen = view.items.size
        setUnits(FakeKarooSystem.imperialProfile())
        val imperialFrame = view.awaitFrame(10_000, after = seen)

        // 1000 m is 0.6 mi: not what the metric frame said, and what a fresh imperial field shows.
        val miBitmap = imperialFrame.inflate(karoo.app, config).value()
        assertFalse(kmBitmap.sameAs(miBitmap))
        assertTrue(miBitmap.sameAs(render("distance").value()))
    }

    @Test
    fun remaining_distance_says_why_it_has_no_number() {
        karoo.system.setStreamState(DataType.Type.DISTANCE_TO_DESTINATION, StreamState.NotAvailable)
        assertEquals("No route", render("distance-remaining").stateText())

        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(
                    DataType.Field.DISTANCE_TO_DESTINATION to 2_500.0,
                    DataType.Field.ON_ROUTE to 0.0,
                ),
            )
        )
        assertEquals("Off route", render("distance-remaining").stateText())

        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(
                    DataType.Field.DISTANCE_TO_DESTINATION to 2_500.0,
                    DataType.Field.ON_ROUTE to 1.0,
                ),
            )
        )
        assertEquals(null, render("distance-remaining").stateText())
    }
}
