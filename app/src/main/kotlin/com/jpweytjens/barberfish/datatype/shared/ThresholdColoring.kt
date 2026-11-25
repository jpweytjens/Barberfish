package com.jpweytjens.barberfish.datatype.shared

// Target-style threshold coloring: fade from neutral at the threshold to a fully colored
// edge at +/- rangePercent% of the threshold. Both inputs are expected in the same display
// unit (caller does any unit conversion). A non-positive threshold disables coloring.
internal fun targetThresholdColor(
    converted: Double,
    threshDisplay: Double,
    rangePercentBelow: Double,
    rangePercentAbove: Double,
): FieldColor {
    if (threshDisplay <= 0.0) return FieldColor.Default
    val rangePercent = if (converted >= threshDisplay) rangePercentAbove else rangePercentBelow
    val factor =
        ((converted - threshDisplay) / threshDisplay * 100.0 / rangePercent)
            .coerceIn(-1.0, 1.0)
            .toFloat()
    return FieldColor.Threshold(factor)
}

// Min/max threshold coloring: inside [min, max] the color warms toward the nearer bound, outside
// it fades to the fully colored edge at rangePercent% of that bound. Either bound may be absent;
// with neither, coloring is disabled. All inputs are expected in the same display unit.
internal fun minMaxThresholdColor(
    value: Double,
    min: Double?,
    max: Double?,
    rangePercentBelow: Double,
    rangePercentAbove: Double,
): FieldColor {
    if (min == null && max == null) return FieldColor.Default
    val bandBelow = band(min, rangePercentBelow)
    val bandAbove = band(max, rangePercentAbove)
    val hasSafeZone = min != null && max != null
    return when {
        min != null && value < min -> {
            val outsideFactor = ((min - value) / bandBelow).coerceIn(0.0, 1.0).toFloat()
            FieldColor.DangerZone(outsideFactor, 1f, hasSafeZone)
        }
        max != null && value > max -> {
            val outsideFactor = ((value - max) / bandAbove).coerceIn(0.0, 1.0).toFloat()
            FieldColor.DangerZone(outsideFactor, 1f, hasSafeZone)
        }
        else -> {
            val nearMin = borderProximity(min?.let { value - it }, bandBelow)
            val nearMax = borderProximity(max?.let { it - value }, bandAbove)
            FieldColor.DangerZone(0f, maxOf(nearMin, nearMax), hasSafeZone)
        }
    }
}

// The width of the fade at a bound: rangePercent% of the bound, 0 without one.
private fun band(bound: Double?, rangePercent: Double): Double =
    bound?.let { it * rangePercent / 100.0 } ?: 0.0

// 1 at the bound, falling to 0 a full band inside it; 0 when the bound or its band is absent.
private fun borderProximity(distanceInside: Double?, band: Double): Float =
    if (distanceInside != null && band > 0.0)
        (1.0 - distanceInside / band).coerceIn(0.0, 1.0).toFloat()
    else 0f
