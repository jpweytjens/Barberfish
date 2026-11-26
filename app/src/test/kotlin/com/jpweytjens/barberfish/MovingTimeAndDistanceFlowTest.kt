package com.jpweytjens.barberfish

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.shared.MovingState
import com.jpweytjens.barberfish.datatype.shared.MovingTimeAndDistance
import com.jpweytjens.barberfish.datatype.shared.RidePhase
import com.jpweytjens.barberfish.datatype.shared.RideReadings
import com.jpweytjens.barberfish.datatype.shared.movingTimeTypeIds
import com.jpweytjens.barberfish.datatype.shared.showsMovingTimeOrDistance
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import com.jpweytjens.barberfish.extension.barberfishDataTypes
import com.jpweytjens.barberfish.extension.movingTimeAndDistanceFlow
import com.jpweytjens.barberfish.karoo.clearBarberfishSettings
import com.jpweytjens.barberfish.karoo.startBarberfish
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.RideState
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
class MovingTimeAndDistanceFlowTest {
    @get:Rule val karoo = FakeKarooRule()

    /** Hand-fed inputs to the flow, and what it emitted and tried to save. */
    private class Run(private val scope: TestScope) {
        val enabled = MutableSharedFlow<Boolean>()
        val phases = MutableSharedFlow<RidePhase>()
        val readings = MutableSharedFlow<RideReadings?>()
        val ticks = MutableSharedFlow<Unit>()
        val retries = MutableSharedFlow<Unit>()
        val shutdownNotices = MutableSharedFlow<Unit>()
        val emitted = mutableListOf<MovingTimeAndDistance?>()

        /** Every call of `save`, the failing ones included. */
        val saves = mutableListOf<MovingState?>()

        /** How many of the next `save` calls throw. */
        var failingSaves = 0

        suspend fun enable(on: Boolean = true) = send { enabled.emit(on) }

        suspend fun phase(phase: RidePhase) = send { phases.emit(phase) }

        suspend fun read(
            recordingTimeMs: Double,
            distanceM: Double,
            hasSpeedReading: Boolean = true,
        ) = send { readings.emit(RideReadings(recordingTimeMs, distanceM, hasSpeedReading)) }

        suspend fun noReadings() = send { readings.emit(null) }

        suspend fun tick(times: Int = 1) = repeat(times) { send { ticks.emit(Unit) } }

        suspend fun retry() = send { retries.emit(Unit) }

        suspend fun shutdownNotice() = send { shutdownNotices.emit(Unit) }

        private suspend fun send(input: suspend () -> Unit) {
            input()
            scope.runCurrent()
        }
    }

    private fun ride(initial: MovingState? = null, body: suspend Run.() -> Unit) = runTest {
        val run = Run(this)
        val collector =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                movingTimeAndDistanceFlow(
                        initial = initial,
                        enabled = run.enabled,
                        phases = run.phases,
                        readings = run.readings,
                        ticks = run.ticks,
                        retries = run.retries,
                        shutdownNotices = run.shutdownNotices,
                        save = { state ->
                            run.saves += state
                            if (run.failingSaves > 0) {
                                run.failingSaves--
                                throw IOException("disk full")
                            }
                        },
                    )
                    .collect { run.emitted += it }
            }
        runCurrent()
        run.body()
        collector.cancel()
    }

    /** A ride recording with the gate on, one tick in at 60 s and 300 m. */
    private suspend fun Run.recording() {
        enable()
        phase(RidePhase.RECORDING)
        read(60_000.0, 300.0)
        tick()
    }

    @Test
    fun emits_null_until_a_ride_has_readings() = ride {
        enable()
        phase(RidePhase.RECORDING)
        tick()
        assertNull(emitted.first())
        read(60_000.0, 300.0)
        tick()
        assertEquals(MovingTimeAndDistance(60_000.0, 300.0), emitted.last())
    }

    @Test
    fun saves_once_per_tick_while_recording() = ride {
        enable()
        phase(RidePhase.RECORDING)
        for (second in 1..5) {
            read(second * 1_000.0, second * 8.0)
            tick()
        }
        assertEquals(5, saves.size)
    }

    @Test
    fun does_not_save_while_paused() = ride {
        recording()
        phase(RidePhase.PAUSED)
        val before = saves.size
        tick(5)
        assertEquals(before, saves.size)
    }

    @Test
    fun a_pause_saves_at_once() = ride {
        recording()
        assertEquals(1, saves.size)
        read(61_000.0, 308.0)
        phase(RidePhase.PAUSED)
        assertEquals(2, saves.size)
        assertEquals(61_000.0, saves.last()?.recordingTimeMs)
        assertEquals(308.0, saves.last()?.distanceM)
    }

    @Test
    fun an_unchanged_pause_saves_nothing_more() = ride {
        recording()
        read(61_000.0, 308.0)
        phase(RidePhase.PAUSED)
        val before = saves.size
        tick(5)
        phase(RidePhase.PAUSED)
        assertEquals(before, saves.size)
    }

    @Test
    fun saves_at_the_shutdown_notice() = ride {
        recording()
        read(61_000.0, 308.0)
        shutdownNotice()
        assertEquals(2, saves.size)
        assertEquals(61_000.0, saves.last()?.recordingTimeMs)
        assertEquals(308.0, saves.last()?.distanceM)
    }

    @Test
    fun a_failing_save_does_not_stop_the_flow() = ride {
        failingSaves = 1
        recording()
        read(61_000.0, 308.0)
        tick()
        assertEquals(MovingTimeAndDistance(61_000.0, 308.0), emitted.last())
    }

    @Test
    fun a_failed_save_is_retried_while_the_state_is_unchanged() = ride {
        failingSaves = 1
        recording()
        val failed = saves.single()
        phase(RidePhase.PAUSED)
        tick()
        tick()
        assertEquals(listOf(failed, failed), saves)
    }

    @Test
    fun a_failed_clear_is_retried_with_the_gate_off() = ride {
        recording()
        failingSaves = 2
        phase(RidePhase.IDLE)
        enable(false)
        tick(4)
        assertEquals("ticks are off with the gate", listOf<MovingState?>(null), saves.drop(1))
        retry()
        retry()
        assertEquals(listOf<MovingState?>(null, null, null), saves.drop(1))
    }

    @Test
    fun retries_run_only_while_a_save_is_owed() = ride {
        recording()
        assertEquals("nothing is owed", 0, retries.subscriptionCount.value)
        failingSaves = 1
        phase(RidePhase.IDLE)
        assertEquals("the clear failed", 1, retries.subscriptionCount.value)
        retry()
        assertEquals("the clear is stored", 0, retries.subscriptionCount.value)
    }

    @Test
    fun the_gate_going_off_and_on_adds_the_gap() =
        ride(initial = MovingState(50_000.0, 300.0, 60_000.0, 300.0)) {
            recording()
            enable(false)
            read(70_000.0, 350.0)
            val before = saves.size
            tick(30)
            assertEquals("no step while the gate is off", before, saves.size)
            enable()
            read(95_000.0, 510.0)
            tick()
            assertEquals(MovingTimeAndDistance(85_000.0, 510.0), emitted.last())
        }

    @Test
    fun no_tick_is_taken_while_the_gate_is_off() = ride {
        phase(RidePhase.RECORDING)
        assertEquals("ticks before the gate is on", 0, ticks.subscriptionCount.value)
        enable()
        assertEquals("ticks while the gate is on", 1, ticks.subscriptionCount.value)
        enable(false)
        assertEquals("ticks after the gate went off", 0, ticks.subscriptionCount.value)
    }

    @Test
    fun ride_boundaries_apply_while_the_gate_is_off() =
        ride(initial = MovingState(50_000.0, 300.0, 60_000.0, 300.0)) {
            phase(RidePhase.RECORDING)
            phase(RidePhase.IDLE)
            assertEquals(listOf<MovingState?>(null), saves)
        }

    @Test
    fun the_end_of_a_ride_saves_null() = ride {
        recording()
        phase(RidePhase.IDLE)
        assertEquals(2, saves.size)
        assertNull(saves.last())
    }

    @Test
    fun emits_null_while_the_gate_is_off() = ride {
        recording()
        assertEquals(MovingTimeAndDistance(60_000.0, 300.0), emitted.last())
        enable(false)
        tick()
        assertNull(emitted.last())
        assertFalse(null in saves)
    }

    @Test
    fun a_stream_gap_holds_the_pair() = ride {
        recording()
        repeat(10) {
            noReadings()
            tick()
        }
        val pair = MovingTimeAndDistance(60_000.0, 300.0)
        assertEquals(pair, emitted.last())
        assertFalse(null in emitted.dropWhile { it == null })
    }

    @Test
    fun a_stored_pair_is_emitted_before_any_reading() {
        val initial = MovingState(50_000.0, 300.0, 60_000.0, 300.0)
        ride(initial) {
            enable()
            phase(RidePhase.PAUSED)
            tick()
            assertEquals(initial.moving, emitted.filterNotNull().first())
        }
    }

    @Test
    fun every_id_the_gate_reads_is_a_registered_field() {
        val types = barberfishDataTypes(KarooSystemService(karoo.app), MutableStateFlow(null))
        val registered = types.map { it.typeId }.toSet()
        assertEquals(emptySet<String>(), movingTimeTypeIds - registered)

        // The gate matches the Speed field and the HUD by their own ids, next to the colour and
        // slot checks they need, so ask it with the ids the fields register.
        val speedId = types.filterIsInstance<SpeedField>().single().typeId
        val hudId = types.filterIsInstance<HUDField>().single().typeId
        val movingSpeed =
            SpeedFieldConfig(
                threshold = SpeedThresholdConfig(source = SpeedThresholdSource.AVG_MOVING)
            )
        val movingHud =
            HUDSlots(leftSlot = HUDSlotConfig(field = HUDSlotField.AvgSpeed(includePaused = false)))
        assertTrue(showsMovingTimeOrDistance(setOf(speedId), HUDSlots(), movingSpeed))
        assertTrue(showsMovingTimeOrDistance(setOf(hudId), movingHud, SpeedFieldConfig()))
    }

    @Test
    fun the_streams_exist_only_while_a_ride_shows_the_fields() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        clearBarberfishSettings(app)
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        karoo.system.setActiveRideProfile(profileWith("power"))
        karoo.system.setRideState(RideState.Recording)
        RobolectricPump.advanceBy(1.seconds)
        karoo.startBarberfish()
        karoo.awaitConsumer(RideState.Params)
        assertNoStreamsFor(2.seconds)

        karoo.system.setActiveRideProfile(profileWith("time-moving"))
        karoo.system.setRideState(RideState.Idle)
        assertNoStreamsFor(2.seconds)

        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(*rideStreams)
    }

    private val rideStreams =
        arrayOf(DataType.Type.ELAPSED_TIME, DataType.Type.DISTANCE, DataType.Type.SPEED)

    /**
     * Watches the ride streams for [duration] of wall time and fails the moment one is subscribed.
     * Wall time, because the extension's flows run on real threads that a looper pump does not wait
     * for.
     */
    private fun assertNoStreamsFor(duration: Duration) {
        val step = 50.milliseconds
        repeat((duration / step).toInt()) {
            Thread.sleep(step.inWholeMilliseconds)
            RobolectricPump.pumpMainLooper()
            rideStreams.forEach {
                assertFalse("$it was subscribed", karoo.system.hasStreamConsumer(it))
            }
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
