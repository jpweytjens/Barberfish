package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.renderInputs
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderInputsTest {

    private fun avgHr(bpm: Int) = FieldState("$bpm", "Avg HR", FieldColor.Default)

    private fun renders(states: Flow<FieldState>, design: Flow<String> = flowOf("design")) =
        runBlocking {
            renderInputs(states, design) { false }.toList()
        }

    /** A minute of Avg HR at 1 Hz: 60 updates, but the average moves only 5 times. */
    @Test
    fun aMinuteOfSteadyAverageRendersOncePerChange() {
        val minute = (0 until 60).map { second -> avgHr(140 + second / 12) }

        assertEquals(5, renders(minute.asFlow()).size)
    }

    @Test
    fun aValueThatReturnsRendersAgain() {
        val states = listOf(avgHr(140), avgHr(141), avgHr(140))

        assertEquals(states, renders(states.asFlow()).map { it.first })
    }

    @Test
    fun aDesignChangeRendersTheSameStateAgain() {
        val states = flow {
            emit(avgHr(140))
            delay(50)
        }
        val design = flow {
            emit("small labels")
            delay(20)
            emit("large labels")
        }

        assertEquals(
            listOf("small labels", "large labels"),
            renders(states, design).map { it.second },
        )
    }

    /** A renderer that falls behind skips to the newest state instead of drawing a backlog. */
    @Test
    fun aSlowRendererSkipsToTheLatestState() {
        val burst = (0 until 60).map { avgHr(100 + it) }
        val drawn = runBlocking {
            renderInputs(burst.asFlow(), flowOf("design")) { false }
                .onEach { delay(5) }
                .toList()
                .map { it.first }
        }

        assertEquals(burst.last(), drawn.last())
        assertTrue("drew ${drawn.size} of 60", drawn.size < burst.size)
    }

    /** A steady value still repaints when the theme flips, since the render reads night mode. */
    @Test
    fun aThemeFlipRendersTheSameStateAgain() {
        var night = false
        val states = flow {
            emit(avgHr(140))
            night = true
            emit(avgHr(140))
            emit(avgHr(140))
        }

        assertEquals(
            2,
            runBlocking { renderInputs(states, flowOf("design")) { night }.toList() }.size,
        )
    }
}
