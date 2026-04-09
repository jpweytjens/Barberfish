package com.jpweytjens.barberfish.datatype.shared

import io.hammerhead.karooext.models.UserProfile
import kotlin.math.roundToLong

private const val MS_TO_MPH = 2.237
internal const val MS_TO_KMH = 3.6
private const val METERS_PER_MILE = 1609.344
private const val METERS_PER_KM = 1000.0
private const val METERS_TO_FEET = 3.28084
private const val KMH_TO_MPH = 0.621371

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
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw * MS_TO_MPH
                    else -> raw * MS_TO_KMH
                }
            DISTANCE ->
                when (profile.preferredUnit.distance) { // m → km or mi
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw / METERS_PER_MILE
                    else -> raw / METERS_PER_KM
                }
            ELEVATION ->
                when (profile.preferredUnit.elevation) { // m → m or ft
                    UserProfile.PreferredUnit.UnitType.IMPERIAL -> raw * METERS_TO_FEET
                    else -> raw
                }
        }

    /** Convert a stored metric display value to the user's display units (km/h → mph, km → mi). */
    fun toDisplay(metricValue: Double, profile: UserProfile): Double =
        when (this) {
            SPEED, DISTANCE ->
                when (profile.preferredUnit.distance) {
                    UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                        (metricValue * KMH_TO_MPH * 100.0).roundToLong() / 100.0
                    else -> metricValue
                }
            else -> metricValue
        }

    /** Convert a user-entered display value back to metric (mph → km/h, mi → km). */
    fun fromDisplay(displayValue: Double, profile: UserProfile): Double =
        when (this) {
            SPEED, DISTANCE ->
                when (profile.preferredUnit.distance) {
                    UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                        (displayValue / KMH_TO_MPH * 100.0).roundToLong() / 100.0
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
