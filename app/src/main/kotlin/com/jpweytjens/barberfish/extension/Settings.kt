package com.jpweytjens.barberfish.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jpweytjens.barberfish.datatype.ETAKind
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

private inline fun <reified T> Context.streamConfig(
    key: Preferences.Key<String>,
    default: T,
): Flow<T> =
    dataStore.data
        .map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() } ?: default
        }
        .distinctUntilChanged()

private suspend inline fun <reified T> Context.saveConfig(
    key: Preferences.Key<String>,
    config: T,
) {
    dataStore.edit { it[key] = json.encodeToString(config) }
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

    @Serializable data object LapPower : HUDSlotField

    @Serializable data object LastLapPower : HUDSlotField

    @Serializable data object AvgHR : HUDSlotField

    @Serializable data object LapAvgHR : HUDSlotField

    @Serializable data object LastLapAvgHR : HUDSlotField

    @Serializable data object Grade : HUDSlotField

    @Serializable data class AvgSpeed(val includePaused: Boolean = false) : HUDSlotField

    @Serializable data class Time(val kind: TimeKind = TimeKind.TOTAL) : HUDSlotField

    @Serializable data class ETA(val kind: ETAKind = ETAKind.TIME_TO_DESTINATION) : HUDSlotField
}

@Serializable
data class HUDSlotConfig(
    val field: HUDSlotField = HUDSlotField.Power,
    val powerSmoothing: PowerSmoothingStream = PowerSmoothingStream.S3,
    val speedSmoothing: SpeedSmoothingStream = SpeedSmoothingStream.S0,
    val cadenceSmoothing: CadenceSmoothingStream = CadenceSmoothingStream.S0,
    val avgSpeedConfig: AvgSpeedConfig = AvgSpeedConfig(),
    val cadenceThreshold: CadenceThresholdConfig = CadenceThresholdConfig(),
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
    val fourthSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Grade),
    val sparkline: SparklineConfig = SparklineConfig(),
)

fun Context.streamHUDConfig(): Flow<HUDConfig> =
    streamConfig(threeColumnConfigKey, HUDConfig())

suspend fun Context.saveHUDConfig(config: HUDConfig) =
    saveConfig(threeColumnConfigKey, config)

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
    streamConfig(powerFieldConfigKey, PowerFieldConfig())

suspend fun Context.savePowerFieldConfig(config: PowerFieldConfig) =
    saveConfig(powerFieldConfigKey, config)

// --- HRFieldConfig ---

@Serializable data class HRFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

private val hrFieldConfigKey = stringPreferencesKey("hr_field_config")
private val avgHrFieldConfigKey = stringPreferencesKey("avg_hr_field_config")
private val lapAvgHrFieldConfigKey = stringPreferencesKey("lap_avg_hr_field_config")
private val lastLapAvgHrFieldConfigKey = stringPreferencesKey("last_lap_avg_hr_field_config")

enum class HRFieldKind(internal val key: Preferences.Key<String>) {
    HR(hrFieldConfigKey),
    AVG(avgHrFieldConfigKey),
    LAP_AVG(lapAvgHrFieldConfigKey),
    LAST_LAP_AVG(lastLapAvgHrFieldConfigKey),
}

fun Context.streamHRFieldConfig(kind: HRFieldKind = HRFieldKind.HR): Flow<HRFieldConfig> =
    streamConfig(kind.key, HRFieldConfig())

suspend fun Context.saveHRFieldConfig(kind: HRFieldKind = HRFieldKind.HR, config: HRFieldConfig) =
    saveConfig(kind.key, config)

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
    streamConfig(speedFieldConfigKey, SpeedFieldConfig())

suspend fun Context.saveSpeedFieldConfig(config: SpeedFieldConfig) =
    saveConfig(speedFieldConfigKey, config)

// --- AvgSpeedConfig ---

// All speed values stored in km/h; converted to the user's preferred unit at display time.
// TARGET mode: thresholdKph = 0.0 means disabled.
// MIN_MAX mode: null means that boundary is disabled (only-min or only-max behavior).
// rangePercentAbove/Below: % of threshold speed that maps to the fully-orange gradient edge.
@Serializable
enum class ThresholdMode {
    TARGET,
    MIN_MAX,
}

@Serializable
data class AvgSpeedConfig(
    val mode: ThresholdMode = ThresholdMode.TARGET,
    val thresholdKph: Double = 0.0,
    val rangePercentAbove: Double = 10.0,
    val rangePercentBelow: Double = 10.0,
    val minKph: Double? = null,
    val maxKph: Double? = null,
)

fun Context.streamAvgSpeedConfig(includePaused: Boolean): Flow<AvgSpeedConfig> =
    streamConfig(
        if (includePaused) avgSpeedTotalConfigKey else avgSpeedMovingConfigKey,
        AvgSpeedConfig(),
    )

suspend fun Context.saveAvgSpeedConfig(includePaused: Boolean, config: AvgSpeedConfig) =
    saveConfig(
        if (includePaused) avgSpeedTotalConfigKey else avgSpeedMovingConfigKey,
        config,
    )

// --- ZoneConfig ---

@Serializable
data class ZoneConfig(
    val hrPalette: ZonePalette = ZonePalette.KAROO,
    val powerPalette: ZonePalette = ZonePalette.KAROO,
    val gradePalette: GradePalette = GradePalette.KAROO,
    val readableColors: Boolean = true,
)

fun Context.streamZoneConfig(): Flow<ZoneConfig> =
    streamConfig(zoneConfigKey, ZoneConfig())

suspend fun Context.saveZoneConfig(config: ZoneConfig) =
    saveConfig(zoneConfigKey, config)

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
data class CadenceThresholdConfig(
    val mode: ThresholdMode = ThresholdMode.TARGET,
    val thresholdRpm: Double = 0.0,
    val rangePercentAbove: Double = 10.0,
    val rangePercentBelow: Double = 10.0,
    val minRpm: Double? = null,
    val maxRpm: Double? = null,
)

@Serializable
data class CadenceFieldConfig(
    val smoothing: CadenceSmoothingStream = CadenceSmoothingStream.S0,
    val threshold: CadenceThresholdConfig = CadenceThresholdConfig(),
)

fun Context.streamCadenceFieldConfig(): Flow<CadenceFieldConfig> =
    streamConfig(cadenceFieldConfigKey, CadenceFieldConfig())

suspend fun Context.saveCadenceFieldConfig(config: CadenceFieldConfig) =
    saveConfig(cadenceFieldConfigKey, config)

// --- AvgPowerFieldConfig ---

@Serializable data class AvgPowerFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

fun Context.streamAvgPowerFieldConfig(): Flow<AvgPowerFieldConfig> =
    streamConfig(avgPowerFieldConfigKey, AvgPowerFieldConfig())

suspend fun Context.saveAvgPowerFieldConfig(config: AvgPowerFieldConfig) =
    saveConfig(avgPowerFieldConfigKey, config)

// --- NPFieldConfig ---

@Serializable data class NPFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

fun Context.streamNPFieldConfig(): Flow<NPFieldConfig> =
    streamConfig(npFieldConfigKey, NPFieldConfig())

suspend fun Context.saveNPFieldConfig(config: NPFieldConfig) =
    saveConfig(npFieldConfigKey, config)

// --- LapPowerFieldConfig ---

@Serializable data class LapPowerFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

private val lapPowerFieldConfigKey = stringPreferencesKey("lap_power_field_config")
private val lastLapPowerFieldConfigKey = stringPreferencesKey("last_lap_power_field_config")

fun Context.streamLapPowerFieldConfig(isLastLap: Boolean): Flow<LapPowerFieldConfig> =
    streamConfig(
        if (isLastLap) lastLapPowerFieldConfigKey else lapPowerFieldConfigKey,
        LapPowerFieldConfig(),
    )

suspend fun Context.saveLapPowerFieldConfig(isLastLap: Boolean, config: LapPowerFieldConfig) =
    saveConfig(
        if (isLastLap) lastLapPowerFieldConfigKey else lapPowerFieldConfigKey,
        config,
    )

// --- GradeFieldConfig ---

@Serializable
enum class GradePalette(val label: String) {
    KAROO("Karoo"),
    WAHOO("Wahoo"),
    GARMIN("Garmin"),
    HSLUV("HSLuv"),
    ZWIFT("Zwift"),
}

@Serializable data class GradeFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

fun Context.streamGradeFieldConfig(): Flow<GradeFieldConfig> =
    streamConfig(gradeFieldConfigKey, GradeFieldConfig())

suspend fun Context.saveGradeFieldConfig(config: GradeFieldConfig) =
    saveConfig(gradeFieldConfigKey, config)

// --- ETAConfig ---

@Serializable
data class ETAConfig(
    val priorSpeedKph: Double = 25.0,
)

private val etaConfigKey = stringPreferencesKey("eta_config")

fun Context.streamETAConfig(): Flow<ETAConfig> =
    streamConfig(etaConfigKey, ETAConfig())

suspend fun Context.saveETAConfig(config: ETAConfig) =
    saveConfig(etaConfigKey, config)

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
    streamConfig(timeConfigKey, TimeConfig())

suspend fun Context.saveTimeConfig(config: TimeConfig) =
    saveConfig(timeConfigKey, config)
