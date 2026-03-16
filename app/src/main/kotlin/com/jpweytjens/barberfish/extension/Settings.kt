package com.jpweytjens.barberfish.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "barberfish")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val threeColumnConfigKey = stringPreferencesKey("three_column_config")
private val avgSpeedTotalConfigKey = stringPreferencesKey("avg_speed_total_config")
private val avgSpeedMovingConfigKey = stringPreferencesKey("avg_speed_moving_config")

// --- ThreeColumnConfig ---

@Serializable
enum class PowerStream(val label: String, val typeId: String, val fieldId: String) {
    INSTANT("1s",  DataType.Type.POWER,                     DataType.Field.POWER),
    S3(     "3s",  DataType.Type.SMOOTHED_3S_AVERAGE_POWER,  DataType.Field.SMOOTHED_3S_AVERAGE_POWER),
    S5(     "5s",  DataType.Type.SMOOTHED_5S_AVERAGE_POWER,  DataType.Field.SMOOTHED_5S_AVERAGE_POWER),
    S10(    "10s", DataType.Type.SMOOTHED_10S_AVERAGE_POWER, DataType.Field.SMOOTHED_10S_AVERAGE_POWER),
    S30(    "30s", DataType.Type.SMOOTHED_30S_AVERAGE_POWER, DataType.Field.SMOOTHED_30S_AVERAGE_POWER),
}

@Serializable
data class ThreeColumnConfig(
    val powerStream: PowerStream = PowerStream.S3,
)

fun Context.streamThreeColumnConfig(): Flow<ThreeColumnConfig> =
    dataStore.data
        .map { prefs ->
            prefs[threeColumnConfigKey]
                ?.let { runCatching { json.decodeFromString<ThreeColumnConfig>(it) }.getOrNull() }
                ?: ThreeColumnConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveThreeColumnConfig(config: ThreeColumnConfig) {
    dataStore.edit { it[threeColumnConfigKey] = json.encodeToString(config) }
}

// --- AvgSpeedConfig ---

// thresholdKph is always stored in km/h; converted to the user's preferred unit at display time.
@Serializable
data class AvgSpeedConfig(
    val thresholdKph: Double? = null,
)

fun Context.streamAvgSpeedConfig(includePaused: Boolean): Flow<AvgSpeedConfig> {
    val key = if (includePaused) avgSpeedTotalConfigKey else avgSpeedMovingConfigKey
    return dataStore.data
        .map { prefs ->
            prefs[key]
                ?.let { runCatching { json.decodeFromString<AvgSpeedConfig>(it) }.getOrNull() }
                ?: AvgSpeedConfig()
        }
        .distinctUntilChanged()
}

suspend fun Context.saveAvgSpeedConfig(includePaused: Boolean, config: AvgSpeedConfig) {
    val key = if (includePaused) avgSpeedTotalConfigKey else avgSpeedMovingConfigKey
    dataStore.edit { it[key] = json.encodeToString(config) }
}

// --- TimeConfig ---

@Serializable
enum class TimeFormat(val label: String) { COMPACT("Racing"), CLOCK("Clock") }

@Serializable
data class TimeConfig(val format: TimeFormat = TimeFormat.COMPACT)

private val timeConfigKey = stringPreferencesKey("time_config")

fun Context.streamTimeConfig(): Flow<TimeConfig> =
    dataStore.data
        .map { prefs ->
            prefs[timeConfigKey]
                ?.let { runCatching { json.decodeFromString<TimeConfig>(it) }.getOrNull() }
                ?: TimeConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveTimeConfig(config: TimeConfig) {
    dataStore.edit { it[timeConfigKey] = json.encodeToString(config) }
}
