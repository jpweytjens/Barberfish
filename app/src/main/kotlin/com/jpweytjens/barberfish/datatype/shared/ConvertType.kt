package com.jpweytjens.barberfish.datatype.shared

import io.hammerhead.karooext.models.UserProfile

// The SDK provides UpdateNumericConfig(formatDataTypeId) to auto-format standard
// numeric fields, but custom Glance views render themselves, so we convert manually.
// SDK has no .speed sub-property on PreferredUnit; .distance drives the system choice.
enum class ConvertType {
    NONE, SPEED, DISTANCE, ELEVATION;

    fun apply(raw: Double, profile: UserProfile): Double = when (this) {
        NONE -> raw
        SPEED -> when (profile.preferredUnit.distance) {         // m/s → km/h or mph
            UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw * 2.237
            else -> raw * 3.6
        }
        DISTANCE -> when (profile.preferredUnit.distance) {      // m → km or mi
            UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw / 1609.344
            else -> raw / 1000.0
        }
        ELEVATION -> when (profile.preferredUnit.elevation) {    // m → m or ft
            UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw * 3.28084
            else -> raw
        }
    }

    fun unit(profile: UserProfile): String = when (this) {
        NONE      -> ""
        SPEED     -> if (profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL) "mph" else "km/h"
        DISTANCE  -> if (profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL) "mi" else "km"
        ELEVATION -> if (profile.preferredUnit.elevation == UserProfile.PreferredUnit.UnitType.IMPERIAL) "ft" else "m"
    }
}
