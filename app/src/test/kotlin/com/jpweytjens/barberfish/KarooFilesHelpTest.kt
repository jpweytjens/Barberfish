package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.screens.karooFilesHelp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The help line under OFFLINE MAPS, word for word. */
class KarooFilesHelpTest {
    private val explanation =
        "Reads the Karoo's offline maps. The grade map follows the road through bends, and off " +
            "the route the grade map and Profile show the climb back to it."
    private val willAsk =
        "$explanation Android will ask for access to photos and media, where the maps are stored."
    private val noMap = "No offline map for this route, so the grade map keeps to the route line."
    private val storage =
        "Storage access is off: allow it for Barberfish in the Karoo's app settings."
    private val unavailable = "Not available on this Karoo."

    private fun help(
        on: Boolean = true,
        supported: Boolean = true,
        storageGranted: Boolean = true,
        status: RoadFollowStatus = RoadFollowStatus.IDLE,
    ) = karooFilesHelp(on, supported, storageGranted, status)

    @Test
    fun not_supported_wins_over_everything() {
        for (on in listOf(true, false)) {
            for (granted in listOf(true, false)) {
                RoadFollowStatus.entries.forEach {
                    assertEquals(unavailable, help(on, supported = false, granted, it))
                }
            }
        }
    }

    @Test
    fun on_without_storage_access_says_where_to_allow_it() {
        assertEquals(storage, help(storageGranted = false))
    }

    @Test
    fun on_with_no_road_anywhere_says_there_is_no_offline_map() {
        assertEquals(noMap, help(status = RoadFollowStatus.NO_MAP))
    }

    @Test
    fun off_without_access_it_adds_what_android_will_ask() {
        RoadFollowStatus.entries.forEach {
            assertEquals(willAsk, help(on = false, storageGranted = false, status = it))
        }
    }

    @Test
    fun with_access_the_explanation_is_the_same_off_and_on() {
        assertEquals(explanation, help(on = false))
        assertEquals(explanation, help(on = true, status = RoadFollowStatus.IDLE))
        assertEquals(explanation, help(on = true, status = RoadFollowStatus.FOLLOWING))
    }

    @Test
    fun off_a_route_without_a_map_is_not_reported() {
        assertEquals(explanation, help(on = false, status = RoadFollowStatus.NO_MAP))
    }

    @Test
    fun storage_wins_over_the_status() {
        RoadFollowStatus.entries.forEach {
            assertEquals(storage, help(storageGranted = false, status = it))
        }
    }
}
