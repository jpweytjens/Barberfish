package com.jpweytjens.barberfish.extension

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.ROAD_HEIGHT_INTERVAL_M
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.roadHeights
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber

/** Where the Karoo keeps its offline road data: `offline/nav` in shared storage. */
internal fun roadTilesDir(): String =
    File(Environment.getExternalStorageDirectory(), "offline/nav").path

// The engine's answer encodes its shape at six decimals, one more than a route polyline.
private const val ROAD_SHAPE_PRECISION = 6

// The processor types the engine is packaged for (see abiFilters in the build file).
private val ROAD_MATCHING_ABIS = setOf("arm64-v8a", "armeabi-v7a")

/** The request for the road under [points]: the shape, and a height every 30 m. */
internal fun roadMatchRequest(points: List<LatLng>): String = buildJsonObject {
    putJsonArray("shape") {
        points.forEach {
            addJsonObject {
                put("lat", it.lat)
                put("lon", it.lng)
            }
        }
    }
    put("costing", "bicycle")
    put("shape_match", "map_snap")
    // Ask for the shape alone. Building the per-road attributes trips over fields in
    // the Karoo's road data, and on the 32-bit Karoo that takes the whole process down.
    putJsonObject("filters") {
        put("action", "include")
        putJsonArray("attributes") { add("shape") }
    }
    put("elevation_interval", ROAD_HEIGHT_INTERVAL_M)
}
    .toString()

private fun parseAnswer(response: String): JsonObject? =
    try {
        Json.parseToJsonElement(response) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

private fun JsonObject.shapePoints(): List<LatLng>? =
    (get("shape") as? JsonPrimitive)
        ?.takeIf { it.isString && it.content.isNotEmpty() }
        ?.let { decodeGpsPolyline(it.content, ROAD_SHAPE_PRECISION) }

private fun JsonObject.elevationValues(): List<Double>? =
    (get("elevation") as? JsonArray)?.map {
        (it as? JsonPrimitive)?.takeIf { number -> !number.isString }?.doubleOrNull ?: return null
    }

/** The points in the engine's answer, or null when it carries no shape. */
internal fun roadMatchShape(response: String): List<LatLng>? = parseAnswer(response)?.shapePoints()

/** The heights in the engine's answer, or null when it has none or they are not all numbers. */
internal fun roadMatchElevation(response: String): List<Double>? =
    parseAnswer(response)?.elevationValues()

/** True when this device can load the engine: it runs one of the packaged ARM types. */
internal fun roadMatchingSupported(abis: Array<String> = Build.SUPPORTED_ABIS): Boolean = abis.any {
    it in ROAD_MATCHING_ABIS
}

/** The storage permissions the manifest declares; the config screen requests exactly these. */
internal val ROAD_DATA_PERMISSIONS: Array<String> =
    arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

/** True when every one of [ROAD_DATA_PERMISSIONS] is granted. */
internal fun Context.hasRoadDataAccess(): Boolean = ROAD_DATA_PERMISSIONS.all {
    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

// One engine per call, closed before it returns: an engine keeps every road tile it has read,
// and a fresh one per piece gives that memory back in between.
private fun Context.runValhalla(tilesDir: String, request: String): String =
    Valhalla(this, ValhallaConfigFactory.usingTilesDir(tilesDir)).use {
        it.traceAttributesRaw(request)
    }

/**
 * Matches a line to the Karoo's own offline road data, on the device. Gives no match, without
 * starting the engine, when the device cannot run it, storage access is missing or the road data
 * folder is not there. [engine] takes the folder and the request and returns the engine's answer.
 */
internal class ValhallaRoadMatcher(
    context: Context,
    private val tilesDir: String = roadTilesDir(),
    private val supported: Boolean = roadMatchingSupported(),
    engine: ((tilesDir: String, request: String) -> String)? = null,
) : RoadMatcher {
    private val app = context.applicationContext
    private val engine = engine ?: { tiles, request -> app.runValhalla(tiles, request) }

    private fun usable(): Boolean {
        val tiles = File(tilesDir)
        return supported && app.hasRoadDataAccess() && tiles.isDirectory && tiles.canRead()
    }

    // Suppressed: the engine is native code behind a third-party wrapper. Whatever it throws,
    // and a library that fails to load, must leave the band on its chords, not end the service.
    @Suppress("TooGenericExceptionCaught")
    override fun match(points: List<LatLng>): MatchedRoad? {
        if (points.size < 2 || !usable()) return null
        return try {
            val answer = parseAnswer(engine(tilesDir, roadMatchRequest(points)))
            answer?.shapePoints()?.let { shape ->
                MatchedRoad(
                    shape,
                    answer.elevationValues()?.let {
                        roadHeights(it, cumulativeDistancesM(shape).last())
                    },
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "road match failed")
            null
        } catch (e: LinkageError) {
            Timber.w(e, "road matching engine did not load")
            null
        }
    }
}
