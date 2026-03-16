package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.formatTime
import com.jpweytjens.barberfish.extension.TimeFormat
import org.junit.Test
import org.junit.Assert.assertEquals

class TimeFieldTest {

    // --- COMPACT ---

    @Test fun compact_0s()    = assertEquals("0'00\"",    formatTime(0L,    TimeFormat.COMPACT))
    @Test fun compact_5s()    = assertEquals("0'05\"",    formatTime(5L,    TimeFormat.COMPACT))
    @Test fun compact_32s()   = assertEquals("0'32\"",    formatTime(32L,   TimeFormat.COMPACT))
    @Test fun compact_59s()   = assertEquals("0'59\"",    formatTime(59L,   TimeFormat.COMPACT))
    @Test fun compact_60s()   = assertEquals("1'00\"",    formatTime(60L,   TimeFormat.COMPACT))
    @Test fun compact_180s()  = assertEquals("3'00\"",    formatTime(180L,  TimeFormat.COMPACT))
    @Test fun compact_3599s() = assertEquals("59'59\"",   formatTime(3599L, TimeFormat.COMPACT))
    @Test fun compact_3600s() = assertEquals("1h0'00\"",  formatTime(3600L, TimeFormat.COMPACT))
    @Test fun compact_5025s() = assertEquals("1h23'45\"", formatTime(5025L, TimeFormat.COMPACT))

    // --- CLOCK ---

    @Test fun clock_0s()    = assertEquals("0:00:00", formatTime(0L,    TimeFormat.CLOCK))
    @Test fun clock_5s()    = assertEquals("0:00:05", formatTime(5L,    TimeFormat.CLOCK))
    @Test fun clock_32s()   = assertEquals("0:00:32", formatTime(32L,   TimeFormat.CLOCK))
    @Test fun clock_59s()   = assertEquals("0:00:59", formatTime(59L,   TimeFormat.CLOCK))
    @Test fun clock_60s()   = assertEquals("0:01:00", formatTime(60L,   TimeFormat.CLOCK))
    @Test fun clock_180s()  = assertEquals("0:03:00", formatTime(180L,  TimeFormat.CLOCK))
    @Test fun clock_3599s() = assertEquals("0:59:59", formatTime(3599L, TimeFormat.CLOCK))
    @Test fun clock_3600s() = assertEquals("1:00:00", formatTime(3600L, TimeFormat.CLOCK))
    @Test fun clock_5025s() = assertEquals("1:23:45", formatTime(5025L, TimeFormat.CLOCK))

    // --- Negative guard ---

    @Test fun compact_negative() = assertEquals("0'00\"", formatTime(-1L, TimeFormat.COMPACT))
    @Test fun clock_negative()   = assertEquals("0:00:00", formatTime(-1L, TimeFormat.CLOCK))
}
