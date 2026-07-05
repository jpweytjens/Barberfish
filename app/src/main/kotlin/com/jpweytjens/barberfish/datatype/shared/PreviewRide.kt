package com.jpweytjens.barberfish.datatype.shared

// One synthetic ride sampled at seven moments: easy warm-up, endurance, tempo,
// threshold, a steep climb, a sprint, and soft-pedal recovery. Preview cycles
// walk these lists in order, so the same index across fields describes the same
// moment: higher power comes with higher heart rate, the climb pairs high power
// with a 13% grade and grinding cadence, the sprint spikes everything at once.
//
// Per-index invariants the lists maintain:
// - maxPowerW / maxHrBpm are non-decreasing and >= the instantaneous value
// - npW >= avgPowerW (normalized power never reads below average power)
// - lastLap* is lap* shifted one moment back
// - elapsedS = ridingS + pausedS (elapsed always exceeds ride time)
// - civil dawn precedes sunrise; civil dusk follows sunset
internal object PreviewRide {
    // Instantaneous, per moment
    val powerW = listOf(120, 185, 245, 295, 340, 1234, 95)
    val hrBpm = listOf(96, 128, 148, 164, 178, 189, 132)
    val cadenceRpm = listOf(78, 88, 92, 95, 68, 121, 62)
    val gradePercent = listOf(0.8, 1.8, 3.5, 6.0, 13.0, 0.4, -4.2)

    // Running ride aggregates, per moment
    val avgPowerW = listOf(118, 152, 183, 211, 232, 268, 255)
    val npW = listOf(122, 160, 196, 228, 252, 310, 298)
    val maxPowerW = listOf(385, 385, 425, 470, 540, 1234, 1234)
    val avgHrBpm = listOf(94, 108, 122, 134, 144, 152, 149)
    val maxHrBpm = listOf(152, 155, 161, 170, 182, 191, 191)

    // Lap aggregates track the current phase; last lap is the phase before it
    val lapPowerW = listOf(125, 180, 238, 288, 332, 520, 105)
    val lastLapPowerW = listOf(110, 125, 180, 238, 288, 332, 520)
    val lapAvgHrBpm = listOf(95, 124, 145, 160, 174, 184, 138)
    val lastLapAvgHrBpm = listOf(90, 95, 124, 145, 160, 174, 184)

    // Zone fields render a raw zone float directly
    val powerZone = listOf(1.3, 2.4, 3.2, 4.1, 5.3, 7.0, 1.1)
    val hrZone = listOf(1.2, 2.3, 3.1, 3.9, 4.6, 5.0, 2.6)

    // Durations in seconds. Three snapshots (early, mid, long ride) covering the
    // under-an-hour and over-an-hour rendering of every time format.
    val elapsedS = listOf(1743L, 5462L, 38175L)
    val ridingS = listOf(1512L, 4841L, 34980L)
    val pausedS = listOf(231L, 621L, 3195L)
    val lapS = listOf(754L, 1926L, 3378L)
    val lastLapS = listOf(812L, 1745L, 2903L)
    val toCivilDawnS = listOf(25547L, 4972L, 1133L)
    val toSunriseS = listOf(27407L, 6832L, 2993L)
    val toSunsetS = listOf(9851L, 5327L, 648L)
    val toCivilDuskS = listOf(11711L, 7187L, 2508L)
}
