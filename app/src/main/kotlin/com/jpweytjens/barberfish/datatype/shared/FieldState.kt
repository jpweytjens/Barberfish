package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import io.hammerhead.karooext.models.UserProfile

data class FieldState(
    val primary: String,
    val label: String = "",
    val color: FieldColor,
    val iconRes: Int? = null,
    val secondaryIconRes: Int? = null,
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
) {
    companion object {
        fun unavailable(label: String, iconRes: Int? = null) =
            FieldState("Not available", label, FieldColor.StreamState, iconRes = iconRes)

        fun searching(label: String = "", iconRes: Int? = null) =
            FieldState("Searching...", label, FieldColor.StreamState, iconRes = iconRes)

        fun notAvailable(label: String = "", iconRes: Int? = null) =
            FieldState("Not available", label, FieldColor.StreamState, iconRes = iconRes)

        fun idle(label: String = "", iconRes: Int? = null) =
            FieldState("No data", label, FieldColor.StreamState, iconRes = iconRes)
    }
}

sealed interface FieldColor {

    data object Default : FieldColor

    // zone: 1-based zone number, total: number of zones (7 for power, 5 for HR)
    data class Zone(
        val zone: Int,
        val total: Int,
        val palette: ZonePalette,
        val isHr: Boolean,
        val readable: Boolean = true,
    ) : FieldColor

    // factor: -1.0 (fully red) to 0.0 (yellow, at threshold) to +1.0 (fully green) — RdYlGn map
    data class Threshold(val factor: Float) : FieldColor

    // outsideFactor: 0→1, how far outside a boundary (0 = at/inside boundary, 1 = far outside)
    // borderProximity: 0→1, how close to a boundary from inside (0 = center/far-safe, 1 = at edge)
    // hasSafeZone: if true (both min+max set), inside colors green; if false (one-sided), white
    data class DangerZone(
        val outsideFactor: Float,
        val borderProximity: Float,
        val hasSafeZone: Boolean,
    ) : FieldColor

    data object Error : FieldColor // null value in a live stream — #FF5252 red in field_value

    data object Muted : FieldColor // reserved — #7D7D7D grey

    data object StreamState : FieldColor // SDK non-Streaming state — white ibm-plex-sans-condensed in stream_state_tv

    // percent: grade as a percentage (e.g. 5.0 = 5%). Coloring based on gradient palette.
    data class Grade(val percent: Double, val palette: GradePalette, val readable: Boolean = true) : FieldColor
}

fun zoneFieldColor(
    zone: Int,
    colorMode: ZoneColorMode,
    profile: UserProfile,
    zones: ZoneConfig,
    isHr: Boolean,
): FieldColor =
    if (colorMode == ZoneColorMode.NONE) FieldColor.Default
    else FieldColor.Zone(
        zone,
        (if (isHr) profile.heartRateZones else profile.powerZones).size.coerceAtLeast(1),
        if (isHr) zones.hrPalette else zones.powerPalette,
        isHr = isHr,
        readable = zones.readableColors,
    )
