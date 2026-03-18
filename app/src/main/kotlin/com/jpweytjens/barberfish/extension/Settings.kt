package com.jpweytjens.barberfish.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jpweytjens.barberfish.datatype.shared.ZonePalette
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "barberfish")

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val threeColumnConfigKey = stringPreferencesKey("three_column_config")
private val avgSpeedTotalConfigKey = stringPreferencesKey("avg_speed_total_config")
private val avgSpeedMovingConfigKey = stringPreferencesKey("avg_speed_moving_config")
private val zoneConfigKey = stringPreferencesKey("zone_config")

// --- HUDConfig (was ThreeColumnConfig) ---

@Serializable
enum class ZoneColorMode(val label: String) {
    NONE("None"),
    TEXT("Text"),
    BACKGROUND("Background"),
}

@Serializable
data class HUDConfig(
    val powerStream: PowerSmoothingStream = PowerSmoothingStream.S3,
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
)

fun Context.streamHUDConfig(): Flow<HUDConfig> =
    dataStore.data
        .map { prefs ->
            prefs[threeColumnConfigKey]?.let {
                runCatching { json.decodeFromString<HUDConfig>(it) }.getOrNull()
            } ?: HUDConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveHUDConfig(config: HUDConfig) {
    dataStore.edit { it[threeColumnConfigKey] = json.encodeToString(config) }
}

// --- PowerFieldConfig ---

@Serializable
enum class PowerSmoothingStream(val label: String, val typeId: String, val fieldId: String) {
    S0("Off", DataType.Type.POWER, DataType.Field.POWER),
    S3("3s", DataType.Type.SMOOTHED_3S_AVERAGE_POWER, DataType.Field.SMOOTHED_3S_AVERAGE_POWER),
    S5("5s", DataType.Type.SMOOTHED_5S_AVERAGE_POWER, DataType.Field.SMOOTHED_5S_AVERAGE_POWER),
    S10("10s", DataType.Type.SMOOTHED_10S_AVERAGE_POWER, DataType.Field.SMOOTHED_10S_AVERAGE_POWER),
    S30("30s", DataType.Type.SMOOTHED_30S_AVERAGE_POWER, DataType.Field.SMOOTHED_30S_AVERAGE_POWER),
    M20("20m", DataType.Type.SMOOTHED_20M_AVERAGE_POWER, DataType.Field.SMOOTHED_20M_AVERAGE_POWER),
    H1("1h", DataType.Type.SMOOTHED_1HR_AVERAGE_POWER, DataType.Field.SMOOTHED_1HR_AVERAGE_POWER),
}

@Serializable
data class PowerFieldConfig(
    val smoothing: PowerSmoothingStream = PowerSmoothingStream.S3,
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
)

private val powerFieldConfigKey = stringPreferencesKey("power_field_config")

fun Context.streamPowerFieldConfig(): Flow<PowerFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[powerFieldConfigKey]?.let {
                runCatching { json.decodeFromString<PowerFieldConfig>(it) }.getOrNull()
            } ?: PowerFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.savePowerFieldConfig(config: PowerFieldConfig) {
    dataStore.edit { it[powerFieldConfigKey] = json.encodeToString(config) }
}

// --- HRFieldConfig ---

@Serializable data class HRFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

private val hrFieldConfigKey = stringPreferencesKey("hr_field_config")

fun Context.streamHRFieldConfig(): Flow<HRFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[hrFieldConfigKey]?.let {
                runCatching { json.decodeFromString<HRFieldConfig>(it) }.getOrNull()
            } ?: HRFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveHRFieldConfig(config: HRFieldConfig) {
    dataStore.edit { it[hrFieldConfigKey] = json.encodeToString(config) }
}

// --- SpeedFieldConfig ---

@Serializable
enum class SpeedSmoothingStream(val label: String, val typeId: String, val fieldId: String) {
    S0("Off", DataType.Type.SPEED, DataType.Field.SPEED),
    S3("3s", DataType.Type.SMOOTHED_3S_AVERAGE_SPEED, DataType.Field.SMOOTHED_3S_AVERAGE_SPEED),
    S5("5s", DataType.Type.SMOOTHED_5S_AVERAGE_SPEED, DataType.Field.SMOOTHED_5S_AVERAGE_SPEED),
    S10("10s", DataType.Type.SMOOTHED_10S_AVERAGE_SPEED, DataType.Field.SMOOTHED_10S_AVERAGE_SPEED),
}

@Serializable
data class SpeedFieldConfig(val smoothing: SpeedSmoothingStream = SpeedSmoothingStream.S3)

private val speedFieldConfigKey = stringPreferencesKey("speed_field_config")

fun Context.streamSpeedFieldConfig(): Flow<SpeedFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[speedFieldConfigKey]?.let {
                runCatching { json.decodeFromString<SpeedFieldConfig>(it) }.getOrNull()
            } ?: SpeedFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveSpeedFieldConfig(config: SpeedFieldConfig) {
    dataStore.edit { it[speedFieldConfigKey] = json.encodeToString(config) }
}

// --- AvgSpeedConfig ---

// thresholdKph is always stored in km/h; converted to the user's preferred unit at display time.
@Serializable data class AvgSpeedConfig(val thresholdKph: Double? = null)

fun Context.streamAvgSpeedConfig(includePaused: Boolean): Flow<AvgSpeedConfig> {
    val key = if (includePaused) avgSpeedTotalConfigKey else avgSpeedMovingConfigKey
    return dataStore.data
        .map { prefs ->
            prefs[key]?.let {
                runCatching { json.decodeFromString<AvgSpeedConfig>(it) }.getOrNull()
            } ?: AvgSpeedConfig()
        }
        .distinctUntilChanged()
}

suspend fun Context.saveAvgSpeedConfig(includePaused: Boolean, config: AvgSpeedConfig) {
    val key = if (includePaused) avgSpeedTotalConfigKey else avgSpeedMovingConfigKey
    dataStore.edit { it[key] = json.encodeToString(config) }
}

// --- ZoneConfig ---

@Serializable
data class ZoneConfig(
    val hrPalette: ZonePalette = ZonePalette.KAROO,
    val powerPalette: ZonePalette = ZonePalette.KAROO,
)

fun Context.streamZoneConfig(): Flow<ZoneConfig> =
    dataStore.data
        .map { prefs ->
            prefs[zoneConfigKey]?.let {
                runCatching { json.decodeFromString<ZoneConfig>(it) }.getOrNull()
            } ?: ZoneConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveZoneConfig(config: ZoneConfig) {
    dataStore.edit { it[zoneConfigKey] = json.encodeToString(config) }
}

// --- TimeConfig ---

@Serializable
enum class TimeFormat(val label: String) {
    COMPACT("Racing"),
    CLOCK("Clock"),
    HM_S("Segments"),
}

@Serializable data class TimeConfig(val format: TimeFormat = TimeFormat.COMPACT)

private val timeConfigKey = stringPreferencesKey("time_config")

fun Context.streamTimeConfig(): Flow<TimeConfig> =
    dataStore.data
        .map { prefs ->
            prefs[timeConfigKey]?.let {
                runCatching { json.decodeFromString<TimeConfig>(it) }.getOrNull()
            } ?: TimeConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveTimeConfig(config: TimeConfig) {
    dataStore.edit { it[timeConfigKey] = json.encodeToString(config) }
}
