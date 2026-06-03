package com.jpweytjens.barberfish.datatype.shared

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/** Web Mercator y for a latitude in degrees (unitless, same scale as longitude in radians). */
private fun mercatorY(latDeg: Double): Double {
    val latRad = latDeg * PI / 180.0
    return ln(tan(PI / 4.0 + latRad / 2.0))
}

/**
 * Projects [lat]/[lng] to a (u, v) pair in [0, 1] within [bounds] using Web Mercator.
 * u runs west->east; v runs north->south so that screen-top is north. Map tiles are
 * Mercator, so projecting the route the same way keeps geometry registered to the tile.
 */
internal fun projectToUnit(bounds: LatLngBounds, lat: Double, lng: Double): Pair<Double, Double> {
    val u = (lng - bounds.minLng) / (bounds.maxLng - bounds.minLng)
    val yNorth = mercatorY(bounds.maxLat)
    val ySouth = mercatorY(bounds.minLat)
    val v = (yNorth - mercatorY(lat)) / (yNorth - ySouth)
    return u to v
}

/** Web Mercator width/height aspect of [bounds], used to size the preview box. */
internal fun mercatorBoundsAspect(bounds: LatLngBounds): Double {
    val xSpan = (bounds.maxLng - bounds.minLng) * PI / 180.0
    val ySpan = mercatorY(bounds.maxLat) - mercatorY(bounds.minLat)
    return xSpan / ySpan
}
