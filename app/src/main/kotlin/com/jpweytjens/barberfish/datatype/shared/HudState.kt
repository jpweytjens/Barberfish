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

/** Configured slots in column order: three, or four when [HUDState.columns] is 4. */
val HUDState.slots: List<SlotState>
    get() = listOf(left, middle, right, fourth).take(columns)

/**
 * Column indices the strip renders: every configured slot except those whose sensor is not paired
 * ([FieldState.noSensor]). When no slot is paired nothing is dropped, so the strip shows its
 * placeholders instead of rendering empty.
 */
fun HUDState.visibleColumns(): List<Int> {
    val paired = slots.indices.filterNot { slots[it].field.noSensor }
    return paired.ifEmpty { slots.indices.toList() }
}
