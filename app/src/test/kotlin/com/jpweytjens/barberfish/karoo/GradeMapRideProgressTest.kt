package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import fi.nikosavola.karooext.testing.MapRecorder
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HideSymbols
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The map holds distance samples for 2 s after a route change, read from System.currentTimeMillis,
 * so the extension's own clock is virtualized and advanced by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["com.jpweytjens.barberfish"])
class GradeMapRideProgressTest {
    @get:Rule val karoo = FakeKarooRule()

    @Before
    fun clean() = clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())

    private fun drawnMap(): MapRecorder {
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setLocation(0.0, lngAt(100.0))
        val map = karoo.host<BarberfishExtension>().startMap()
        karoo.awaitStreamConsumer(DataType.Type.DISTANCE_TO_DESTINATION)
        map.awaitDrawn(karoo)
        return map
    }

    private fun ridden(metres: Double) =
        karoo.system.setDataPoint(
            DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(
                    DataType.Field.DISTANCE_TO_DESTINATION to 4_000.0 - metres,
                    DataType.Field.ON_ROUTE to 1.0,
                ),
            )
        )

    private fun MapRecorder.chevronsM() = visibleChevrons().map { metresEast(it.lng) }

    @Test
    fun chevrons_the_rider_has_passed_leave_the_map() {
        val map = drawnMap()
        val before = map.chevronsM()
        assertTrue(before.min() < 400.0)
        RobolectricPump.advanceBy(3.seconds)

        val mark = map.mark()
        ridden(600.0)
        map.await(10_000, mark) { it is HideSymbols }
        map.awaitQuiet(quietMs = 800, timeoutMs = 15_000)

        assertTrue(map.chevronsM().all { it >= 590.0 })
        assertTrue(map.chevronsM().isNotEmpty())
    }

    @Test
    fun a_distance_sample_right_after_a_route_change_is_ignored() {
        val map = drawnMap()
        val before = map.chevronsM()

        val mark = map.mark()
        ridden(600.0)
        map.awaitQuiet(quietMs = 500, timeoutMs = 15_000)
        assertEquals(before, map.chevronsM())
        assertTrue(map.items.drop(mark).none { it is HideSymbols })

        RobolectricPump.advanceBy(3.seconds)
        ridden(601.0)
        map.await(10_000, mark) { it is HideSymbols }
    }
}
