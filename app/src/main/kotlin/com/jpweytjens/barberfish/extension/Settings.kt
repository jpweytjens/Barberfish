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
import com.jpweytjens.barberfish.datatype.shared.gradeBandStops
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
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

private val sparklineConfigKey = stringPreferencesKey("sparkline_config")
private val fieldSparklineConfigKey = stringPreferencesKey("field_sparkline_config")
// Persisted as "three_column_config" for backwards compatibility with installs from the
// pre-4-column era; the HUDConfig blob covers both layouts now.
private val hudConfigKey = stringPreferencesKey("three_column_config")
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

    @Serializable data object PowerZone : HUDSlotField

    @Serializable data object MaxPower : HUDSlotField

    @Serializable data object AvgHR : HUDSlotField

    @Serializable data object LapAvgHR : HUDSlotField

    @Serializable data object LastLapAvgHR : HUDSlotField

    @Serializable data object HRMaxPercent : HUDSlotField

    @Serializable data object MaxHR : HUDSlotField

    @Serializable data object HRZone : HUDSlotField

    @Serializable data object Grade : HUDSlotField

    @Serializable data object Distance : HUDSlotField

    @Serializable data object DistanceRemaining : HUDSlotField

    @Serializable data object ElevationRemaining : HUDSlotField

    @Serializable data object DescentRemaining : HUDSlotField

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
    val zoneDisplayMode: ZoneDisplayMode = ZoneDisplayMode.FLOAT,
    val gradePrecision: ZoneDisplayMode = ZoneDisplayMode.FLOAT,
    val gradeShowPercentSign: Boolean = true,
)

@Serializable
enum class ElevationSimplification(val label: String, val minAreaM2: Float) {
    NONE("Off", 0f),
    MILD("Mild", 25f), // just above the 21 m² rainbow-noise floor
    MEDIUM("Medium", 60f), // merges most micro-wiggles
    HEAVY("Max", 120f), // abstract blocks; preserves sharp flat→climb corners
}

// Whole-route overview simplification. Uses a vertex budget (target point count) rather
// than an absolute m² area floor: at full-route zoom an area threshold removes only
// sub-pixel noise, whereas a fixed vertex count visibly coarsens the profile and reads the
// same on a 20 km route or a 200 km one. NONE keeps every point.
@Serializable
enum class RouteSimplification(val label: String, val targetCount: Int) {
    NONE("Off", Int.MAX_VALUE),
    MILD("Mild", 120),
    MEDIUM("Medium", 50),
    HEAVY("Max", 20),
}

@Serializable
enum class SparklineWarp(val label: String, val k: Float, val positionFraction: Float) {
    NONE("Off", 0f, 0.1235f),
    MILD("Mild", 4f, 0.0783f),
    MEDIUM("Medium", 8f, 0.05f),
    HEAVY("Max", 12f, 0.0357f),
}

@Serializable
enum class ElevationZoom(val label: String, val minRangeM: Float) {
    CLOSE("Close", 20f),
    NORMAL("Normal", 50f),
    WIDE("Wide", 100f),
}

@Serializable
enum class SparklineMode {
    OFF,
    CLIMBS,
    ON,
}

/**
 * The (climb, descent) grade edges a stored pair of band-skip counts means, read off [palette]'s
 * own band stops.
 *
 * Counts stopped being portable when 4.x added the Barberfish palette: its climb bands have no 0.0
 * entry (they jump straight from 2.0 to -2.0), so the same count resolves to a different threshold
 * on Barberfish than on the other six palettes, which all open a band at 0.0. Persisting the
 * threshold instead of the count keeps a stored config meaning what it meant when it was written.
 *
 * A count of 0 ("Off") maps to an edge of 0.0 on both sides, keeping exactly what the count meant:
 * colour every climb from grade 0 up, colour every descent. Counts of 1 and up step outward through
 * the stops and clamp to the last one. A side with no bands at all (every palette but Barberfish
 * and Turbo, on the descent side) has no edge and stays uncoloured.
 */
internal fun edgesFromSkipBands(
    skipBands: Int,
    skipBandsDescent: Int,
    palette: GradePalette,
): Pair<Double?, Double?> {
    val stops = gradeBandStops(palette)
    return stops.climb.stopAt(skipBands) to stops.descent.stopAt(skipBandsDescent)
}

private fun List<Double>.stopAt(skipCount: Int): Double? =
    when {
        isEmpty() -> null
        skipCount <= 0 -> 0.0
        else -> this[(skipCount - 1).coerceAtMost(lastIndex)]
    }

@Serializable
data class SparklineConfig(
    // `mode` is nullable so an absent key falls through to the legacy `enabled` boolean
    // (pre-3.x installs) instead of masking it with a default. Resolve via `hudMode`.
    val mode: SparklineMode? = null,
    @SerialName("enabled") private val legacyEnabled: Boolean? = null,
    val lookaheadKm: Int = 5,
    // Legacy band-skip counts, superseded by climbEdge/descentEdge. Nothing writes them since
    // the edge sliders replaced the count selectors; they persist as migration input for stored
    // configs. Read them through `gradeEdges` rather than directly.
    val skipBands: Int = 1,
    val skipBandsDescent: Int = 0,
    // Grade thresholds at and beyond which a side takes its band colour, written by the profile
    // card's GradeBandSlider. Null means unset, so an absent key falls through to the migrated
    // legacy counts instead of masking them; a side is turned off by parking its edge past the
    // palette's last stop (the sliders store GRADE_EDGE_OFF), not by storing null.
    val climbEdge: Double? = null,
    val descentEdge: Double? = null,
    val simplification: ElevationSimplification = ElevationSimplification.HEAVY,
    val warp: SparklineWarp = SparklineWarp.MILD,
    val yZoom: ElevationZoom = ElevationZoom.NORMAL,
    val showClimbs: Boolean = true,
    val showPois: Boolean = true,
    val showHeader: Boolean = true,
) {
    val hudMode: SparklineMode
        get() = mode ?: if (legacyEnabled == false) SparklineMode.OFF else SparklineMode.ON

    /**
     * The resolved (climb, descent) edges: the stored thresholds when set, otherwise the legacy
     * counts migrated through [palette]. A null in the result means that side stays uncoloured.
     */
    fun gradeEdges(palette: GradePalette): Pair<Double?, Double?> {
        val (climb, descent) = edgesFromSkipBands(skipBands, skipBandsDescent, palette)
        return (climbEdge ?: climb) to (descentEdge ?: descent)
    }
}

@Serializable
data class HUDConfig(
    val columns: Int = 3,
    val leftSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Speed),
    val middleSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.HR),
    val rightSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Power),
    val fourthSlot: HUDSlotConfig = HUDSlotConfig(field = HUDSlotField.Grade),
    val sparkline: SparklineConfig = SparklineConfig(),
)

fun Context.streamHUDConfig(): Flow<HUDConfig> = streamConfig(hudConfigKey, HUDConfig())

suspend fun Context.saveHUDConfig(config: HUDConfig) = saveConfig(hudConfigKey, config)

// --- SparklineConfig ---
// Two independent instances: the HUD strip and the standalone elevation-sparkline field.
// The HUD instance keeps the original "sparkline_config" key; the field instance has its
// own key and, when unset, falls back to plain defaults. The two are not linked: the field
// never reads the HUD value, and its mode is always ON (see toFieldConfig) so a placed field
// always renders, never inheriting the HUD's OFF/CLIMBS strip behaviour.

// Resolves the HUD sparkline config: own key first, then the legacy embedded HUDConfig
// blob (pre-split installs), then defaults.
private fun Preferences.hudSparklineConfig(): SparklineConfig =
    this[sparklineConfigKey]?.let {
        runCatching { json.decodeFromString<SparklineConfig>(it) }.getOrNull()
    }
        ?: this[hudConfigKey]
            ?.let { runCatching { json.decodeFromString<HUDConfig>(it) }.getOrNull() }
            ?.sparkline
        ?: SparklineConfig()

fun Context.streamHudSparklineConfig(): Flow<SparklineConfig> =
    dataStore.data.map { it.hudSparklineConfig() }.distinctUntilChanged()

suspend fun Context.saveHudSparklineConfig(config: SparklineConfig) =
    saveConfig(sparklineConfigKey, config)

// Coerces a stored field config (or none) into the effective field config: plain defaults
// when unset, and mode forced to ON so the standalone field always renders regardless of
// any OFF/CLIMBS value baked in by a past tap or inherited before the surfaces were split.
fun SparklineConfig?.toFieldConfig(): SparklineConfig =
    (this ?: SparklineConfig()).copy(mode = SparklineMode.ON)

fun Context.streamFieldSparklineConfig(): Flow<SparklineConfig> =
    dataStore.data
        .map { prefs ->
            prefs[fieldSparklineConfigKey]
                ?.let {
                    runCatching { json.decodeFromString<SparklineConfig>(it) }.getOrNull()
                }
                .toFieldConfig()
        }
        .distinctUntilChanged()

suspend fun Context.saveFieldSparklineConfig(config: SparklineConfig) =
    saveConfig(fieldSparklineConfigKey, config)

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

// --- HRMaxPercentFieldConfig ---

@Serializable data class HRMaxPercentFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

private val hrMaxPercentFieldConfigKey = stringPreferencesKey("hr_max_percent_field_config")

fun Context.streamHRMaxPercentFieldConfig(): Flow<HRMaxPercentFieldConfig> =
    streamConfig(hrMaxPercentFieldConfigKey, HRMaxPercentFieldConfig())

suspend fun Context.saveHRMaxPercentFieldConfig(config: HRMaxPercentFieldConfig) =
    saveConfig(hrMaxPercentFieldConfigKey, config)

// --- MaxHRFieldConfig ---

@Serializable data class MaxHRFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

private val maxHrFieldConfigKey = stringPreferencesKey("max_hr_field_config")

fun Context.streamMaxHRFieldConfig(): Flow<MaxHRFieldConfig> =
    streamConfig(maxHrFieldConfigKey, MaxHRFieldConfig())

suspend fun Context.saveMaxHRFieldConfig(config: MaxHRFieldConfig) =
    saveConfig(maxHrFieldConfigKey, config)

// --- HRZoneFieldConfig ---

@Serializable
enum class ZoneDisplayMode(val label: String) {
    INTEGER("Integer"),
    FLOAT("Decimal"),
}

@Serializable
data class HRZoneFieldConfig(
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    val zoneDisplayMode: ZoneDisplayMode = ZoneDisplayMode.FLOAT,
)

private val hrZoneFieldConfigKey = stringPreferencesKey("hr_zone_field_config")

fun Context.streamHRZoneFieldConfig(): Flow<HRZoneFieldConfig> =
    streamConfig(hrZoneFieldConfigKey, HRZoneFieldConfig())

suspend fun Context.saveHRZoneFieldConfig(config: HRZoneFieldConfig) =
    saveConfig(hrZoneFieldConfigKey, config)

// --- SpeedFieldConfig ---

@Serializable
enum class SpeedSmoothingStream(val label: String, val typeId: String, val fieldId: String) {
    S0("0s", DataType.Type.SPEED, DataType.Field.SPEED),
    S3("3s", DataType.Type.SMOOTHED_3S_AVERAGE_SPEED, DataType.Field.SMOOTHED_3S_AVERAGE_SPEED),
    S5("5s", DataType.Type.SMOOTHED_5S_AVERAGE_SPEED, DataType.Field.SMOOTHED_5S_AVERAGE_SPEED),
    S10("10s", DataType.Type.SMOOTHED_10S_AVERAGE_SPEED, DataType.Field.SMOOTHED_10S_AVERAGE_SPEED),
}

// SpeedThresholdSource selects what the live speed is compared against for color:
// FIXED — the user-entered thresholdKph (0.0 disables coloring).
// AVG_TOTAL / AVG_MOVING — the ride's running average (including / excluding paused time).
// AVG sources are gated by ELAPSED_TIME >= 30 s so a near-zero startup avg doesn't flash colors.
@Serializable
enum class SpeedThresholdSource {
    FIXED,
    AVG_TOTAL,
    AVG_MOVING,
}

@Serializable
data class SpeedFieldConfig(
    val smoothing: SpeedSmoothingStream = SpeedSmoothingStream.S0,
    val source: SpeedThresholdSource = SpeedThresholdSource.FIXED,
    val thresholdKph: Double = 0.0,
    val rangePercentBelow: Double = 10.0,
    val rangePercentAbove: Double = 10.0,
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
)

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
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
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
)

fun Context.streamZoneConfig(): Flow<ZoneConfig> = streamConfig(zoneConfigKey, ZoneConfig())

suspend fun Context.saveZoneConfig(config: ZoneConfig) = saveConfig(zoneConfigKey, config)

// --- GradeMapConfig ---

@Serializable
data class GradeMapConfig(
    val enabled: Boolean = true,
    val showPolylines: Boolean = true,
    val showChevrons: Boolean = true,
    // When true, skipBands/simplification are taken from the field sparkline config
    // at the consumer via resolveGradeMapTuning(); the two fields below are ignored.
    val syncWithSparkline: Boolean = true,
    // Legacy band-skip count, superseded by climbEdge/descentEdge. Read through `gradeEdges`.
    // The map never had a descent count, so its descent edge migrates as if the count were 0.
    val skipBands: Int = 1,
    // Written by the GRADE MAP card's GradeBandSlider when tuning is independent; a parked
    // side stores GRADE_EDGE_OFF. Null means unset and falls through to the migrated legacy
    // count above.
    val climbEdge: Double? = null,
    val descentEdge: Double? = null,
    val simplification: ElevationSimplification = ElevationSimplification.HEAVY,
) {
    /**
     * The resolved (climb, descent) edges of *this* config: the stored thresholds when set,
     * otherwise the legacy count migrated through [palette]. A null in the result means that side
     * stays uncoloured.
     *
     * WARNING: this is the overlay's own answer, not the effective one. It ignores
     * [syncWithSparkline], which defaults to true, so a synced overlay follows the field
     * sparkline's edges instead. Render paths must take theirs from `resolveGradeMapTuning(map,
     * sparkline, palette)`, which applies the sync the same way it already does for the other
     * shared settings.
     *
     * The descent edge here is the migrated count only, and the map has no descent count, so it
     * always resolves as 0. An unsynced overlay takes its descent edge from the field sparkline
     * instead; only the climb side of this pair is the overlay's own.
     */
    fun gradeEdges(palette: GradePalette): Pair<Double?, Double?> {
        val (climb, descent) =
            edgesFromSkipBands(skipBands, skipBandsDescent = 0, palette = palette)
        return (climbEdge ?: climb) to (descentEdge ?: descent)
    }
}

private val gradeMapConfigKey = stringPreferencesKey("grade_map_config")

fun Context.streamGradeMapConfig(): Flow<GradeMapConfig> =
    streamConfig(gradeMapConfigKey, GradeMapConfig())

suspend fun Context.saveGradeMapConfig(config: GradeMapConfig) =
    saveConfig(gradeMapConfigKey, config)

/**
 * Id spans the last grade map emission minted, per symbol kind. The rideapp keeps drawn map symbols
 * across extension process death and startMap restarts, so a fresh startMap hides `0 until span` of
 * each kind before drawing — clearing whatever a dead predecessor left. Written before each
 * emission so a death between write and draw errs towards over-hiding, which is a no-op.
 */
@Serializable data class GradeMapDrawnIdSpans(val segments: Int = 0, val chevrons: Int = 0)

private val gradeMapDrawnIdSpansKey = stringPreferencesKey("grade_map_drawn_id_spans")

fun Context.streamGradeMapDrawnIdSpans(): Flow<GradeMapDrawnIdSpans> =
    streamConfig(gradeMapDrawnIdSpansKey, GradeMapDrawnIdSpans())

suspend fun Context.saveGradeMapDrawnIdSpans(spans: GradeMapDrawnIdSpans) =
    saveConfig(gradeMapDrawnIdSpansKey, spans)

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
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
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

suspend fun Context.saveNPFieldConfig(config: NPFieldConfig) = saveConfig(npFieldConfigKey, config)

// --- EffortFieldConfig ---

@Serializable data class EffortFieldConfig(val climbFirst: Boolean = false)

private val effortFieldConfigKey = stringPreferencesKey("remaining_effort_field_config")

fun Context.streamEffortFieldConfig(): Flow<EffortFieldConfig> =
    streamConfig(effortFieldConfigKey, EffortFieldConfig())

suspend fun Context.saveEffortFieldConfig(config: EffortFieldConfig) =
    saveConfig(effortFieldConfigKey, config)

// --- RouteRemainingConfig ---
// Uses RouteSimplification (a vertex-budget enum) rather than the sparkline's m² floor: an
// absolute area threshold is invisible at full-route zoom. Only simplification is exposed —
// the field shows the whole route, so a Y-zoom floor would be meaningless. Default MEDIUM
// gives a readable profile without erasing the route's shape.

@Serializable
data class RouteRemainingConfig(
    val simplification: RouteSimplification = RouteSimplification.MEDIUM,
    val showHeader: Boolean = true,
)

private val routeRemainingConfigKey = stringPreferencesKey("route_remaining_field_config")

fun Context.streamRouteRemainingConfig(): Flow<RouteRemainingConfig> =
    streamConfig(routeRemainingConfigKey, RouteRemainingConfig())

suspend fun Context.saveRouteRemainingConfig(config: RouteRemainingConfig) =
    saveConfig(routeRemainingConfigKey, config)

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

// --- PowerZoneFieldConfig ---

@Serializable
data class PowerZoneFieldConfig(
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    val zoneDisplayMode: ZoneDisplayMode = ZoneDisplayMode.FLOAT,
)

private val powerZoneFieldConfigKey = stringPreferencesKey("power_zone_field_config")

fun Context.streamPowerZoneFieldConfig(): Flow<PowerZoneFieldConfig> =
    streamConfig(powerZoneFieldConfigKey, PowerZoneFieldConfig())

suspend fun Context.savePowerZoneFieldConfig(config: PowerZoneFieldConfig) =
    saveConfig(powerZoneFieldConfigKey, config)

// --- MaxPowerFieldConfig ---

@Serializable data class MaxPowerFieldConfig(val colorMode: ZoneColorMode = ZoneColorMode.TEXT)

private val maxPowerFieldConfigKey = stringPreferencesKey("max_power_field_config")

fun Context.streamMaxPowerFieldConfig(): Flow<MaxPowerFieldConfig> =
    streamConfig(maxPowerFieldConfigKey, MaxPowerFieldConfig())

suspend fun Context.saveMaxPowerFieldConfig(config: MaxPowerFieldConfig) =
    saveConfig(maxPowerFieldConfigKey, config)

// --- GradeFieldConfig ---

@Serializable
enum class GradePalette(val label: String) {
    BARBERFISH("Barberfish"),
    KAROO("Karoo"),
    WAHOO("Wahoo"),
    GARMIN("Garmin"),
    HSLUV("HSLuv"),
    ZWIFT("Zwift"),
    TURBO("Turbo"),
}

@Serializable
data class GradeFieldConfig(
    val colorMode: ZoneColorMode = ZoneColorMode.TEXT,
    val precision: ZoneDisplayMode = ZoneDisplayMode.FLOAT,
    val showPercentSign: Boolean = true,
)

fun Context.streamGradeFieldConfig(): Flow<GradeFieldConfig> =
    streamConfig(gradeFieldConfigKey, GradeFieldConfig())

suspend fun Context.saveGradeFieldConfig(config: GradeFieldConfig) =
    saveConfig(gradeFieldConfigKey, config)

// --- ETAConfig ---

@Serializable data class ETAConfig(val priorSpeedKph: Double = 25.0)

private val etaConfigKey = stringPreferencesKey("eta_config")

fun Context.streamETAConfig(): Flow<ETAConfig> = streamConfig(etaConfigKey, ETAConfig())

suspend fun Context.saveETAConfig(config: ETAConfig) = saveConfig(etaConfigKey, config)

// --- TimeConfig ---

@Serializable
enum class TimeFormat(val label: String) {
    RACING("Racing"),
    CLOCK("Clock"),
    HM_S("Segments"),
}

@Serializable data class TimeConfig(val format: TimeFormat = TimeFormat.RACING)

private val timeConfigKey = stringPreferencesKey("time_config")

fun Context.streamTimeConfig(): Flow<TimeConfig> = streamConfig(timeConfigKey, TimeConfig())

suspend fun Context.saveTimeConfig(config: TimeConfig) = saveConfig(timeConfigKey, config)

// --- DataFieldDesignConfig ---
// Mirrors Karoo OS "Data Field Design" options the SDK does not expose to extensions
// (Show Icons, Label Size). See docs/superpowers/specs/2026-06-07-data-field-design-design.md.

@Serializable
enum class LabelSize(val label: String) {
    SMALL("Small"),
    LARGE("Large"),
}

@Serializable
data class DataFieldDesignConfig(
    val showIcons: Boolean = true,
    val labelSize: LabelSize = LabelSize.SMALL,
)

private val dataFieldDesignConfigKey = stringPreferencesKey("data_field_design_config")

fun Context.streamDataFieldDesignConfig(): Flow<DataFieldDesignConfig> =
    streamConfig(dataFieldDesignConfigKey, DataFieldDesignConfig())

suspend fun Context.saveDataFieldDesignConfig(config: DataFieldDesignConfig) =
    saveConfig(dataFieldDesignConfigKey, config)
