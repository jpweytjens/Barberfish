package com.jpweytjens.barberfish.datatype.shared

/**
 * Format a display value with a fixed number of decimals, clamped at zero.
 * Remaining quantities never go negative; clamping avoids "-0.0" at the destination.
 */
fun formatFixed(value: Double, decimals: Int): String =
    "%.${decimals}f".format(value.coerceAtLeast(0.0))
