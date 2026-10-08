package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.headwindComponent
import com.jpweytjens.barberfish.datatype.shared.relativeWindDeg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindProjectionTest {

    /**
     * The Headwind extension's own projection, ported from karoo-headwind (Apache-2.0) at ab54194:
     * util/AngleDifference.kt and the startStream bodies of HeadwindDirectionDataType and
     * HeadwindSpeedDataType. Barberfish must match it so the Wind field agrees with that
     * extension's fields on the same course.
     */
    private object Headwind {
        fun signedAngleDifference(angle1: Double, angle2: Double): Double {
            val a1 = angle1 % 360
            val a2 = angle2 % 360
            var diff = abs(a1 - a2)
            val sign =
                if (a1 < a2) {
                    if (diff > 180.0) -1 else 1
                } else {
                    if (diff > 180.0) 1 else -1
                }
            if (diff > 180.0) diff = 360.0 - diff
            return sign * diff
        }

        fun diff(courseDeg: Double, windFromDeg: Double): Double =
            signedAngleDifference(courseDeg, windFromDeg + 180)

        fun headwindStream(courseDeg: Double, windFromDeg: Double): Double {
            val d = diff(courseDeg, windFromDeg)
            return if (d < 0) d + 360 else d
        }

        fun headwindSpeedStream(courseDeg: Double, windFromDeg: Double, windSpeed: Double): Double =
            cos((diff(courseDeg, windFromDeg) + 180) * PI / 180.0) * windSpeed
    }

    private fun circularDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return minOf(d, 360.0 - d)
    }

    private val angles = (0 until 720).map { it * 0.5 }

    @Test
    fun relative_angle_matches_the_headwind_stream_on_every_course_and_wind() {
        for (course in angles) for (windFrom in angles) {
            val ours = relativeWindDeg(windFromDeg = windFrom, courseDeg = course)
            val theirs = Headwind.headwindStream(course, windFrom)
            assertTrue(
                "course $course, wind from $windFrom: ours $ours, theirs $theirs",
                circularDistance(ours, theirs) < 1e-9,
            )
        }
    }

    @Test
    fun relative_angle_stays_in_zero_to_360() {
        for (course in angles) for (windFrom in angles) {
            val ours = relativeWindDeg(windFromDeg = windFrom, courseDeg = course)
            assertTrue("$ours", ours >= 0.0 && ours < 360.0)
        }
    }

    @Test
    fun headwind_component_matches_the_headwind_speed_stream_on_every_course_and_wind() {
        val speed = 17.3
        for (course in angles) for (windFrom in angles) {
            val relative = relativeWindDeg(windFromDeg = windFrom, courseDeg = course)
            val ours = headwindComponent(windSpeed = speed, relativeWindDeg = relative)
            val theirs = Headwind.headwindSpeedStream(course, windFrom, speed)
            assertEquals("course $course, wind from $windFrom", theirs, ours, 1e-9)
        }
    }

    @Test
    fun riding_north_into_a_north_wind_is_dead_ahead_and_a_full_headwind() {
        val relative = relativeWindDeg(windFromDeg = 0.0, courseDeg = 0.0)
        assertEquals(180.0, relative, 1e-9)
        assertEquals(10.0, headwindComponent(windSpeed = 10.0, relativeWindDeg = relative), 1e-9)
    }

    @Test
    fun riding_north_with_a_south_wind_is_a_full_tailwind() {
        val relative = relativeWindDeg(windFromDeg = 180.0, courseDeg = 0.0)
        assertEquals(0.0, relative, 1e-9)
        assertEquals(-10.0, headwindComponent(windSpeed = 10.0, relativeWindDeg = relative), 1e-9)
    }

    @Test
    fun riding_north_with_a_west_wind_is_a_pure_crosswind() {
        val relative = relativeWindDeg(windFromDeg = 270.0, courseDeg = 0.0)
        assertEquals(90.0, relative, 1e-9)
        assertEquals(0.0, headwindComponent(windSpeed = 10.0, relativeWindDeg = relative), 1e-9)
    }

    @Test
    fun turning_with_the_wind_turns_the_relative_angle_back() {
        // Riding east into a north wind puts it on the left: 90 degrees less than dead ahead.
        assertEquals(90.0, relativeWindDeg(windFromDeg = 0.0, courseDeg = 90.0), 1e-9)
    }
}
