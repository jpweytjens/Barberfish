package com.jpweytjens.barberfish.datatype.shared

import io.hammerhead.karooext.models.UserProfile

// The SDK provides UpdateNumericConfig(formatDataTypeId) to auto-format standard
// numeric fields, but custom Glance views render themselves, so we convert manually.
// SDK has no .speed sub-property on PreferredUnit; .distance drives the system choice.
enum class ConvertType {
    NONE,
    TIME,
    SPEED,
    DISTANCE,
    ELEVATION;

    // For conversions that don't vary by unit system (TIME, NONE)
    fun apply(raw: Double): Double =
        when (this) {
            NONE -> raw
            TIME -> raw / 1000.0
            else -> error("$this requires a UserProfile")
        }

    fun apply(raw: Double, profile: UserProfile): Double =
        when (this) {
            NONE -> raw
            TIME -> raw / 1000.0
            SPEED ->
                when (profile.preferredUnit.distance) { // m/s → km/h or mph
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw * 2.237
                    else -> raw * 3.6
                }
            DISTANCE ->
                when (profile.preferredUnit.distance) { // m → km or mi
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw / 1609.344
                    else -> raw / 1000.0
                }
            ELEVATION ->
                when (profile.preferredUnit.elevation) { // m → m or ft
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw * 3.28084
                    else -> raw
                }
        }

    /** Convert a stored metric display value to the user's display units (km/h → mph, km → mi). */
    fun toDisplay(metricValue: Double, profile: UserProfile): Double =
        when (this) {
            SPEED ->
                when (profile.preferredUnit.distance) {
                    UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                        Math.round(metricValue * 0.621371 * 100.0) / 100.0
                    else -> metricValue
                }
            DISTANCE ->
                when (profile.preferredUnit.distance) {
                    UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                        Math.round(metricValue * 0.621371 * 100.0) / 100.0
                    else -> metricValue
                }
            else -> metricValue
        }

    /** Convert a user-entered display value back to metric (mph → km/h, mi → km). */
    fun fromDisplay(displayValue: Double, profile: UserProfile): Double =
        when (this) {
            SPEED ->
                when (profile.preferredUnit.distance) {
                    UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                        Math.round(displayValue / 0.621371 * 100.0) / 100.0
                    else -> displayValue
                }
            DISTANCE ->
                when (profile.preferredUnit.distance) {
                    UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                        Math.round(displayValue / 0.621371 * 100.0) / 100.0
                    else -> displayValue
                }
            else -> displayValue
        }

    fun unit(profile: UserProfile): String =
        when (this) {
            NONE -> ""
            TIME -> "s"
            SPEED ->
                if (profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL)
                    "mph"
                else "km/h"
            DISTANCE ->
                if (profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL)
                    "mi"
                else "km"
            ELEVATION ->
                if (profile.preferredUnit.elevation == UserProfile.PreferredUnit.UnitType.IMPERIAL)
                    "ft"
                else "m"
        }
}
