package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.hudRenderInputs
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.datatype.shared.HUDState
import com.jpweytjens.barberfish.datatype.shared.SlotState
import com.jpweytjens.barberfish.datatype.shared.SparklineFrame
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HudRenderInputsTest {

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

    private fun hud(speed: Int): HUDState {
        fun slot(value: String) =
            SlotState(FieldState(value, "", FieldColor.Default), ZoneColorMode.TEXT)
        return HUDState(3, slot("$speed"), slot("140"), slot("250"), slot("0"), profile)
    }

    /** Slot updates four times a second for 10 s, as fast sensors deliver them. */
    private fun fourPerSecond(value: (Int) -> Int): Flow<HUDState> = flow {
        repeat(40) {
            emit(hud(value(it)))
            delay(250)
        }
    }

    @Test
    fun changingSlotsRenderOncePerSecond() = runTest {
        val renders =
            hudRenderInputs(
                    fourPerSecond { 25 + it },
                    flowOf(SparklineFrame.OFF),
                    flowOf(DataFieldDesignConfig()),
                    flowOf(null),
                )
                .toList()

        assertEquals(10, renders.size)
    }

    @Test
    fun aSteadyHudWithNoRouteRendersOnce() = runTest {
        val renders =
            hudRenderInputs(
                    fourPerSecond { 25 },
                    flowOf(SparklineFrame.OFF),
                    flowOf(DataFieldDesignConfig()),
                    flowOf(null),
                )
                .toList()

        assertEquals(1, renders.size)
    }

    @Test
    fun aTapShowsAtOnceNotOnTheNextTick() = runTest {
        val taps = flow {
            emit(null)
            delay(2500)
            emit(7)
            delay(2000)
            emit(null)
        }
        val renders =
            hudRenderInputs(
                    fourPerSecond { 25 },
                    flowOf(SparklineFrame.OFF),
                    flowOf(DataFieldDesignConfig()),
                    taps,
                )
                .map { testScheduler.currentTime to it.transitionKm }
                .toList()

        assertEquals(listOf(1000L to null, 2500L to 7, 4500L to null), renders)
    }
}
