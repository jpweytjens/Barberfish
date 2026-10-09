package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.shared.GradeReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GradePinTest {

    private val live = flowOf(GradeReading.Unavailable, GradeReading.Fresh(5.0f))

    @Test
    fun noPin_passesTheLiveReadingsThrough() = runBlocking {
        val readings = GradeField.pinnedOrLive(flowOf(null), live).toList()
        assertEquals(listOf(GradeReading.Unavailable, GradeReading.Fresh(5.0f)), readings)
    }

    @Test
    fun pin_replacesTheLiveReadingWithAFreshOneAtThatPercent() = runBlocking {
        val readings = GradeField.pinnedOrLive(flowOf(-4.2f), live).toList()
        assertEquals(listOf(GradeReading.Fresh(-4.2f)), readings)
    }

    @Test
    fun clearingThePin_returnsToTheLiveReadings() = runBlocking {
        val pin = MutableStateFlow<Float?>(-4.2f)
        val readings = mutableListOf<GradeReading>()
        GradeField.pinnedOrLive(pin, live).take(3).collect {
            readings += it
            // The pinned reading arrives first; clearing the pin lets the live readings through.
            if (readings.size == 1) pin.value = null
        }
        assertEquals(
            listOf(GradeReading.Fresh(-4.2f), GradeReading.Unavailable, GradeReading.Fresh(5.0f)),
            readings,
        )
    }
}
