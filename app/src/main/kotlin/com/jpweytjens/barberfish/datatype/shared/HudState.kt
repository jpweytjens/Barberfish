package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode

data class HudState(
    val speed: FieldValue,
    val hr: FieldValue,
    val power: FieldValue,
    val colorMode: ZoneColorMode,
)
