package com.jpweytjens.barberfish.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jpweytjens.barberfish.datatype.TimeKind
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
private val gradeFieldConfigKey = stringPreferencesKey("grade_field_config")
private val avgPowerFieldConfigKey = stringPreferencesKey("avg_power_field_config")
private val npFieldConfigKey = stringPreferencesKey("np_field_config")
private val cadenceFieldConfigKey = stringPreferencesKey("cadence_field_config")

// --- HUDConfig ---

@Serializable
enum class ZoneColorMode(val label: String) {
    NONE("None"),
    TEXT("Text"),
    BACKGROUND("Fill"),
}

@Serializable
sealed interface HUDSlotField {
    @Serializable data object Speed : HUDSlotField

    @Serializable data object HR : HUDSlotField

    @Serializable data object Power : HUDSlotField

    @Serializable data object Cadence : HUDSlotField

    @Serializable data object AvgPower : HUDSlotField

    @Serializable data object NP : HUDSlotField

    @Serializable data object Grade : HUDSlotField

    @Serializable data class AvgSpeed(val includePaused: Boolean = false) : HUDSlotField

    @Serializable data class Time(val kind: TimeKind = TimeKind.TOTAL) : HUDSlotField
}

@Serializable
data class HUDSlotConfig(
    val field: HUDSlotField = HUDSlotField.Power,
    val powerSmoothing: PowerSmoothingStream = PowerSmoothingStream.S3,
    val speedSmoothing: SpeedSmoothingStream = SpeedSmoothingStream.S0,
    val cadenceSmoothing: CadenceSmoothingStream = CadenceSmoothingStream.S0,
    val avgSpeedConfig: AvgSpeedConfig = AvgSpeedConfig(),
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
)

@Serializable
data class SparklineConfig(
    val enabled: Boolean = true,
    val lookaheadKm: Int = 10,
    val skipBands: Int = 1,
)

@Serializable
data class HUDConfig(
    val columns: Int = 3,
    val leftSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Speed),
    val middleSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.HR),
    val rightSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Power),
    val fourthSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Cadence),
    val sparkline: SparklineConfig = SparklineConfig(),
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
    S0("0s", DataType.Type.POWER, DataType.Field.POWER),
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
    S0("0s", DataType.Type.SPEED, DataType.Field.SPEED),
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

// All speed values stored in km/h; converted to the user's preferred unit at display time.
// SINGLE mode: thresholdKph = 0.0 means disabled.
// MIN_MAX mode: null means that boundary is disabled (only-min or only-max behavior).
// rangePercentAbove/Below: % of threshold speed that maps to the fully-orange gradient edge.
@Serializable
enum class SpeedThresholdMode {
    SINGLE,
    MIN_MAX,
}

@Serializable
data class AvgSpeedConfig(
    val mode: SpeedThresholdMode = SpeedThresholdMode.SINGLE,
    val thresholdKph: Double = 0.0,
    val rangePercentAbove: Double = 10.0,
    val rangePercentBelow: Double = 10.0,
    val minKph: Double? = null,
    val maxKph: Double? = null,
)

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
    val gradePalette: GradePalette = GradePalette.WAHOO,
    val readableColors: Boolean = true,
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

// --- CadenceFieldConfig ---

@Serializable
enum class CadenceSmoothingStream(val label: String, val typeId: String, val fieldId: String) {
    S0("0s", DataType.Type.CADENCE, DataType.Field.CADENCE),
    S3("3s", DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE, DataType.Field.SMOOTHED_3S_AVERAGE_CADENCE),
    S5("5s", DataType.Type.SMOOTHED_5S_AVERAGE_CADENCE, DataType.Field.SMOOTHED_5S_AVERAGE_CADENCE),
    S10(
        "10s",
        DataType.Type.SMOOTHED_10S_AVERAGE_CADENCE,
        DataType.Field.SMOOTHED_10S_AVERAGE_CADENCE,
    ),
}

@Serializable
data class CadenceFieldConfig(val smoothing: CadenceSmoothingStream = CadenceSmoothingStream.S0)

fun Context.streamCadenceFieldConfig(): Flow<CadenceFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[cadenceFieldConfigKey]?.let {
                runCatching { json.decodeFromString<CadenceFieldConfig>(it) }.getOrNull()
            } ?: CadenceFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveCadenceFieldConfig(config: CadenceFieldConfig) {
    dataStore.edit { it[cadenceFieldConfigKey] = json.encodeToString(config) }
}

// --- AvgPowerFieldConfig ---

@Serializable data class AvgPowerFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

fun Context.streamAvgPowerFieldConfig(): Flow<AvgPowerFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[avgPowerFieldConfigKey]?.let {
                runCatching { json.decodeFromString<AvgPowerFieldConfig>(it) }.getOrNull()
            } ?: AvgPowerFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveAvgPowerFieldConfig(config: AvgPowerFieldConfig) {
    dataStore.edit { it[avgPowerFieldConfigKey] = json.encodeToString(config) }
}

// --- NPFieldConfig ---

@Serializable data class NPFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

fun Context.streamNPFieldConfig(): Flow<NPFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[npFieldConfigKey]?.let {
                runCatching { json.decodeFromString<NPFieldConfig>(it) }.getOrNull()
            } ?: NPFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveNPFieldConfig(config: NPFieldConfig) {
    dataStore.edit { it[npFieldConfigKey] = json.encodeToString(config) }
}

// --- GradeFieldConfig ---

@Serializable
enum class GradePalette(val label: String) {
    KAROO("Karoo"),
    WAHOO("Wahoo"),
    GARMIN("Garmin"),
    HSLUV("HSLuv"),
}

@Serializable data class GradeFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

fun Context.streamGradeFieldConfig(): Flow<GradeFieldConfig> =
    dataStore.data
        .map { prefs ->
            prefs[gradeFieldConfigKey]?.let {
                runCatching { json.decodeFromString<GradeFieldConfig>(it) }.getOrNull()
            } ?: GradeFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveGradeFieldConfig(config: GradeFieldConfig) {
    dataStore.edit { it[gradeFieldConfigKey] = json.encodeToString(config) }
}

// --- TimeConfig ---

@Serializable
enum class TimeFormat(val label: String) {
    RACING("Racing"),
    CLOCK("Clock"),
    HM_S("Segments"),
}

@Serializable data class TimeConfig(val format: TimeFormat = TimeFormat.RACING)

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
