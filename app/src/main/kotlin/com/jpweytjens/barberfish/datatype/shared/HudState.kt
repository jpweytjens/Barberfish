package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode

data class HudState(
    val speed: FieldState,
    val hr: FieldState,
    val power: FieldState,
    val colorMode: ZoneColorMode,
)
