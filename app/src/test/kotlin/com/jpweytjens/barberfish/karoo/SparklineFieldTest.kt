package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.RouteRemainingConfig
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.saveFieldSparklineConfig
import com.jpweytjens.barberfish.extension.saveRouteRemainingConfig
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The graphical sparkline fields draw a route profile and honour their own header setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SparklineFieldTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    private val config = ViewConfig(gridSize = 60 to 30, viewSize = 480 to 200, textSize = 30)

    @Before
    fun startExtension() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)
        karoo.system.setStreamState(DataType.Type.DISTANCE_TO_DESTINATION, StreamState.NotAvailable)
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

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

    private fun render(typeId: String) =
        host.startView(typeId, config).awaitFrame(20_000).inflate(karoo.app, config)

    @Test
    fun the_elevation_sparkline_shows_its_header_by_default() {
        assertTrue(render("elevation-sparkline").headerVisible())
    }

    @Test
    fun the_elevation_sparkline_hides_its_header_when_the_setting_says_so() {
        runBlocking { karoo.app.saveFieldSparklineConfig(SparklineConfig(showHeader = false)) }
        assertFalse(render("elevation-sparkline").headerVisible())
    }

    @Test
    fun the_route_remaining_field_honours_its_own_header_setting() {
        assertTrue(render("route-remaining").headerVisible())

        runBlocking { karoo.app.saveRouteRemainingConfig(RouteRemainingConfig(showHeader = false)) }
        assertFalse(render("route-remaining").headerVisible())
    }

    @Test
    fun following_a_route_draws_an_elevation_profile() {
        karoo.system.setNavigation(climbRoute().navigating())
        distanceRemaining(4_000.0)
        val bitmap = render("elevation-sparkline").sparklineValue()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun a_route_change_redraws_the_profile() {
        karoo.system.setNavigation(climbRoute().navigating())
        distanceRemaining(4_000.0)
        val view = host.startView("elevation-sparkline", config)
        val climb = view.awaitFrame(20_000).inflate(karoo.app, config).sparklineValue()

        RobolectricPump.advanceBy(3.seconds)
        karoo.system.setNavigation(flatRoute().navigating())
        distanceRemaining(4_000.0)

        // A redraw of the climb can land before the flat route's frame, so the first new frame
        // proves nothing; wait for a strip that differs from the climb's.
        karoo.awaitValue {
            view.frames.last().inflate(karoo.app, config).sparklineValue().takeUnless {
                it.sameAs(climb)
            }
        }
    }
}
