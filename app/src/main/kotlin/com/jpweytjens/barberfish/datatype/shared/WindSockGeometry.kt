package com.jpweytjens.barberfish.datatype.shared

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
