package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.datatype.TimeKind
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.SpeedFieldConfig
import com.jpweytjens.barberfish.extension.SpeedThresholdSource
import kotlinx.serialization.Serializable

data class MovingTimeAndDistance(val timeMs: Double, val distanceM: Double)

/** Our pair, with the Karoo's recording time and distance from the same tick. */
@Serializable
data class MovingState(
    val movingTimeMs: Double,
    val movingDistanceM: Double,
    val recordingTimeMs: Double,
    val distanceM: Double,
) {
    val moving: MovingTimeAndDistance
        get() = MovingTimeAndDistance(movingTimeMs, movingDistanceM)
}

enum class RidePhase {
    IDLE,
    RECORDING,
    PAUSED,
}

data class RideReadings(
    val recordingTimeMs: Double,
    val distanceM: Double,
    val hasSpeedReading: Boolean,
)

data class MovingTracker(
    val state: MovingState? = null,
    val lastPhase: RidePhase? = null,
    val rideSeenActive: Boolean = false,
    val shuttingDown: Boolean = false,
)

/**
 * Advances our pair by the change in the Karoo's recording time and distance. A negative change
 * (the ride timer restarted) leaves the pair alone. Time counts only with a speed reading or
 * distance covered, so a timer running without a speed signal adds nothing.
 */
fun MovingState.step(readings: RideReadings): MovingState {
    val dt = readings.recordingTimeMs - recordingTimeMs
    val dd = readings.distanceM - distanceM
    val advanced =
        when {
            dt < 0 || dd < 0 -> this
            readings.hasSpeedReading || dd > 0 ->
                copy(movingTimeMs = movingTimeMs + dt, movingDistanceM = movingDistanceM + dd)
            else -> copy(movingDistanceM = movingDistanceM + dd)
        }
    return advanced.copy(recordingTimeMs = readings.recordingTimeMs, distanceM = readings.distanceM)
}

fun MovingTracker.next(phase: RidePhase, readings: RideReadings?): MovingTracker {
    if (phase == RidePhase.IDLE) {
        val ended = rideSeenActive && !shuttingDown
        return if (ended) {
            copy(state = null, rideSeenActive = false, lastPhase = phase)
        } else {
            copy(lastPhase = phase)
        }
    }
    val newRide = lastPhase == RidePhase.IDLE && phase == RidePhase.RECORDING && !rideSeenActive
    val current = if (newRide) null else state
    val stepped =
        when {
            readings == null -> current
            current == null ->
                MovingState(
                    movingTimeMs = readings.recordingTimeMs,
                    movingDistanceM = readings.distanceM,
                    recordingTimeMs = readings.recordingTimeMs,
                    distanceM = readings.distanceM,
                )
            else -> current.step(readings)
        }
    return copy(state = stepped, rideSeenActive = true, lastPhase = phase)
}

fun MovingTracker.onShutdownNotice(): MovingTracker = copy(shuttingDown = true)

internal val movingTimeTypeIds =
    setOf(
        "avg-speed-moving",
        "time-moving",
        "remaining-ride-time",
        "time-to-destination",
        "time-of-arrival",
    )

private fun HUDSlotField.readsMovingTimeOrDistance(): Boolean =
    when (this) {
        is HUDSlotField.AvgSpeed -> !includePaused
        is HUDSlotField.Time -> kind == TimeKind.RIDING
        is HUDSlotField.ETA -> true
        else -> false
    }

/** True when the profile shows a field that reads moving time or distance, on any page. */
fun showsMovingTimeOrDistance(
    fieldTypeIds: Set<String>,
    hud: HUDSlots,
    speed: SpeedFieldConfig,
): Boolean {
    val hudSlots =
        listOf(hud.leftSlot, hud.middleSlot, hud.rightSlot) +
            if (hud.columns == 4) listOf(hud.fourthSlot) else emptyList()
    return fieldTypeIds.any { it in movingTimeTypeIds } ||
        ("speed" in fieldTypeIds && speed.source == SpeedThresholdSource.AVG_MOVING) ||
        ("three-column" in fieldTypeIds && hudSlots.any { it.field.readsMovingTimeOrDistance() })
}
