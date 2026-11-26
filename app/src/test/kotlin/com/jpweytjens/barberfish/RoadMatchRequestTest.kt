package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.extension.roadMatchElevation
import com.jpweytjens.barberfish.extension.roadMatchRequest
import com.jpweytjens.barberfish.extension.roadMatchShape
import com.jpweytjens.barberfish.extension.roadMatchingSupported
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadMatchRequestTest {
    private val points = listOf(LatLng(50.1, 4.2), LatLng(50.2, 4.3))

    // Pinned field by field: asking the engine for anything beyond the shape crashes the whole
    // process on the 32-bit Karoo.
    @Test
    fun the_request_asks_for_the_shape_and_nothing_else() {
        val request = Json.parseToJsonElement(roadMatchRequest(points)).jsonObject
        assertEquals(
            setOf("shape", "costing", "shape_match", "filters", "elevation_interval"),
            request.keys,
        )
        assertEquals(30, request["elevation_interval"]!!.jsonPrimitive.int)
        assertEquals(
            Json.parseToJsonElement("""{"action":"include","attributes":["shape"]}"""),
            request["filters"],
        )
        assertEquals("bicycle", request["costing"]!!.jsonPrimitive.content)
        assertEquals("map_snap", request["shape_match"]!!.jsonPrimitive.content)
        val shape = request["shape"]!!.jsonArray
        assertEquals(2, shape.size)
        assertEquals(Json.parseToJsonElement("""{"lat":50.1,"lon":4.2}"""), shape[0])
        assertEquals(Json.parseToJsonElement("""{"lat":50.2,"lon":4.3}"""), shape[1])
    }

    @Test
    fun the_answers_shape_is_read_at_precision_six() {
        val road = listOf(LatLng(50.123456, 4.654321), LatLng(50.123999, 4.655001))
        val shape = roadMatchShape("""{"shape":"${encodeGpsPolyline(road, precision = 6)}"}""")
        assertEquals(2, shape!!.size)
        shape.zip(road).forEach { (got, want) ->
            assertEquals(want.lat, got.lat, 1e-6)
            assertEquals(want.lng, got.lng, 1e-6)
        }
    }

    @Test
    fun an_answer_without_a_shape_is_null() {
        assertNull(roadMatchShape("""{"error":"x"}"""))
        assertNull(roadMatchShape("not json"))
        assertNull(roadMatchShape("""{"shape":""}"""))
        assertNull(roadMatchShape("""{"shape":7}"""))
        assertNull(roadMatchShape("[]"))
    }

    @Test
    fun the_answers_elevation_is_read_as_numbers() {
        val answer = """{"shape":"","elevation":[-400,-399.75]}"""
        assertEquals(listOf(-400.0, -399.75), roadMatchElevation(answer))
    }

    @Test
    fun an_answer_without_elevation_is_null() {
        assertNull(roadMatchElevation("""{"shape":"abc"}"""))
        assertNull(roadMatchElevation("""{"elevation":[1,"x"]}"""))
        assertNull(roadMatchElevation("""{"elevation":7}"""))
        assertNull(roadMatchElevation("not json"))
        assertNull(roadMatchElevation("[]"))
    }

    @Test
    fun only_arm_devices_are_supported() {
        assertTrue(roadMatchingSupported(arrayOf("arm64-v8a")))
        assertTrue(roadMatchingSupported(arrayOf("armeabi-v7a", "armeabi")))
        assertFalse(roadMatchingSupported(arrayOf("x86_64")))
        assertFalse(roadMatchingSupported(emptyArray()))
    }
}
