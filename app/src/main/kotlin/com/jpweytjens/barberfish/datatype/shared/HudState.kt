package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode

data class HUDState(
    val columns: Int,
    val leftSlot: FieldState,
    val leftColorMode: ZoneColorMode,
    val middleSlot: FieldState,
    val middleColorMode: ZoneColorMode,
    val rightSlot: FieldState,
    val rightColorMode: ZoneColorMode,
    val fourthSlot: FieldState,
    val fourthColorMode: ZoneColorMode,
)
