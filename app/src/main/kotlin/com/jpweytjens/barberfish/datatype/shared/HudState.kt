package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.UserProfile

data class SlotState(val field: FieldState, val colorMode: ZoneColorMode)

data class HUDState(
    val columns: Int,
    val left: SlotState,
    val middle: SlotState,
    val right: SlotState,
    val fourth: SlotState,
    val profile: UserProfile,
)
