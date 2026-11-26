package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.MovingState
import com.jpweytjens.barberfish.datatype.shared.MovingTimeAndDistance
import com.jpweytjens.barberfish.datatype.shared.MovingTracker
import com.jpweytjens.barberfish.datatype.shared.RidePhase
import com.jpweytjens.barberfish.datatype.shared.RideReadings
import com.jpweytjens.barberfish.datatype.shared.next
import com.jpweytjens.barberfish.datatype.shared.onShutdownNotice
import com.jpweytjens.barberfish.datatype.shared.step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MovingTimeAndDistanceTest {

    private fun readings(timeMs: Double, distanceM: Double, speed: Boolean) =
        RideReadings(timeMs, distanceM, speed)

    private fun state(timeMs: Double, distanceM: Double) =
        MovingState(timeMs, distanceM, timeMs, distanceM)

    private fun activeRide(s: MovingState) =
        MovingTracker(state = s).next(RidePhase.RECORDING, null)

    @Test
    fun a_healthy_restore_keeps_the_pair() {
        val result = state(456_000.0, 503.6).step(readings(453_000.0, 499.4, speed = true))

        assertEquals(MovingTimeAndDistance(456_000.0, 503.6), result.moving)
        assertEquals(453_000.0, result.recordingTimeMs, 0.0)
        assertEquals(499.4, result.distanceM, 0.0)
    }

    @Test
    fun a_restarted_timer_continues_from_the_stored_time() {
        val first = state(3_360_000.0, 16_093.4).step(readings(1_000.0, 16_093.4, speed = true))
        val second = first.step(readings(721_000.0, 19_500.0, speed = true))

        assertEquals(3_360_000.0, first.moving.timeMs, 0.0)
        assertEquals(4_080_000.0, second.moving.timeMs, 0.0)
    }

    @Test
    fun time_without_speed_or_distance_does_not_count() {
        var s = state(0.0, 0.0)
        for (tick in 1..85) s = s.step(readings(tick * 1_000.0, 0.0, speed = false))

        assertEquals(0.0, s.moving.timeMs, 0.0)
    }

    @Test
    fun time_starts_at_the_first_metre() {
        var s = state(0.0, 0.0)
        for (tick in 1..100) s = s.step(readings(tick * 1_000.0, 0.0, speed = false))
        s = s.step(readings(101_000.0, 1.0, speed = false))

        assertEquals(1_000.0, s.moving.timeMs, 0.0)
        assertEquals(1.0, s.moving.distanceM, 0.0)
    }

    @Test
    fun standing_with_a_speed_reading_counts() {
        val s = state(10_000.0, 50.0).step(readings(11_000.0, 50.0, speed = true))

        assertEquals(11_000.0, s.moving.timeMs, 0.0)
    }

    @Test
    fun a_new_ride_starts_from_the_readings() {
        val ended = activeRide(state(900_000.0, 5_000.0)).next(RidePhase.IDLE, null)
        val result = ended.next(RidePhase.RECORDING, readings(0.0, 2.05, speed = true))

        assertEquals(MovingTimeAndDistance(0.0, 2.05), result.state?.moving)
    }

    @Test
    fun idle_after_boot_keeps_the_stored_state() {
        val s = state(900_000.0, 5_000.0)

        assertEquals(s, MovingTracker(state = s).next(RidePhase.IDLE, null).state)
    }

    @Test
    fun a_restored_ride_continues_from_the_stored_state() {
        val s = state(900_000.0, 5_000.0)
        val result =
            MovingTracker(state = s)
                .next(RidePhase.IDLE, null)
                .next(RidePhase.PAUSED, null)
                .next(RidePhase.PAUSED, readings(s.recordingTimeMs, s.distanceM, speed = false))

        assertEquals(s.moving, result.state?.moving)
    }

    @Test
    fun no_readings_take_no_step() {
        val s = state(900_000.0, 5_000.0)
        var tracker = MovingTracker(state = s)
        repeat(20) { tracker = tracker.next(RidePhase.PAUSED, null) }

        assertEquals(s, tracker.state)
    }

    @Test
    fun the_end_of_a_ride_clears_the_state() {
        val result = activeRide(state(900_000.0, 5_000.0)).next(RidePhase.IDLE, null)

        assertNull(result.state)
        assertFalse(result.rideSeenActive)
    }

    @Test
    fun idle_after_the_shutdown_notice_keeps_the_state() {
        val result =
            activeRide(state(900_000.0, 5_000.0)).onShutdownNotice().next(RidePhase.IDLE, null)

        assertNotNull(result.state)
    }

    @Test
    fun an_extension_restart_adds_the_gap_when_distance_moved() {
        val s = state(900_000.0, 5_000.0)
        val result =
            MovingTracker(state = s)
                .next(
                    RidePhase.RECORDING,
                    readings(s.recordingTimeMs + 600_000.0, s.distanceM + 3_000.0, speed = false),
                )

        assertEquals(
            MovingTimeAndDistance(s.moving.timeMs + 600_000.0, s.moving.distanceM + 3_000.0),
            result.state?.moving,
        )
    }

    @Test
    fun a_ride_first_seen_mid_way_starts_from_the_readings() {
        val result =
            MovingTracker().next(RidePhase.RECORDING, readings(900_000.0, 5_000.0, speed = true))

        assertEquals(MovingTimeAndDistance(900_000.0, 5_000.0), result.state?.moving)
    }
}
