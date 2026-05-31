package com.jpweytjens.barberfish.datatype.shared

// Per-climb difficulty scoring and the approach distance at which the climb-only
// sparkline reveals before a climb foot. Pure math, no Android dependencies, so it
// stays unit-testable.

// Approach distance tiers (metres) by PCS score. Harder climbs reveal earlier.
internal const val APPROACH_SMALL_M = 500f
internal const val APPROACH_MEDIUM_M = 1000f
internal const val APPROACH_LARGE_M = 2000f

// PCS score tier boundaries.
internal const val CLIMB_SCORE_T1 = 16.0
internal const val CLIMB_SCORE_T2 = 50.0

// Climb-frame right margin past the summit, as a fraction of climb length (with a floor),
// so the crest, summit POI, and position dot clear the right edge without showing a long
// descent. The left margin is the full approach distance (so the dot advances to the foot).
internal const val SUMMIT_TAIL_FRAC = 0.08f
internal const val SUMMIT_TAIL_MIN_M = 100f

/**
 * PCS per-climb profile score: `(gradePct / 2)^2 * (lengthM / 1000)`.
 * No distance-to-finish factor — purely the climb's own shape.
 */
internal fun pcsClimbScore(gradePct: Double, lengthM: Double): Double =
    (gradePct / 2.0) * (gradePct / 2.0) * (lengthM / 1000.0)

/** Approach distance (metres) before a climb foot at which the sparkline reveals. */
internal fun climbApproachM(score: Double): Float =
    when {
        score < CLIMB_SCORE_T1 -> APPROACH_SMALL_M
        score < CLIMB_SCORE_T2 -> APPROACH_MEDIUM_M
        else -> APPROACH_LARGE_M
    }
