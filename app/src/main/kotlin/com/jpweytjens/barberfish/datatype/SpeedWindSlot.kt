package com.jpweytjens.barberfish.datatype

import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.FieldState
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/*
 * The Wind HUD slot's Show speed option: ride speed on the top row, the Wind slot's arrow and
 * headwind number on the bottom row. Colour stays on the wind, the only coloured quantity in the
 * slot; a HUD Speed slot is never coloured either. When the wind has nothing to show the slot is
 * a plain Speed slot, so the column never drops while speed is still valid.
 */

private fun FieldState.isLive(): Boolean = color != FieldColor.StreamState

/**
 * One slot state from the speed and wind states. Both live: the wind state with the speed row and
 * the speed's label and icon, the wind icon second. Otherwise whichever side is live, speed first.
 */
internal fun speedWindState(speed: FieldState, wind: FieldState): FieldState =
    when {
        speed.isLive() && wind.isLive() ->
            wind.copy(
                speedRow = speed.primary,
                label = speed.label,
                iconRes = speed.iconRes,
                secondaryIconRes = wind.iconRes,
                noSensor = false,
            )
        speed.isLive() -> speed
        wind.isLive() -> wind
        else -> speed.copy(noSensor = false)
    }

/**
 * The Wind slot's live states with speed stacked on top when the slot asks for it. The wind side is
 * seeded with Searching… so a silent Headwind extension never holds back the speed.
 */
internal fun Flow<FieldState>.withSpeed(
    slot: HUDSlotConfig,
    karooSystem: KarooSystemService,
    profile: UserProfile,
): Flow<FieldState> =
    if (!slot.windShowSpeed) this
    else
        withSpeedStates(
            karooSystem.streamDataFlow(slot.speedSmoothing.typeId).map {
                SpeedField.toFieldState(it, profile, slot.speedSmoothing)
            }
        )

/** [speed] combined with these wind states, the wind side seeded so speed shows at once. */
internal fun Flow<FieldState>.withSpeedStates(speed: Flow<FieldState>): Flow<FieldState> =
    combine(speed, onStart { emit(FieldState.searching()) }, ::speedWindState)

/** The Wind slot's preview frames, each paired with a speed frame when the slot asks for it. */
internal fun List<FieldState>.withSpeedPreview(
    slot: HUDSlotConfig,
    profile: UserProfile,
): List<FieldState> =
    if (!slot.windShowSpeed) this
    else
        SpeedField.previewStates(SpeedFieldConfig(slot.speedSmoothing), profile)
            .zip(this, ::speedWindState)
