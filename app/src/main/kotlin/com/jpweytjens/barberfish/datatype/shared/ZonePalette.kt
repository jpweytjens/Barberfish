package com.jpweytjens.barberfish.datatype.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class ZonePalette(val label: String) {
    KAROO("Karoo"),
    // Betas stored this palette as SURGEONFISH before it took the house name.
    @JsonNames("SURGEONFISH") BARBERFISH("Barberfish"),
    WAHOO("Wahoo"),
    INTERVALS("Intervals.icu"),
    ZWIFT("Zwift"),
    HSLUV("HSLuv"),
}
