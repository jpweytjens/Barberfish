package com.jpweytjens.barberfish.datatype.shared

data class FieldValue(
    val primary: String,
    val unit: String,
    val label: String,
    val color: FieldColor,
) {
    companion object {
        fun unavailable(label: String) = FieldValue("---", "", label, FieldColor.Default)
    }
}

sealed interface FieldColor {
    data object Default : FieldColor

    // zone: 1-based zone number, total: number of zones (7 for power, 5 for HR)
    data class Zone(val zone: Int, val total: Int, val palette: ZonePalette, val isHr: Boolean) : FieldColor

    data class Threshold(val above: Boolean) : FieldColor
}
