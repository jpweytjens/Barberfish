package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.screens.followRoadHelp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The help line under FOLLOW THE ROAD, word for word. */
class FollowRoadHelpTest {
    private val off =
        "Draws the band along the road in bends, from the Karoo's offline maps. " +
            "Android will ask for access to photos and media: that is where the maps are stored."
    private val waiting = "The band follows the road where the Karoo has offline maps."
    private val following = "Following the road."
    private val noMap = "No offline map for this area; bends are cut straight here."
    private val storage =
        "Storage access is off. Allow it for Barberfish in the Karoo's app settings."
    private val unavailable = "Not available on this Karoo."

    private fun help(
        on: Boolean = true,
        supported: Boolean = true,
        storageGranted: Boolean = true,
        status: RoadFollowStatus = RoadFollowStatus.IDLE,
    ) = followRoadHelp(on, supported, storageGranted, status)

    @Test
    fun off_explains_what_it_does_and_what_android_will_ask() {
        assertEquals(off, help(on = false))
        assertEquals(off, help(on = false, storageGranted = false))
        assertEquals(off, help(on = false, status = RoadFollowStatus.FOLLOWING))
    }

    @Test
    fun on_with_nothing_matched_yet_says_where_it_will_follow() {
        assertEquals(waiting, help(status = RoadFollowStatus.IDLE))
    }

    @Test
    fun on_with_a_road_says_it_is_following() {
        assertEquals(following, help(status = RoadFollowStatus.FOLLOWING))
    }

    @Test
    fun on_with_no_road_anywhere_says_there_is_no_offline_map() {
        assertEquals(noMap, help(status = RoadFollowStatus.NO_MAP))
    }

    @Test
    fun on_without_storage_access_says_where_to_allow_it() {
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
