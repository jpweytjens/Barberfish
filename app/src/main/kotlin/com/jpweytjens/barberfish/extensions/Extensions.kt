package com.jpweytjens.barberfish.extensions

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpweytjens.barberfish.datatype.ClimbFieldSettings

import com.jpweytjens.barberfish.datatype.DoubleFieldSettings
import com.jpweytjens.barberfish.datatype.GeneralSettings
import com.jpweytjens.barberfish.datatype.OneFieldSettings
import com.jpweytjens.barberfish.datatype.RETRY_CHECK_STREAMS
import com.jpweytjens.barberfish.datatype.STREAM_TIMEOUT
import com.jpweytjens.barberfish.datatype.SmartFieldSettings
import com.jpweytjens.barberfish.datatype.WAIT_STREAMS_LONG
import com.jpweytjens.barberfish.datatype.WAIT_STREAMS_MEDIUM
import com.jpweytjens.barberfish.datatype.WAIT_STREAMS_SHORT
import com.jpweytjens.barberfish.datatype.defaultClimbFieldSettings
import com.jpweytjens.barberfish.datatype.defaultDoubleFieldSettings
import com.jpweytjens.barberfish.datatype.defaultGeneralSettings
import com.jpweytjens.barberfish.datatype.defaultOneFieldSettings
import com.jpweytjens.barberfish.datatype.defaultSmartFieldSettings
import com.jpweytjens.barberfish.datatype.defaultPowerSettings
import com.jpweytjens.barberfish.datatype.powerSettings

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }
val generalsettingsKey = stringPreferencesKey("generalsettings")
val doublefieldKey = stringPreferencesKey("doublefieldsettings")
val onefieldKey = stringPreferencesKey("onefieldsettings")
val smartfieldKey = stringPreferencesKey("smartfieldsettings")
val climbfieldKey = stringPreferencesKey("climbfieldsettings")
val powerKey = stringPreferencesKey("powersettings")


suspend fun savePowerSettings(context: Context, settings: powerSettings) {

    context.dataStore.edit { t ->
        t[powerKey] = Json.encodeToString(settings)
    }
}
fun Context.streamStoredPowerSettings(): Flow<powerSettings> {
    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<powerSettings>(
                settingsJson[powerKey] ?: defaultPowerSettings)
        } catch (e: Throwable) {
            Timber.tag("KarooDualTypeExtension").e(e, "Failed to read power settings")
            powerSettings()
        }
    }.distinctUntilChanged()
}

fun KarooSystemService.streamPowerSettings(context: Context): Flow<Pair<powerSettings, Double>> {
    return combine(
        context.streamStoredPowerSettings(),
        streamUserProfile()
    ) { powerSettings, userProfile ->
        Pair(powerSettings, userProfile.weight.toDouble())
    }
}

suspend fun saveGeneralSettings(context: Context, settings: GeneralSettings) {

    context.dataStore.edit { t ->
        t[generalsettingsKey] = Json.encodeToString(settings)
    }
}

fun Context.streamGeneralSettings(): Flow<GeneralSettings> {

    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<GeneralSettings>(
                settingsJson[generalsettingsKey] ?: defaultGeneralSettings
            )
        } catch (e: Throwable) {
            Timber.tag("KarooDualTypeExtension").e(e, "Failed to read preferences")
            jsonWithUnknownKeys.decodeFromString< GeneralSettings>(defaultGeneralSettings)
        }
    }.distinctUntilChanged()
}

suspend fun saveDoubleFieldSettings(context: Context, settings: List<DoubleFieldSettings>) {
    // Timber.d("saveSettings IN $settings")
    context.dataStore.edit { t ->
        t[doublefieldKey] = Json.encodeToString(settings)
    }
}
fun Context.streamDoubleFieldSettings(): Flow<List<DoubleFieldSettings>> {
    return dataStore.data.map { settingsJson ->
        try {
            val decodedSettings = if (settingsJson.contains(doublefieldKey)) {
                jsonWithUnknownKeys.decodeFromString<List<DoubleFieldSettings>>(
                    settingsJson[doublefieldKey] ?: defaultDoubleFieldSettings
                )
            } else {
                jsonWithUnknownKeys.decodeFromString<List<DoubleFieldSettings>>(defaultDoubleFieldSettings)
            }

            if (decodedSettings.size == 5) {
                decodedSettings + DoubleFieldSettings(
                    index = 5
                )
            } else {
                decodedSettings
            }
        } catch (e: Throwable) {
            Timber.tag("KarooDualTypeExtension").e(e, "Failed to read preferences")
            jsonWithUnknownKeys.decodeFromString<List<DoubleFieldSettings>>(defaultDoubleFieldSettings)
        }
    }.distinctUntilChanged()
}


suspend fun saveClimbFieldSettings(context: Context, settings: List<ClimbFieldSettings>) {
    // Timber.d("saveSettings IN $settings")
    context.dataStore.edit { t ->
        t[climbfieldKey] = Json.encodeToString(settings)
    }
}
fun Context.streamClimbFieldSettings(): Flow<List<ClimbFieldSettings>> {
    return dataStore.data.map { settingsJson ->
        try {
            if (settingsJson.contains(climbfieldKey)) {
                jsonWithUnknownKeys.decodeFromString<List<ClimbFieldSettings>>(
                    settingsJson[climbfieldKey] ?: defaultClimbFieldSettings
                )
            } else {
                jsonWithUnknownKeys.decodeFromString<List<ClimbFieldSettings>>(defaultClimbFieldSettings)
            }

        } catch (e: Throwable) {
            Timber.tag("KarooDualTypeExtension").e(e, "Failed to read preferences Climb")
            jsonWithUnknownKeys.decodeFromString<List<ClimbFieldSettings>>(defaultClimbFieldSettings)
        }
    }.distinctUntilChanged()
}
suspend fun saveOneFieldSettings(context: Context, settings: List<OneFieldSettings>) {
    // Timber.d("saveSettings IN $settings")
    context.dataStore.edit { t ->
        t[onefieldKey] = Json.encodeToString(settings)
    }
}


fun Context.streamOneFieldSettings(): Flow<List<OneFieldSettings>> {
    return dataStore.data.map { settingsJson ->
        try {
            val decodedSettings = if (settingsJson.contains(onefieldKey)) {
                jsonWithUnknownKeys.decodeFromString<List<OneFieldSettings>>(
                    settingsJson[onefieldKey] ?: defaultOneFieldSettings
                )
            } else {
                jsonWithUnknownKeys.decodeFromString<List<OneFieldSettings>>(defaultOneFieldSettings)
            }

            if (decodedSettings.size == 2) {
                decodedSettings + OneFieldSettings(
                    index = 2
                )
            } else {
                decodedSettings
            }
        } catch (e: Throwable) {
            Timber.tag("KarooDualTypeExtension").e(e, "Failed to read OneFieldpreferences")
            jsonWithUnknownKeys.decodeFromString<List<OneFieldSettings>>(defaultOneFieldSettings)
        }
    }.distinctUntilChanged()
}

suspend fun saveSmartFieldSettings(context: Context, settings: List<SmartFieldSettings>) {
    // Timber.d("saveSettings IN $settings")
    context.dataStore.edit { t ->
        t[smartfieldKey] = Json.encodeToString(settings)
    }
}


fun Context.streamSmartFieldSettings(): Flow<List<SmartFieldSettings>> {
    return dataStore.data.map { settingsJson ->
        try {
            if (settingsJson.contains(smartfieldKey)) {
                jsonWithUnknownKeys.decodeFromString<List<SmartFieldSettings>>(
                    settingsJson[smartfieldKey] ?: defaultSmartFieldSettings
                )
            } else {
                jsonWithUnknownKeys.decodeFromString<List<SmartFieldSettings>>(defaultSmartFieldSettings)
            }

        } catch (e: Throwable) {
            Timber.tag("KarooDualTypeExtension").e(e, "Failed to read SmarteFieldpreferences")
            jsonWithUnknownKeys.decodeFromString<List<SmartFieldSettings>>(defaultSmartFieldSettings)
        }
    }.distinctUntilChanged()
}

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow<StreamState> {
        val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> {
    return callbackFlow {
        val listenerId = addConsumer { userProfile: UserProfile ->
            trySendBlocking(userProfile)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

@OptIn(FlowPreview::class)
fun KarooSystemService.streamDataMonitorFlow(
    dataTypeID: String,
    noCheck: Boolean = false
): Flow<StreamState> = flow {

    if (noCheck) {
        streamDataFlow(dataTypeID).collect { emit(it) }
        return@flow
    }

    var retryAttempt = 0


    val initialState = StreamState.Streaming(
        DataPoint(
            dataTypeId = dataTypeID,
            values = mapOf(DataType.Field.SINGLE to 0.0)
        )
    )

    emit(initialState)

    while (currentCoroutineContext().isActive) {
        try {
            streamDataFlow(dataTypeID)
                .distinctUntilChanged()
                .timeout(STREAM_TIMEOUT.milliseconds)
                .collect { state ->
                    when (state) {
                        is StreamState.Idle -> {
                            Timber.w("Stream estado inactivo: $dataTypeID, esperando...")
                            if (dataTypeID == DataType.Type.SPEED) emit(initialState)
                            delay(WAIT_STREAMS_SHORT)
                        }
                        is StreamState.NotAvailable -> {
                            Timber.w("Stream estado NotAvailable: $dataTypeID, esperando...")
                            emit(initialState)
                            delay(WAIT_STREAMS_SHORT * 2)
                        }
                        is StreamState.Searching -> {
                            Timber.w("Stream estado searching: $dataTypeID, esperando...")
                            emit(initialState)
                            delay(WAIT_STREAMS_SHORT/2)
                        }
                        else -> {
                            retryAttempt = 0
                            Timber.d("Stream estado: $state")
                            emit(state)
                        }
                    }
                }

        } catch (e: Exception) {
            when (e) {
                is TimeoutCancellationException -> {
                    if (retryAttempt++ < RETRY_CHECK_STREAMS) {
                        val backoffDelay = (1000L * (1 shl retryAttempt))
                            .coerceAtMost(WAIT_STREAMS_MEDIUM)
                        Timber.w("Timeout/Cancel en stream $dataTypeID, reintento $retryAttempt en ${backoffDelay}ms. Motivo $e")
                        delay(backoffDelay)
                    } else {
                        Timber.e("Máximo de reintentos alcanzado")
                        retryAttempt = 0
                        delay(WAIT_STREAMS_LONG)
                    }
                }
                is CancellationException -> {
                    Timber.d("Cancelación ignorada en streamDataFlow")
                }
                else -> {
                    Timber.e(e, "Error en stream")
                    delay(WAIT_STREAMS_LONG)
                }
            }
        }
    }
}

fun<T> Flow<T>.throttle(timeout: Long): Flow<T> = flow {
    var lastEmissionTime = 0L

    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime >= timeout) {
            emit(value)
            lastEmissionTime = currentTime
        }
    }
}

inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> {
    return callbackFlow {
        val listenerId = addConsumer<T> {
            trySend(it)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}