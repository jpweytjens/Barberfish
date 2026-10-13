package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.saveFieldSparklineConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Symbol
import io.hammerhead.karooext.models.ViewConfig
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

/** Saved POIs reach the elevation strip as markers, drawn pixels that are compared across runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SparklinePoiTest {
    @get:Rule val karoo = FakeKarooRule()

    private val config = ViewConfig(gridSize = 60 to 30, viewSize = 480 to 200, textSize = 30)
    private lateinit var host: FakeKarooHost

    @Before
    fun startOnTheRoute() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(
                    DataType.Field.DISTANCE_TO_DESTINATION to 4_000.0,
                    DataType.Field.ON_ROUTE to 1.0,
                ),
            )
        )
        host = karoo.host<BarberfishExtension>()
    }

    private fun poiAt(metresEast: Double, metresNorth: Double) =
        RouteFixtures.point(metresEast, metresNorth).let { Symbol.POI("poi", it.lat, it.lng) }

    private fun strip(): Bitmap {
        val frame = host.startView("elevation-sparkline", config).awaitFrame(15_000)
        val image = frame.inflate(karoo.app, config).findViewById<ImageView>(R.id.sparkline_image)
        return (image.drawable as BitmapDrawable).bitmap
    }

    @Test
    fun a_saved_poi_beside_the_route_gets_a_marker_and_one_far_away_does_not() {
        karoo.system.setGlobalPois(emptyList())
        val bare = strip()

        karoo.system.setGlobalPois(listOf(poiAt(2_000.0, metresNorth = 100.0)))
        val withPoi = strip()
        assertFalse("a POI 100 m off the route is marked", bare.sameAs(withPoi))

        karoo.system.setGlobalPois(listOf(poiAt(2_000.0, metresNorth = 5_000.0)))
        assertTrue("a POI 5 km off the route is not", bare.sameAs(strip()))
    }

    @Test
    fun poi_markers_can_be_turned_off_in_settings() {
        karoo.system.setGlobalPois(emptyList())
        val bare = strip()
        karoo.system.setGlobalPois(listOf(poiAt(2_000.0, metresNorth = 100.0)))
        assertFalse(bare.sameAs(strip()))

        runBlocking { karoo.app.saveFieldSparklineConfig(SparklineConfig(showPois = false)) }

        assertTrue(bare.sameAs(strip()))
    }
}
