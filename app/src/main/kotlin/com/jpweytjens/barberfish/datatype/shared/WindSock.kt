package com.jpweytjens.barberfish.datatype.shared

import com.jpweytjens.barberfish.extension.ZoneColorMode
import io.hammerhead.karooext.models.Symbol
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The one map symbol id. A ShowSymbols for an existing id updates it in place. */
const val WIND_SOCK_ID = "barberfish-wind-sock"

/** How far the puck's tip sits ahead of its centre, and the gap a sock keeps from it. */
private const val PUCK_TIP_DP = 18f
private const val SOCK_CLEARANCE_DP = 3f

/**
 * Mast distance ahead of the puck centre: past the puck's tip by the longest sock and a gap, so a
 * sock blowing straight back never touches the puck.
 */
const val WIND_SOCK_MAST_DP =
    PUCK_TIP_DP + WindSockGeometry.MAX_BANDS * WindSockGeometry.BAND_LENGTH_DP + SOCK_CLEARANCE_DP

/** One sock band: 3 knots, in m/s. */
private const val BAND_MS = 3 * 1852.0 / 3600.0

/** Standing bands for [speedMs]: one per 3 knots, five at most, calm below half a band. */
fun windSockBands(speedMs: Double): Int =
    (speedMs / BAND_MS).roundToInt().coerceIn(0, WindSockGeometry.MAX_BANDS)

/**
 * Where the wind blows relative to [courseDeg], clockwise in [0, 360): 0 straight from behind, 180
 * straight ahead. The same convention as the headwind extension's own headwind stream, so the field
 * agrees with that extension's fields on the same course.
 */
fun relativeWindDeg(windFromDeg: Double, courseDeg: Double): Double =
    ((windFromDeg + 180.0 - courseDeg) % 360.0 + 360.0) % 360.0

/** The along-course part of [windSpeed] at [relativeWindDeg]: positive into the wind. */
fun headwindComponent(windSpeed: Double, relativeWindDeg: Double): Double =
    -cos(relativeWindDeg * PI / 180.0) * windSpeed

/** Grade-field convention: bare into the wind, minus with it, no decimals. */
fun formatHeadwind(speed: Double): String = speed.roundToInt().toString()

/** Headwind at which the colour saturates: 20 km/h, in m/s. */
private const val WIND_COLOR_FULL_SCALE_MS = 20.0 / 3.6

/** Threshold scale with the target at zero: red rising into a headwind, green with a tailwind. */
fun windFieldColor(headwindMs: Double, colorMode: ZoneColorMode): FieldColor {
    if (colorMode == ZoneColorMode.NONE) return FieldColor.Default
    val factor = (-headwindMs / WIND_COLOR_FULL_SCALE_MS).coerceIn(-1.0, 1.0).toFloat()
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
 * oriented to where the wind blows (the meteorological "from" direction plus 180), grey when
 * [muted] (a stale forecast). Null when calm, so the caller hides the symbol. The map rotates
 * symbols with itself, so the absolute bearing reads relative on a heading-up map and true on a
 * north-up one.
 */
@Suppress("LongParameterList")
internal fun windSockSymbol(
    fix: LatLng,
    courseDeg: Double,
    zoom: Double,
    density: Float,
    windFromDeg: Double,
    bands: Int,
    muted: Boolean = false,
): Symbol.Icon? {
    if (bands <= 0) return null
    val mastM = WIND_SOCK_MAST_DP * density * metresPerPixel(zoom)
    val mast = destinationLatLng(fix, courseDeg, mastM)
    val blowsTo = ((windFromDeg + 180.0) % 360.0 + 360.0) % 360.0
    return Symbol.Icon(
        id = WIND_SOCK_ID,
        lat = mast.lat,
        lng = mast.lng,
        iconRes = windSockDrawable(bands, muted),
        orientation = blowsTo.toFloat(),
    )
}
