package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.screens.followRoadHelp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The help line under FOLLOW THE ROAD, word for word. */
class FollowRoadHelpTest {
    private val explanation = "Closely takes the road's shape from the Karoo's offline maps."
    private val willAsk =
        "$explanation Android will ask for access to photos and media, where the maps are stored."
    private val noMap = "No offline map for this route, so the band follows it loosely."
    private val storage =
        "Storage access is off: allow it for Barberfish in the Karoo's app settings."
    private val unavailable = "Closely is not available on this Karoo."

    private fun help(
        on: Boolean = true,
        supported: Boolean = true,
        storageGranted: Boolean = true,
        status: RoadFollowStatus = RoadFollowStatus.IDLE,
    ) = followRoadHelp(on, supported, storageGranted, status)

    @Test
    fun with_access_the_explanation_is_the_same_on_loosely_and_closely() {
        assertEquals(explanation, help(on = false))
        assertEquals(explanation, help(on = true, status = RoadFollowStatus.IDLE))
        assertEquals(explanation, help(on = true, status = RoadFollowStatus.FOLLOWING))
    }

    @Test
    fun on_loosely_without_access_it_adds_what_android_will_ask() {
        RoadFollowStatus.entries.forEach {
            assertEquals(willAsk, help(on = false, storageGranted = false, status = it))
        }
    }

    @Test
    fun on_loosely_a_route_without_a_map_is_not_reported() {
        assertEquals(explanation, help(on = false, status = RoadFollowStatus.NO_MAP))
    }

    @Test
    fun on_closely_with_no_road_anywhere_says_there_is_no_offline_map() {
        assertEquals(noMap, help(status = RoadFollowStatus.NO_MAP))
    }

    @Test
    fun on_closely_without_storage_access_says_where_to_allow_it() {
        assertEquals(storage, help(storageGranted = false))
    }

    @Test
    fun storage_wins_over_the_status() {
        RoadFollowStatus.entries.forEach {
            assertEquals(storage, help(storageGranted = false, status = it))
        }
    }

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
}
