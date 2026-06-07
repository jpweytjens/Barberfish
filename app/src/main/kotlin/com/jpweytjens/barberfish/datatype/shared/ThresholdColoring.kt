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
