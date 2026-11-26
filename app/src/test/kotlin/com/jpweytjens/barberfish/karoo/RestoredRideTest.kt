package com.jpweytjens.barberfish.karoo

import android.graphics.Bitmap
import com.jpweytjens.barberfish.datatype.shared.MovingState
import com.jpweytjens.barberfish.extension.loadMovingState
import com.jpweytjens.barberfish.extension.saveMovingState
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.RideState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val AVG_SPEED_MOVING = "avg-speed-moving"
private const val AVG_SPEED_TOTAL = "avg-speed-total"

/**
 * A ride stored before the Karoo powered off, restored when the extension starts again, as the
 * field the extension hosts draws it. A field draws its value as a bitmap, so each expected average
 * is drawn by Avg Speed (total) from the same distance over a total time of the same length; that
 * needs real text rendering, hence native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RestoredRideTest {
    @get:Rule val karoo = FakeKarooRule()

    @Test
    fun a_stored_ride_continues_after_a_restart() {
        clearBarberfishSettings(karoo.app)
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        // 10 mi in 56 min of moving time, stored with the Karoo's recording time and distance.
        runBlocking {
            karoo.app.saveMovingState(MovingState(3_360_000.0, 16_093.4, 3_360_000.0, 16_093.4))
        }
        karoo.system.setActiveRideProfile(profileWith(AVG_SPEED_MOVING))
        karoo.system.setRideState(RideState.Paused(auto = false))
        RobolectricPump.advanceBy(1.seconds)
        val host = karoo.startBarberfish()

        // No recording time has arrived, so the stored pair is the only source of an average.
        val stored = host.averageDrawnFor(totalTimeMs = 3_360_000.0, distanceM = 16_093.4)
        host.awaitDrawn(AVG_SPEED_MOVING, stored)

        // The Karoo's recording time restarts at 1 s on the same distance, then runs on 60 s.
        record(1_000.0, 16_093.4)
        karoo.system.setRideState(RideState.Recording)
        awaitStepOn(1_000.0)
        record(61_000.0, 16_593.4)
        awaitStepOn(61_000.0)

        // 16 593.4 m in 3 420 s, not in the 61 s since the restart.
        val continued = host.averageDrawnFor(totalTimeMs = 3_420_000.0, distanceM = 16_593.4)
        assertFalse("the two averages must draw differently", continued.sameAs(stored))
        host.awaitDrawn(AVG_SPEED_MOVING, continued)
    }

    /** The value Avg Speed (total) draws for [distanceM] over [totalTimeMs]. */
    private fun FakeKarooHost.averageDrawnFor(totalTimeMs: Double, distanceM: Double): Bitmap {
        karoo.system.setDataPoint(
            dataPoint(DataType.Type.DISTANCE, DataType.Field.DISTANCE to distanceM)
        )
        karoo.system.setDataPoint(
            dataPoint(DataType.Type.RIDE_TIME, DataType.Field.RIDE_TIME to totalTimeMs)
        )
        return awaitDrawn(AVG_SPEED_TOTAL) { true }
    }

    private fun FakeKarooHost.awaitDrawn(typeId: String, expected: Bitmap) {
        awaitDrawn(typeId) { it.sameAs(expected) }
    }

    /**
     * Opens a fresh view of [typeId] until its first frame draws a value that satisfies [expected].
     * A fresh view, because the SDK drops a frame that follows the last within 900 ms.
     */
    private fun FakeKarooHost.awaitDrawn(typeId: String, expected: (Bitmap) -> Boolean): Bitmap =
        karoo.awaitValue {
            val view = startView(typeId, FIELD_CONFIG)
            val drawn = view.awaitFrame(15_000).inflate(karoo.app, FIELD_CONFIG)
            stopView(view)
            drawn.takeIf { it.fieldStateText() == null }?.fieldValue()?.takeIf(expected)
        }

    /** The Karoo's recording time and distance, with a speed reading. */
    private fun record(recordingTimeMs: Double, distanceM: Double) {
        karoo.system.setDataPoint(dataPoint(DataType.Type.SPEED, DataType.Field.SPEED to 5.0))
        karoo.system.setDataPoint(
            dataPoint(DataType.Type.DISTANCE, DataType.Field.DISTANCE to distanceM)
        )
        karoo.system.setDataPoint(
            dataPoint(DataType.Type.ELAPSED_TIME, DataType.Field.ELAPSED_TIME to recordingTimeMs)
        )
    }

    /**
     * Waits until the extension has taken a step on [recordingTimeMs], as its stored state shows.
     */
    private fun awaitStepOn(recordingTimeMs: Double) {
        karoo.awaitValue {
            runBlocking { karoo.app.loadMovingState() }
                ?.takeIf { it.recordingTimeMs == recordingTimeMs }
        }
    }

    private fun profileWith(typeId: String) =
        RideProfile(
            id = typeId,
            name = typeId,
            pages =
                listOf(
                    RideProfile.Page(
                        mapPage = false,
                        elements =
                            listOf(
                                RideProfile.Page.Element(
                                    DataType.dataTypeId("barberfish", typeId),
                                    1 to 1,
                                )
                            ),
                    )
                ),
            indoor = false,
            defaultActivityType = "",
            routingPreference = "",
        )
}
