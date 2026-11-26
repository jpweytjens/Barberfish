package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.datatype.shared.showsMovingTimeOrDistance
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingTimeAndDistanceGateTest {
    private val speed = SpeedFieldConfig()
    private val hud = HUDSlots()

    private fun shows(
        ids: Set<String> = emptySet(),
        hud: HUDSlots = this.hud,
        speed: SpeedFieldConfig = this.speed,
    ) = showsMovingTimeOrDistance(ids, hud, speed)

    private fun hudWith(field: HUDSlotField, columns: Int = 3) =
        HUDSlots(columns = columns, leftSlot = HUDSlotConfig(field = field))

    @Test
    fun avg_speed_moving_turns_it_on() {
        assertTrue(shows(setOf("avg-speed-moving")))
    }

    @Test
    fun time_moving_turns_it_on() {
        assertTrue(shows(setOf("time-moving")))
    }

    @Test
    fun remaining_ride_time_turns_it_on() {
        assertTrue(shows(setOf("remaining-ride-time")))
    }

    @Test
    fun time_to_destination_turns_it_on() {
        assertTrue(shows(setOf("time-to-destination")))
    }

    @Test
    fun time_of_arrival_turns_it_on() {
        assertTrue(shows(setOf("time-of-arrival")))
    }

    @Test
    fun speed_coloured_against_the_moving_average_turns_it_on() {
        val coloured =
            speed.copy(threshold = SpeedThresholdConfig(source = SpeedThresholdSource.AVG_MOVING))
        assertTrue(shows(setOf("speed"), speed = coloured))
    }

    @Test
    fun the_three_column_hud_with_a_moving_average_slot_turns_it_on() {
        assertTrue(
            shows(
                setOf("three-column"),
                hud = hudWith(HUDSlotField.AvgSpeed(includePaused = false)),
            )
        )
    }

    @Test
    fun the_three_column_hud_with_a_moving_time_slot_turns_it_on() {
        assertTrue(shows(setOf("three-column"), hud = hudWith(HUDSlotField.Time(TimeKind.RIDING))))
    }

    @Test
    fun the_three_column_hud_with_an_eta_slot_turns_it_on() {
        assertTrue(shows(setOf("three-column"), hud = hudWith(HUDSlotField.ETA())))
    }

    @Test
    fun a_profile_without_these_fields_is_off() {
        assertFalse(shows(setOf("speed", "avg-speed-total", "time-elapsed", "three-column")))
    }

    @Test
    fun speed_with_a_fixed_threshold_is_off() {
        assertFalse(
            shows(
                setOf("speed"),
                speed =
                    speed.copy(
                        threshold = SpeedThresholdConfig(source = SpeedThresholdSource.FIXED)
                    ),
            )
        )
    }

    @Test
    fun the_fourth_hud_slot_counts_only_with_four_columns() {
        val fourth = HUDSlotConfig(field = HUDSlotField.Time(TimeKind.RIDING))
        assertFalse(shows(setOf("three-column"), hud = HUDSlots(columns = 3, fourthSlot = fourth)))
        assertTrue(shows(setOf("three-column"), hud = HUDSlots(columns = 4, fourthSlot = fourth)))
    }

    @Test
    fun avg_speed_total_is_off() {
        assertFalse(
            shows(
                setOf("three-column"),
                hud = hudWith(HUDSlotField.AvgSpeed(includePaused = true)),
            )
        )
    }

    @Test
    fun a_hud_speed_slot_is_off() {
        val coloured =
            speed.copy(threshold = SpeedThresholdConfig(source = SpeedThresholdSource.AVG_MOVING))
        assertFalse(
            shows(setOf("three-column"), hud = hudWith(HUDSlotField.Speed), speed = coloured)
        )
    }
}
