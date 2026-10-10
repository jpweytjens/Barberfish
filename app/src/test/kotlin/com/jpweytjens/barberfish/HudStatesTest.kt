package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.hudStates
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.slots
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudStatesTest {

    private val profile =
        UserProfile(
            weight = 70f,
            preferredUnit =
                UserProfile.PreferredUnit(
                    distance = UserProfile.PreferredUnit.UnitType.METRIC,
                    elevation = UserProfile.PreferredUnit.UnitType.METRIC,
                    temperature = UserProfile.PreferredUnit.UnitType.METRIC,
                    weight = UserProfile.PreferredUnit.UnitType.METRIC,
                ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList(),
        )

    /** Runs the HUD's states once and returns the slots whose sources were subscribed. */
    private fun subscribedSlots(cfg: HUDConfig): List<HUDSlotField> = runBlocking {
        val subscribed = mutableListOf<HUDSlotField>()
        hudStates(cfg, profile) { slot ->
                flow {
                    subscribed += slot.field
                    emit(FieldState(slot.field.toString(), "", FieldColor.Default))
                }
            }
            .first()
        subscribed
    }

    private val cfg =
        HUDConfig(
            leftSlot = HUDSlotConfig(field = HUDSlotField.Speed),
            middleSlot = HUDSlotConfig(field = HUDSlotField.HR),
            rightSlot = HUDSlotConfig(field = HUDSlotField.Power),
            fourthSlot = HUDSlotConfig(field = HUDSlotField.Cadence),
        )

    @Test
    fun a_three_column_hud_never_subscribes_its_fourth_slot() {
        val subscribed = subscribedSlots(cfg.copy(columns = 3))
        assertEquals(3, subscribed.size)
        assertFalse(HUDSlotField.Cadence in subscribed)
    }

    @Test
    fun a_four_column_hud_subscribes_all_four() {
        val subscribed = subscribedSlots(cfg.copy(columns = 4))
        assertEquals(4, subscribed.size)
        assertTrue(HUDSlotField.Cadence in subscribed)
    }

    @Test
    fun the_shown_slots_carry_their_sources_state() = runBlocking {
        val state =
            hudStates(cfg.copy(columns = 4), profile) { slot ->
                    flow { emit(FieldState(slot.field.toString(), "", FieldColor.Default)) }
                }
                .first()
        assertEquals(
            listOf("Speed", "HR", "Power", "Cadence"),
            state.slots.map { it.field.primary },
        )
    }
}
