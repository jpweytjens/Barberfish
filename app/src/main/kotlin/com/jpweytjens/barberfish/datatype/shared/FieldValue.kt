package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode

data class FieldValue(
    val primary: String,
    val label: String = "",
    val color: FieldColor,
    val iconRes: Int? = null,
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
) {
    companion object {
        fun unavailable(label: String) = FieldValue("---", label, FieldColor.Default)

        fun noSensor(label: String = "") = FieldValue("No sensor", label, FieldColor.Error)

        fun notAvailable(label: String = "") = FieldValue("Not available", label, FieldColor.Error)

        fun noData(label: String = "") = FieldValue("No data", label, FieldColor.Muted)
    }
}

sealed interface FieldColor {

    data object Default : FieldColor

    // zone: 1-based zone number, total: number of zones (7 for power, 5 for HR)
    data class Zone(val zone: Int, val total: Int, val palette: ZonePalette, val isHr: Boolean) :
        FieldColor

    // factor: -1.0 (fully red) to 0.0 (yellow, at threshold) to +1.0 (fully green) — RdYlGn map
    data class Threshold(val factor: Float) : FieldColor

    // outsideFactor: 0→1, how far outside a boundary (0 = at/inside boundary, 1 = far outside)
    // borderProximity: 0→1, how close to a boundary from inside (0 = center/far-safe, 1 = at edge)
    // hasSafeZone: if true (both min+max set), inside colors green; if false (one-sided), white
    data class DangerZone(
        val outsideFactor: Float,
        val borderProximity: Float,
        val hasSafeZone: Boolean,
    ) : FieldColor

    data object Error : FieldColor // sensor missing or unavailable — #FF5252 red

    data object Muted : FieldColor // sensor idle, no data flowing — #7D7D7D grey
}
