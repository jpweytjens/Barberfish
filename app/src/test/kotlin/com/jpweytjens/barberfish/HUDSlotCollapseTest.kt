package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.SlotState
import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.toHudSlotSizeConfig
import com.jpweytjens.barberfish.datatype.shared.visibleColumns
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.LabelSize
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HUDSlotCollapseTest {

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

    private val live = SlotState(FieldState("142", "HR", FieldColor.Default), ZoneColorMode.TEXT)
    private val unpaired = SlotState(FieldState.noSensor("HR"), ZoneColorMode.TEXT)
    private val searching = SlotState(FieldState.searching("HR"), ZoneColorMode.TEXT)
    private val notAvailable = SlotState(FieldState.notAvailable("HR"), ZoneColorMode.TEXT)

    private fun hud(columns: Int, vararg slots: SlotState) =
        HUDState(
            columns = columns,
            left = slots[0],
            middle = slots[1],
            right = slots[2],
            fourth = slots.getOrElse(3) { live },
            profile = profile,
        )

    // HUD strip: full width, five rows tall.
    private fun hudViewConfig(textSize: Int = 55) =
        ViewConfig(
            gridSize = 60 to 12,
            viewSize = 480 to 96,
            textSize = textSize,
            alignment = ViewConfig.Alignment.RIGHT,
            boundariesEnabled = true,
            preview = false,
        )

    @Test
    fun `only the noSensor factory marks a state as unpaired`() {
        assertTrue(FieldState.noSensor("HR").noSensor)
        assertFalse(FieldState.searching("HR").noSensor)
        assertFalse(FieldState.notAvailable("HR").noSensor)
        assertFalse(FieldState.idle("HR").noSensor)
        assertFalse(FieldState("142", "HR", FieldColor.Default).noSensor)
    }

    @Test
    fun `all paired keeps every configured column`() {
        assertEquals(listOf(0, 1, 2), hud(3, live, live, live).visibleColumns())
        assertEquals(listOf(0, 1, 2, 3), hud(4, live, live, live, live).visibleColumns())
    }

    @Test
    fun `an unpaired slot drops its column, order preserved`() {
        assertEquals(listOf(0, 2), hud(3, live, unpaired, live).visibleColumns())
        assertEquals(listOf(1, 3), hud(4, unpaired, live, unpaired, live).visibleColumns())
    }

    @Test
    fun `fourth slot is ignored in a 3-column HUD`() {
        assertEquals(listOf(0, 1, 2), hud(3, live, live, live, unpaired).visibleColumns())
    }

    @Test
    fun `searching and not available keep their column`() {
        assertEquals(listOf(0, 1, 2), hud(3, live, searching, notAvailable).visibleColumns())
    }

    @Test
    fun `every slot unpaired shows them all rather than nothing`() {
        assertEquals(listOf(0, 1, 2), hud(3, unpaired, unpaired, unpaired).visibleColumns())
    }

    @Test
    fun `4 and 3 visible columns use the HUD presets`() {
        val design = DataFieldDesignConfig(showIcons = false)
        val four = hudViewConfig().toHudSlotSizeConfig(4, design)
        assertEquals(ViewSizeConfig.HUD_FOUR.valueFontSizeBase, four.valueFontSizeBase)
        assertEquals(ViewSizeConfig.HUD_FOUR.headerFontSize, four.headerFontSize)
        assertFalse(four.showIcons)
        val three = hudViewConfig().toHudSlotSizeConfig(3, design)
        assertEquals(ViewSizeConfig.HUD_THREE.valueFontSizeBase, three.valueFontSizeBase)
        assertEquals(ViewSizeConfig.HUD_THREE.headerFontSize, three.headerFontSize)
    }

    @Test
    fun `2 visible columns size like a native 2-col 5-row field`() {
        val small =
            hudViewConfig()
                .toHudSlotSizeConfig(2, DataFieldDesignConfig(labelSize = LabelSize.SMALL))
        assertEquals(15.5f, small.headerFontSize.value, 0.001f)
        assertEquals(47, small.valueFontSizeBase)
        val large =
            hudViewConfig()
                .toHudSlotSizeConfig(2, DataFieldDesignConfig(labelSize = LabelSize.LARGE))
        assertEquals(17.6f, large.headerFontSize.value, 0.001f)
        assertEquals(41, large.valueFontSizeBase)
    }

    @Test
    fun `1 visible column takes the SDK size for the whole strip`() {
        val one = hudViewConfig(textSize = 55).toHudSlotSizeConfig(1, DataFieldDesignConfig())
        assertEquals(55, one.valueFontSizeBase)
        assertEquals(17.6f, one.headerFontSize.value, 0.001f)
        assertEquals(-3, one.valueTranslationDp)
    }
}
