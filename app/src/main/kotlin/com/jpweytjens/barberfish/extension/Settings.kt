package com.jpweytjens.barberfish.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
data class ThreeColumnConfig(
    val thirdColumn: ThirdColumnMetric = ThirdColumnMetric.SPEED,
)

@Serializable
enum class ThirdColumnMetric(val label: String) {
    SPEED("Speed"),
    CADENCE("Cadence"),
    DISTANCE("Distance"),
    ELEVATION_GAIN("Ascent"),
    GRADE("Grade"),
    TEMPERATURE("Temp"),
}

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
