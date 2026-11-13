package com.jpweytjens.barberfish.screens

import androidx.compose.runtime.Composable
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.SpeedSmoothingStream

/** The Wind slot's own controls: Show speed, and the speed smoothing when it is on. */
@Composable
internal fun HUDWindCard(slot: HUDSlotConfig, onUpdate: (HUDSlotConfig) -> Unit) {
    BoolToggleRow(
        label = "SHOW SPEED",
        value = slot.windShowSpeed,
        onChange = { onUpdate(slot.copy(windShowSpeed = it)) },
        help = "Ride speed above the wind. Color stays on the wind.",
    )
    if (slot.windShowSpeed) {
        ControlLabel("SMOOTHING")
        SmoothingSlider(
            options = SpeedSmoothingStream.entries,
            selected = slot.speedSmoothing,
            label = { it.label },
            onSelected = { onUpdate(slot.copy(speedSmoothing = it)) },
            thumbIcon = R.drawable.ic_col_speed,
        )
    }
}
