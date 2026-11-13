package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.FollowedRoute
import com.jpweytjens.barberfish.datatype.shared.sparklineInputs
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
                )
                .toList()

        // 20 input events over 10 s: one draw per second, not one per event.
        assertEquals(10, ticks.size)
    }
}
