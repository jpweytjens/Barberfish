package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.APPROACH_LARGE_M
import com.jpweytjens.barberfish.datatype.shared.APPROACH_MEDIUM_M
import com.jpweytjens.barberfish.datatype.shared.APPROACH_SMALL_M
import com.jpweytjens.barberfish.datatype.shared.climbApproachM
import com.jpweytjens.barberfish.datatype.shared.pcsClimbScore
import org.junit.Assert.assertEquals
import org.junit.Test

class ClimbScoreTest {

    @Test
    fun score_of_1km_at_8pct_is_16() {
        assertEquals(16.0, pcsClimbScore(gradePct = 8.0, lengthM = 1000.0), 0.001)
    }

    @Test
    fun score_scales_with_length_and_grade_squared() {
        // 2 km at 6%: (3)^2 * 2 = 18
        assertEquals(18.0, pcsClimbScore(gradePct = 6.0, lengthM = 2000.0), 0.001)
        // 10 km at 7%: (3.5)^2 * 10 = 122.5
        assertEquals(122.5, pcsClimbScore(gradePct = 7.0, lengthM = 10_000.0), 0.001)
    }

    @Test
    fun approach_tier_boundaries() {
        assertEquals(APPROACH_SMALL_M, climbApproachM(15.9))
        assertEquals(APPROACH_MEDIUM_M, climbApproachM(16.0))
        assertEquals(APPROACH_MEDIUM_M, climbApproachM(49.9))
        assertEquals(APPROACH_LARGE_M, climbApproachM(50.0))
    }

    @Test
    fun tiny_bump_is_small_tier() {
        // 500 m at 5%: (2.5)^2 * 0.5 = 3.125
        assertEquals(APPROACH_SMALL_M, climbApproachM(pcsClimbScore(5.0, 500.0)))
    }

    @Test
    fun hc_climb_is_large_tier() {
        // ~14 km at 8%: (4)^2 * 14 = 224
        assertEquals(APPROACH_LARGE_M, climbApproachM(pcsClimbScore(8.0, 14_000.0)))
    }
}
