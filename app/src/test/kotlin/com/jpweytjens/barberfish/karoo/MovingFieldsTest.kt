package com.jpweytjens.barberfish.karoo

import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.MovingTimeAndDistance
import com.jpweytjens.barberfish.extension.HUDScope
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import com.jpweytjens.barberfish.extension.loadMovingState
import com.jpweytjens.barberfish.extension.movingTimeAndDistance
import com.jpweytjens.barberfish.extension.saveMovingState
import com.jpweytjens.barberfish.extension.saveSpeedFieldConfig
import com.jpweytjens.barberfish.extension.updateHUDConfig
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val AVG_SPEED_MOVING = "avg-speed-moving"
private const val HUD = "three-column"

/** 10 000 m in 1 800 s of recording time: 20.0 km/h. */
private const val RIDE_TIME_MS = 1_800_000.0
private const val RIDE_DISTANCE_M = 10_000.0

/**
 * The fields that read our moving time and distance, fed by the extension's own wiring from the
 * fake Karoo's recording time, distance and speed. The wiring ticks once a second on the real
 * clock, so each step is awaited through the state it saves. Every flow runs on one thread, so a
 * reading reaches the fields before the wiring's next tick can take a step on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
class MovingFieldsTest {
    @get:Rule val karoo = FakeKarooRule()

    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)
    private lateinit var karooSystem: KarooSystemService

    @Before
    fun connect() {
        clearBarberfishSettings(karoo.app)
        runBlocking { karoo.app.saveMovingState(null) }
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        karooSystem = KarooSystemService(karoo.app)
        karooSystem.connect()
        karoo.awaitValue { true.takeIf { karooSystem.connected } }
    }

    @After
    fun disconnect() {
        scope.cancel()
        thread.close()
        karooSystem.disconnect()
    }

    // --- Set-up shared by the fields that read the pair ---

    /** A ride profile with the Barberfish fields [typeIds] on one page. */
    private fun rideProfileWith(vararg typeIds: String) =
        RideProfile(
            id = typeIds.joinToString(),
            name = typeIds.joinToString(),
            pages =
                listOf(
                    RideProfile.Page(
                        mapPage = false,
                        elements =
                            typeIds.map {
                                RideProfile.Page.Element(
                                    DataType.dataTypeId("barberfish", it),
                                    1 to 1,
                                )
                            },
                    )
                ),
            indoor = false,
            defaultActivityType = "",
            routingPreference = "",
        )

    /**
     * Starts a recording ride on a profile showing [typeIds] and returns our pair for it, from the
     * wiring the extension runs.
     */
    private fun rideShowing(vararg typeIds: String): StateFlow<MovingTimeAndDistance?> {
        karoo.system.setActiveRideProfile(rideProfileWith(*typeIds))
        karoo.system.setRideState(RideState.Recording)
        return latest(karoo.app.movingTimeAndDistance(karooSystem, emptyFlow()))
    }

    /**
     * The Karoo's recording time and distance, with a speed reading in m/s or, for a null [speed],
     * none. Speed goes first, so a tick between the two never pairs the new time with the old
     * signal.
     */
    private fun record(recordingTimeMs: Double, distanceM: Double, speed: Double? = 5.0) {
        if (speed == null) {
            karoo.system.setStreamState(DataType.Type.SPEED, StreamState.Idle)
        } else {
            karoo.system.setDataPoint(dataPoint(DataType.Type.SPEED, DataType.Field.SPEED to speed))
        }
        karoo.system.setDataPoint(
            dataPoint(DataType.Type.DISTANCE, DataType.Field.DISTANCE to distanceM)
        )
        karoo.system.setDataPoint(
            dataPoint(DataType.Type.ELAPSED_TIME, DataType.Field.ELAPSED_TIME to recordingTimeMs)
        )
    }

    /**
     * Waits until the wiring has taken a step on [recordingTimeMs], as the state it saved shows.
     */
    private fun awaitStepOn(recordingTimeMs: Double) {
        karoo.awaitValue {
            runBlocking { karoo.app.loadMovingState() }
                ?.takeIf { it.recordingTimeMs == recordingTimeMs }
        }
    }

    private fun <T> latest(flow: Flow<T>): StateFlow<T?> =
        flow.stateIn(scope, SharingStarted.Eagerly, null)

    private fun <T> StateFlow<T?>.await(expected: (T) -> Boolean): T = karoo.awaitValue {
        value?.takeIf(expected)
    }

    /** What the Avg Speed (moving) field shows, placed on its own. */
    private fun avgSpeedMovingField(moving: Flow<MovingTimeAndDistance?>): StateFlow<FieldState?> =
        latest(AvgSpeedField(karooSystem, includePaused = false, moving).liveFlow(karoo.app))

    /** What the HUD's left slot shows, with every slot set to Avg Speed (moving). */
    private fun avgSpeedMovingHUDSlot(
        moving: Flow<MovingTimeAndDistance?>
    ): StateFlow<FieldState?> {
        val slot = HUDSlotConfig(field = HUDSlotField.AvgSpeed(includePaused = false))
        runBlocking {
            karoo.app.updateHUDConfig(HUDScope.Shared) {
                it.copy(slots = HUDSlots(leftSlot = slot, middleSlot = slot, rightSlot = slot))
            }
        }
        return latest(HUDField(karooSystem, moving).liveFlow(karoo.app).map { it.left.field })
    }

    /** Fails unless every one of [shown] reads [expected], naming each that does not. */
    private fun assertShows(expected: String, shown: Map<String, StateFlow<FieldState?>>) {
        val wrong = shown.mapValues { it.value.value?.primary }.filterValues { it != expected }
        assertEquals("expected $expected", emptyMap<String, String?>(), wrong)
    }

    /** A ride at 20.0 km/h, read by the field and by a HUD slot. */
    private fun rideAt20Kph(): Map<String, StateFlow<FieldState?>> {
        val moving = rideShowing(AVG_SPEED_MOVING, HUD)
        val shown =
            mapOf("field" to avgSpeedMovingField(moving), "HUD" to avgSpeedMovingHUDSlot(moving))
        record(RIDE_TIME_MS, RIDE_DISTANCE_M)
        moving.await { it == MovingTimeAndDistance(RIDE_TIME_MS, RIDE_DISTANCE_M) }
        shown.values.forEach { states -> states.await { it.primary == "20.0" } }
        return shown
    }

    // --- Avg Speed (moving) ---

    @Test
    fun avg_speed_moving_survives_a_restarted_timer() {
        val shown = rideAt20Kph()
        record(1_000.0, RIDE_DISTANCE_M)
        awaitStepOn(1_000.0)
        assertShows("20.0", shown)
    }

    @Test
    fun avg_speed_moving_holds_without_a_speed_signal() {
        val shown = rideAt20Kph()
        record(RIDE_TIME_MS + 85_000.0, RIDE_DISTANCE_M, speed = null)
        awaitStepOn(RIDE_TIME_MS + 85_000.0)
        assertShows("20.0", shown)
    }

    @Test
    fun avg_speed_moving_reads_the_karoo_pair_until_ours_exists() {
        val moving = rideShowing("power")
        val shown =
            mapOf("field" to avgSpeedMovingField(moving), "HUD" to avgSpeedMovingHUDSlot(moving))
        // 9 000 m in 1 800 s of recording time.
        record(RIDE_TIME_MS, 9_000.0)
        shown.values.forEach { states -> states.await { it.primary == "18.0" } }
        assertNull(moving.value)
    }

    // --- Speed colour ---

    @Test
    fun the_speed_colour_reads_the_moving_average() {
        runBlocking {
            karoo.app.saveSpeedFieldConfig(
                SpeedFieldConfig(source = SpeedThresholdSource.AVG_MOVING)
            )
        }
        val moving = rideShowing("speed")
        val speed = latest(SpeedField(karooSystem, moving).liveFlow(karoo.app))
        // 21.6 km/h against a 20.0 km/h average: inside the band above it, so both a lost
        // warm-up and a wrong average change the colour.
        record(RIDE_TIME_MS, RIDE_DISTANCE_M, speed = 6.0)
        moving.await { it == MovingTimeAndDistance(RIDE_TIME_MS, RIDE_DISTANCE_M) }
        val above = speed.await { it.primary == "21.6" && it.color != FieldColor.Default }.color

        record(1_000.0, RIDE_DISTANCE_M, speed = 6.0)
        awaitStepOn(1_000.0)
        assertEquals(above, speed.value?.color)
    }
}
