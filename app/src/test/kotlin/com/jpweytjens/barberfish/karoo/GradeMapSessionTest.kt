package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.ShowSymbols
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A map page's lifecycle: a zoom band crossing redraws it, and stopping it releases its inputs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradeMapSessionTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    @Before
    fun clean() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        host = karoo.host<BarberfishExtension>()
    }

    @Test
    fun a_zoom_band_change_redraws_the_overlay() {
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setLocation(0.0, lngAt(100.0))
        val map = host.startMap()
        map.awaitDrawn(karoo)
        val mark = map.mark()

        // Crossing into the next integer band refreezes the zoom and re-lays the chevrons.
        karoo.system.setMapZoom(18.0)

        map.await(20_000, mark) { it is ShowPolyline || it is ShowSymbols }
    }

    @Test
    fun stopping_a_map_session_releases_its_inputs() {
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setLocation(0.0, lngAt(100.0))
        val before = karoo.system.consumerCount
        val map = host.startMap()
        map.awaitDrawn(karoo)
        assertTrue(
            "the map should hold the navigation and location consumers while open",
            karoo.system.consumerCount > before,
        )

        host.stopMap(map)
        karoo.awaitValue { true.takeIf { karoo.system.consumerCount == before } }
    }
}
