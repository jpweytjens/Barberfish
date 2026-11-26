package com.jpweytjens.barberfish.extension

import android.content.Context
import com.jpweytjens.barberfish.datatype.shared.MovingState
import com.jpweytjens.barberfish.datatype.shared.MovingTimeAndDistance
import com.jpweytjens.barberfish.datatype.shared.MovingTracker
import com.jpweytjens.barberfish.datatype.shared.RidePhase
import com.jpweytjens.barberfish.datatype.shared.RideReadings
import com.jpweytjens.barberfish.datatype.shared.next
import com.jpweytjens.barberfish.datatype.shared.onShutdownNotice
import com.jpweytjens.barberfish.datatype.shared.showsMovingTimeOrDistance
import com.jpweytjens.barberfish.datatype.shared.whileOn
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import timber.log.Timber

private const val TICK_MS = 1_000L
private const val RETRY_MAX_MS = 60_000L

/** One input to the tracker, handled in the order the inputs arrive. */
private sealed interface MovingEvent {
    data class Enabled(val on: Boolean) : MovingEvent

    data class Phase(val phase: RidePhase) : MovingEvent

    data class Readings(val readings: RideReadings?) : MovingEvent

    data object Tick : MovingEvent

    data object Retry : MovingEvent

    data object ShutdownNotice : MovingEvent
}

/**
 * Pure flow logic: one tracker step per element of [ticks], and one at once for every phase change
 * and shutdown notice. Every one of those is followed by a save of the tracker's state when it
 * differs from the last state [save] stored. A failed save is tried again at the next of them and
 * at every element of [retries], which is collected only while a save is owed, whatever the gate.
 *
 * While [enabled] is false it emits null, takes no step and does not collect [ticks], but ride
 * boundaries in [phases] still apply. While it is true it emits the tracker's pair, which a null in
 * [readings] holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
internal fun movingTimeAndDistanceFlow(
    initial: MovingState?,
    enabled: Flow<Boolean>,
    phases: Flow<RidePhase>,
    readings: Flow<RideReadings?>,
    ticks: Flow<Unit>,
    retries: Flow<Unit>,
    shutdownNotices: Flow<Unit>,
    save: suspend (MovingState?) -> Unit,
): Flow<MovingTimeAndDistance?> = flow {
    var tracker = MovingTracker(state = initial)
    var saved = initial
    var on = false
    var phase: RidePhase? = null
    var latest: RideReadings? = null
    val owed = MutableStateFlow(false)
    fun step(current: RidePhase) {
        tracker = tracker.next(current, if (on) latest else null)
    }
    merge(
            ticksWhileOn(enabled, ticks),
            retriesWhile(owed, retries),
            phases.map { MovingEvent.Phase(it) },
            readings.map { MovingEvent.Readings(it) },
            shutdownNotices.map { MovingEvent.ShutdownNotice },
        )
        .collect { event ->
            when (event) {
                is MovingEvent.Enabled -> on = event.on
                is MovingEvent.Readings -> latest = event.readings
                is MovingEvent.Phase -> {
                    phase = event.phase
                    step(event.phase)
                }
                MovingEvent.Tick -> phase?.let(::step)
                MovingEvent.Retry -> Unit
                MovingEvent.ShutdownNotice -> {
                    tracker = tracker.onShutdownNotice()
                    phase?.let(::step)
                }
            }
            val triggers = event !is MovingEvent.Enabled && event !is MovingEvent.Readings
            if (triggers && tracker.state != saved) {
                try {
                    save(tracker.state)
                    saved = tracker.state
                } catch (e: IOException) {
                    Timber.w(e, "Could not save the moving time and distance")
                }
            }
            owed.value = tracker.state != saved
            emit(if (on) tracker.state?.moving else null)
        }
}
    .distinctUntilChanged()

/** The gate's changes, each followed by [ticks] for as long as the gate is on. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun ticksWhileOn(enabled: Flow<Boolean>, ticks: Flow<Unit>): Flow<MovingEvent> =
    enabled.flatMapLatest { on ->
        flow {
            emit(MovingEvent.Enabled(on))
            if (on) emitAll(ticks.map { MovingEvent.Tick })
        }
    }

/** [retries] for as long as a save is [owed]. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun retriesWhile(owed: Flow<Boolean>, retries: Flow<Unit>): Flow<MovingEvent> =
    owed.flatMapLatest {
        if (it) retries.map { MovingEvent.Retry } else emptyFlow()
    }

/**
 * The wiring to the Karoo: gate, 1 s tick, storage. A stored state that cannot be read starts the
 * tracking from none. Any other failure ends it: the pair stays null and the fields read the
 * Karoo's own values, as the extension hosts every field and must not crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Context.movingTimeAndDistance(
    karooSystem: KarooSystemService,
    shutdownNotices: Flow<Unit>,
    load: suspend () -> MovingState? = { loadMovingState() },
    save: suspend (MovingState?) -> Unit = { saveMovingState(it) },
): Flow<MovingTimeAndDistance?> {
    val phases = karooSystem.streamRideState().map { it.phase() }
    val shown =
        combine(
            karooSystem.streamActiveRideProfile(),
            streamActiveHUDConfig(),
            streamSpeedFieldConfig(),
        ) { active, hud, speed ->
            showsMovingTimeOrDistance(active.profile.fieldTypeIds(), hud.slots, speed)
        }
    val enabled =
        combine(shown, phases) { show, phase -> show && phase != RidePhase.IDLE }
            .distinctUntilChanged()
    val readings =
        enabled.whileOn<Boolean, RideReadings?>(isOn = { it }, off = null) {
            karooSystem.rideReadings()
        }
    // No timer between rides: the ride state listener alone sees the next one start. The flow
    // runs the timer only while the gate is on, which it never is between rides, and the retry
    // timer only while a save has failed.
    val ticks = everySecond()
    return flow {
        val initial =
            try {
                load()
            } catch (e: IOException) {
                Timber.w(e, "Could not read the stored moving time and distance")
                null
            }
        emitAll(
            movingTimeAndDistanceFlow(
                initial = initial,
                enabled = enabled,
                phases = phases,
                readings = readings,
                ticks = ticks,
                retries = backingOff(),
                shutdownNotices = shutdownNotices,
                save = save,
            )
        )
    }
        .catch { e ->
            Timber.e(e, "Moving time and distance stopped")
            emit(null)
        }
}

private fun RideState.phase(): RidePhase =
    when (this) {
        is RideState.Idle -> RidePhase.IDLE
        is RideState.Recording -> RidePhase.RECORDING
        is RideState.Paused -> RidePhase.PAUSED
    }

private val barberfishTypeIdPrefix = DataType.dataTypeId("barberfish", "")

/** The Barberfish type ids on any page of the profile, e.g. "time-moving". */
private fun RideProfile.fieldTypeIds(): Set<String> =
    pages
        .flatMap { it.elements }
        .map { it.dataTypeId }
        .filter { it.startsWith(barberfishTypeIdPrefix) }
        .map { it.removePrefix(barberfishTypeIdPrefix) }
        .toSet()

/** Recording time and distance, or null while either has no reading. */
private fun KarooSystemService.rideReadings(): Flow<RideReadings?> =
    combine(
        streamDataFlow(DataType.Type.ELAPSED_TIME),
        streamDataFlow(DataType.Type.DISTANCE),
        streamDataFlow(DataType.Type.SPEED),
    ) { recordingTime, distance, speed ->
        val recordingTimeMs = recordingTime.value(DataType.Field.ELAPSED_TIME)
        val distanceM = distance.value(DataType.Field.DISTANCE)
        if (recordingTimeMs != null && distanceM != null) {
            RideReadings(recordingTimeMs, distanceM, speed is StreamState.Streaming)
        } else {
            null
        }
    }

private fun StreamState.value(field: String): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.values?.get(field)

private fun everySecond(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(TICK_MS)
    }
}

/** After 1 s, then 2 s, 4 s and so on, up to once a minute. */
private fun backingOff(): Flow<Unit> = flow {
    var waitMs = TICK_MS
    while (true) {
        delay(waitMs)
        emit(Unit)
        waitMs = (waitMs * 2).coerceAtMost(RETRY_MAX_MS)
    }
}
