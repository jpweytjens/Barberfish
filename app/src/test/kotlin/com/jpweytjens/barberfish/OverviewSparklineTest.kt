package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.OVERVIEW_PAD_PX
import com.jpweytjens.barberfish.datatype.shared.overviewToX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overview frame is the whole route, so the position dot reaches both ends of the bitmap on
 * every ride: x must stay a dot radius clear of each edge.
 */
class OverviewSparklineTest {

    // Narrowest plausible overview cell through a full-width one.
    private val cellWidthsPx = listOf(160, 240, 480)

    private val START_M = 97.1f
    private val SPAN_M = 42_000f

    private fun toX(d: Float, widthPx: Int) = overviewToX(d, START_M, SPAN_M, widthPx)

    @Test
    fun route_ends_clear_the_dot_pad() {
        for (widthPx in cellWidthsPx) {
            val startX = toX(START_M, widthPx)
            val endX = toX(START_M + SPAN_M, widthPx)
            assertEquals("${widthPx}px start", OVERVIEW_PAD_PX, startX, 0.01f)
            assertEquals("${widthPx}px end", widthPx - OVERVIEW_PAD_PX, endX, 0.01f)
            assertTrue("${widthPx}px start", startX >= OVERVIEW_PAD_PX)
            assertTrue("${widthPx}px end", endX <= widthPx - OVERVIEW_PAD_PX)
        }
    }

    @Test
    fun mapping_stays_proportional_between_the_ends() {
        for (widthPx in cellWidthsPx) {
            val usable = widthPx - 2 * OVERVIEW_PAD_PX
            assertEquals(
                "${widthPx}px midpoint",
                OVERVIEW_PAD_PX + usable / 2f,
                toX(START_M + SPAN_M / 2f, widthPx),
                0.01f,
            )
            assertEquals(
                "${widthPx}px quarter",
                OVERVIEW_PAD_PX + usable / 4f,
                toX(START_M + SPAN_M / 4f, widthPx),
                0.01f,
            )
        }
    }

    @Test
    fun mapping_is_monotonic_across_the_route() {
        for (widthPx in cellWidthsPx) {
            var previous = -1f
            var d = START_M
            while (d <= START_M + SPAN_M) {
                val x = toX(d, widthPx)
                assertTrue("${widthPx}px at ${d}m: $x", x > previous)
                assertTrue("${widthPx}px at ${d}m: $x", x in 0f..widthPx.toFloat())
                previous = x
                d += SPAN_M / 40f
            }
        }
    }
}
