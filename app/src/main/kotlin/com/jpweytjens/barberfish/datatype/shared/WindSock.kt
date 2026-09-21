package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Symbol
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The windsock glyph's fixed geometry, in dp. The drawables in res/drawable/ic_wind_sock_N.xml are
 * generated from the same numbers by scripts/gen_wind_sock_drawables.py; WindSockDrawablesTest pins
 * the two together. Every sock is a complete sock with the same mouth and tip; only the length and
 * the band count change.
 */
object WindSockGeometry {
    const val MAX_BANDS = 5
    const val BAND_LENGTH_DP = 6.4f
    const val MOUTH_HALF_WIDTH_DP = 5.3f
    const val TIP_HALF_WIDTH_DP = 1.3f
    const val STROKE_DP = 2f

    /** The drawable is a square this wide with the mouth at its centre, sock extending upward. */
    const val ICON_SIZE_DP = 64f

    fun lengthDp(bands: Int): Float = bands.coerceIn(0, MAX_BANDS) * BAND_LENGTH_DP
}

/** The headwind extension's id and the four streams Barberfish reads from it. */
const val HEADWIND_EXTENSION = "karoo-headwind"

/** The headwind extension's Android package, for the config screen's installed check. */
const val HEADWIND_PACKAGE = "de.timklge.karooheadwind"
val WIND_DIRECTION_STREAM: String = DataType.dataTypeId(HEADWIND_EXTENSION, "windDirection")
val HEADWIND_ANGLE_STREAM: String = DataType.dataTypeId(HEADWIND_EXTENSION, "headwind")
val WIND_SPEED_STREAM: String = DataType.dataTypeId(HEADWIND_EXTENSION, "windSpeed")
val HEADWIND_SPEED_STREAM: String = DataType.dataTypeId(HEADWIND_EXTENSION, "headwindSpeed")

/** The one map symbol id. A ShowSymbols for an existing id updates it in place. */
const val WIND_SOCK_ID = "barberfish-wind-sock"

/** Mast distance ahead of the puck centre: puck tip 18 dp, longest sock 32 dp, 3 dp clearance. */
const val WIND_SOCK_MAST_DP = 53f

/**
 * The unit the headwind extension sends speed in. It defaults to the Karoo profile's family and
 * cannot be read across extensions, so Barberfish assumes the default. One band per 3 knots.
 */
enum class WindUnit(val perBand: Double) {
    KPH(5.56),
    MPH(3.45),
}

fun windUnitFor(profile: UserProfile): WindUnit =
    if (profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL) WindUnit.MPH
    else WindUnit.KPH

/** Standing bands for [speed] in [unit]: one per 3 knots, five at most, calm below half a band. */
fun windSockBands(speed: Double, unit: WindUnit): Int =
    (speed / unit.perBand).roundToInt().coerceIn(0, WindSockGeometry.MAX_BANDS)

/** Grade-field convention: bare into the wind, minus with it, no decimals. */
fun formatHeadwind(speed: Double): String = speed.roundToInt().toString()

/** Headwind speed at which the colour saturates: 20 km/h, the same wind in mph. */
private const val WIND_COLOR_FULL_SCALE_KPH = 20.0

/** Threshold scale with the target at zero: red rising into a headwind, green with a tailwind. */
fun windFieldColor(
    headwindSpeed: Double,
    unit: WindUnit,
    colorMode: ZoneColorMode,
): FieldColor {
    if (colorMode == ZoneColorMode.NONE) return FieldColor.Default
    val fullScale = WIND_COLOR_FULL_SCALE_KPH * unit.perBand / WindUnit.KPH.perBand
    val factor = (-headwindSpeed / fullScale).coerceIn(-1.0, 1.0).toFloat()
    return FieldColor.Threshold(factor)
}

private const val METRES_PER_DEGREE_LAT = 111_320.0

/**
 * Point [distanceM] from [from] along [bearingDeg]; flat-earth, exact enough for a few hundred
 * metres.
 */
internal fun destinationLatLng(from: LatLng, bearingDeg: Double, distanceM: Double): LatLng {
    val rad = bearingDeg * PI / 180.0
    val dLat = distanceM * cos(rad) / METRES_PER_DEGREE_LAT
    val dLng = distanceM * sin(rad) / (METRES_PER_DEGREE_LAT * cos(from.lat * PI / 180.0))
    return LatLng(from.lat + dLat, from.lng + dLng)
}

/**
 * The map sock for one fix: on the mast [WIND_SOCK_MAST_DP] ahead of [fix] along [courseDeg],
 * oriented to where the wind blows (the meteorological "from" direction plus 180). Null when calm,
 * so the caller hides the symbol. The map rotates symbols with itself, so the absolute bearing
 * reads relative on a heading-up map and true on a north-up one.
 */
@Suppress("LongParameterList")
internal fun windSockSymbol(
    fix: LatLng,
    courseDeg: Double,
    zoom: Double,
    density: Float,
    windFromDeg: Double,
    bands: Int,
): Symbol.Icon? {
    if (bands <= 0) return null
    val mastM = WIND_SOCK_MAST_DP * density * metresPerPixel(zoom)
    val mast = destinationLatLng(fix, courseDeg, mastM)
    val blowsTo = ((windFromDeg + 180.0) % 360.0 + 360.0) % 360.0
    return Symbol.Icon(
        id = WIND_SOCK_ID,
        lat = mast.lat,
        lng = mast.lng,
        iconRes = windSockDrawable(bands),
        orientation = blowsTo.toFloat(),
    )
}
