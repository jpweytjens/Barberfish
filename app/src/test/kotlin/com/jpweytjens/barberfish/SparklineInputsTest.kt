package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.FollowedRoute
import com.jpweytjens.barberfish.datatype.shared.RejoinView
import com.jpweytjens.barberfish.datatype.shared.rejoinView
import com.jpweytjens.barberfish.datatype.shared.rejoinWindow
import com.jpweytjens.barberfish.datatype.shared.sparklineInputs
import com.jpweytjens.barberfish.extension.RejoinRoad
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.ZoneConfig
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnGlobalPOIs
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SparklineInputsTest {

    /** [count] values of [make], one per second, starting [offsetMs] in. */
    private fun <T> everySecond(count: Int, offsetMs: Long, make: (Int) -> T): Flow<T> = flow {
        delay(offsetMs)
        repeat(count) {
            emit(make(it))
            delay(1000)
        }
    }

    @Test
    fun navigationAndDistanceOutOfStepDrawOncePerSecond() = runTest {
        val nav = everySecond<FollowedRoute?>(10, offsetMs = 0) { null }
        val dist =
            everySecond(10, offsetMs = 500) {
                StreamState.Streaming(
                    DataPoint(DataType.Type.DISTANCE_TO_DESTINATION, mapOf("FIELD" to 1000.0 - it))
                )
            }

        val ticks =
            sparklineInputs(
                    nav,
                    dist,
                    flowOf(ZoneConfig()),
                    flowOf(SparklineConfig()),
                    flowOf(OnGlobalPOIs(emptyList())),
                    flowOf<RejoinRoad?>(null),
                )
                .toList()

        // 20 input events over 10 s: one draw per second, not one per event.
        assertEquals(10, ticks.size)
    }

    // --- the rejoin view ---

    private fun positionOn917(rejoinDistanceM: Double?) =
        rejoinView(917f, rejoinDistanceM, lookaheadM = 5_000f, widthPx = 256).positionM

    @Test
    fun the_rider_is_the_line_length_minus_the_rejoin_distance_along_it() {
        assertEquals(0f, positionOn917(917.0), 0f)
        assertEquals(517f, positionOn917(400.0), 0f)
        assertEquals(917f, positionOn917(0.0), 0f)
    }

    @Test
    fun a_missing_or_out_of_range_rejoin_distance_stays_on_the_line() {
        // Unknown: the line's start.
        assertEquals(0f, positionOn917(null), 0f)
        assertEquals(917f, positionOn917(-50.0), 0f)
        assertEquals(0f, positionOn917(2_000.0), 0f)
    }

    @Test
    fun a_line_shorter_than_the_lookahead_is_fitted_to_the_strip() {
        assertEquals(
            RejoinView(positionM = 0f, windowOverride = rejoinWindow(917f, 256)),
            rejoinView(917f, 917.0, lookaheadM = 5_000f, widthPx = 256),
        )
    }

    @Test
    fun a_line_as_long_as_the_lookahead_or_longer_scrolls_like_the_route() {
        assertNull(rejoinView(5_000f, 5_000.0, lookaheadM = 5_000f, widthPx = 256).windowOverride)
        assertNull(
            rejoinView(12_000f, 12_000.0, lookaheadM = 10_000f, widthPx = 256).windowOverride
        )
    }
}
