package com.jpweytjens.barberfish.datatype.shared

data class FieldValue(
    val primary: String,
    val unit: String,
    val label: String = "",
    val color: FieldColor,
    val iconRes: Int? = null,
) {
    companion object {
        fun unavailable(label: String) = FieldValue("---", "", label, FieldColor.Default)
    }
}

sealed interface FieldColor {
    data object Default : FieldColor

    // zone: 1-based zone number, total: number of zones (7 for power, 5 for HR)
    data class Zone(val zone: Int, val total: Int, val palette: ZonePalette, val isHr: Boolean) :
        FieldColor

    // factor: -1.0 (fully red, well below threshold) to 0.0 (at threshold) to +1.0 (fully blue,
    // well above)
    data class Threshold(val factor: Float) : FieldColor
}
