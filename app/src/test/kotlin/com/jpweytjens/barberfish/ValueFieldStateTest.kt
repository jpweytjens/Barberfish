package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.ValueField
import com.jpweytjens.barberfish.datatype.ValueKind
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the placeholder vocabulary of route-gated value fields: "No route" when the stream or value
 * is absent, "Off route" when the rider has left the loaded route, and the generic "Not available"
 * for the non-route-gated Distance field.
 */
class ValueFieldStateTest {

    // Metric profile (copied from SpeedThresholdTest).
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

    private fun streaming(kind: ValueKind, values: Map<String, Double>) =
        StreamState.Streaming(DataPoint(kind.sourceType, values))

    @Test
    fun `route kind not available maps to no route`() {
        val s =
            ValueField.toFieldState(
                StreamState.NotAvailable,
                ValueKind.DISTANCE_REMAINING,
                metricProfile,
            )
        assertEquals("No route", s.primary)
    }

    @Test
    fun `route kind off route maps to off route`() {
        val kind = ValueKind.DISTANCE_REMAINING
        val s =
            ValueField.toFieldState(
                streaming(kind, mapOf(DataType.Field.ON_ROUTE to 0.0)),
                kind,
                metricProfile,
            )
        assertEquals("Off route", s.primary)
    }

    @Test
    fun `route kind missing value maps to no route`() {
        val kind = ValueKind.DISTANCE_REMAINING
        val s = ValueField.toFieldState(streaming(kind, emptyMap()), kind, metricProfile)
        assertEquals("No route", s.primary)
    }

    @Test
    fun `route kind on route with value renders the value`() {
        val kind = ValueKind.DISTANCE_REMAINING
        val s =
            ValueField.toFieldState(
                streaming(
                    kind,
                    mapOf(
                        DataType.Field.ON_ROUTE to 1.0,
                        kind.fieldId to 42_100.0,
                    ),
                ),
                kind,
                metricProfile,
            )
        assertEquals("42.1", s.primary)
    }

    @Test
    fun `non-route kind keeps the generic not available`() {
        val s = ValueField.toFieldState(StreamState.NotAvailable, ValueKind.DISTANCE, metricProfile)
        assertEquals("Not available", s.primary)
    }
}
