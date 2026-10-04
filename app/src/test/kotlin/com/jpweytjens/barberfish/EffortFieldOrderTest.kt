package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.EffortField
import io.hammerhead.karooext.models.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortFieldOrderTest {

    // Metric profile so distance renders in km and elevation in m (copied from SpeedThresholdTest).
    private val metricProfile =
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

    @Test
    fun `distance first by default, arrow on the climb row`() {
        val s = EffortField.previewStates(metricProfile, climbFirst = false).first()
        assertFalse("distance should not carry the arrow", s.primary.startsWith("↗"))
        assertTrue("climb row carries the arrow", s.secondary!!.startsWith("↗"))
    }

    @Test
    fun `climb first swaps the rows and keeps the arrow on the climb`() {
        val s = EffortField.previewStates(metricProfile, climbFirst = true).first()
        assertTrue("climb now on top, still carries the arrow", s.primary.startsWith("↗"))
        assertFalse("distance now on the bottom row", s.secondary!!.startsWith("↗"))
    }
}
