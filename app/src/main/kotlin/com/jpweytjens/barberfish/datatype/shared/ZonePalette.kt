package com.jpweytjens.barberfish.datatype.shared

import kotlinx.serialization.Serializable

@Serializable
enum class ZonePalette(val label: String) {
    KAROO("Karoo"),
    WAHOO("Wahoo"),
}
