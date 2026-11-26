package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.ROAD_HEIGHT_INTERVAL_M
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.encodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.karooHeightM
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.ValhallaRoadMatcher
import com.jpweytjens.barberfish.extension.hasRoadDataAccess
import com.jpweytjens.barberfish.extension.roadMatchRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** When the matcher starts the engine at all. The engine itself is checked on the devices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ValhallaRoadMatcherTest {
    @get:Rule val folder = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val line = listOf(LatLng(50.1, 4.2), LatLng(50.2, 4.3))
    private val road = listOf(LatLng(50.100001, 4.200001), LatLng(50.200001, 4.300001))
    private val answer = """{"shape":"${encodeGpsPolyline(road, precision = 6)}"}"""

    // What the engine was started with, in place of the engine.
    private val calls = mutableListOf<Pair<String, String>>()

    private fun matcher(
        tilesDir: String = folder.root.path,
        supported: Boolean = true,
        engine: (String, String) -> String = { tiles, request ->
            calls += tiles to request
            answer
        },
    ) = ValhallaRoadMatcher(app, tilesDir, supported, engine)

    private fun grantStorage() = shadowOf(app).grantPermissions(*ROAD_DATA_PERMISSIONS)

    @Test
    fun with_everything_in_place_the_engine_gets_the_shape_only_request() {
        grantStorage()
        val matched = matcher().match(line)
        assertEquals(listOf(folder.root.path to roadMatchRequest(line)), calls)
        assertEquals(2, matched!!.points.size)
        assertEquals(road[0].lat, matched.points[0].lat, 1e-6)
    }

    @Test
    fun an_answer_with_elevation_gives_heights_on_the_shapes_own_length() {
        grantStorage()
        // One sample every 30 m plus one at the road's end.
        val length = cumulativeDistancesM(road).last()
        val sampleCount = (length / ROAD_HEIGHT_INTERVAL_M).toInt() + 2
        val reported = List(sampleCount) { -400.0 }
        val elevated =
            """{"shape":"${encodeGpsPolyline(road, precision = 6)}","elevation":$reported}"""
        val heights = matcher(engine = { _, _ -> elevated }).match(line)!!.heights!!
        assertEquals(sampleCount, heights.size)
        assertEquals(0f, heights.first().first, 0f)
        assertEquals(length.toFloat(), heights.last().first, 1e-2f)
        heights.forEach { assertEquals(karooHeightM(-400.0).toFloat(), it.second, 1e-3f) }
    }

    @Test
    fun an_answer_without_elevation_gives_the_shape_and_no_heights() {
        grantStorage()
        val matched = matcher().match(line)!!
        assertEquals(2, matched.points.size)
        assertNull(matched.heights)
    }

    @Test
    fun without_storage_access_there_is_no_match_and_no_engine() {
        assertFalse(app.hasRoadDataAccess())
        assertNull(matcher().match(line))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun with_one_of_the_two_permissions_there_is_no_access() {
        shadowOf(app).grantPermissions(ROAD_DATA_PERMISSIONS.first())
        assertFalse(app.hasRoadDataAccess())
        grantStorage()
        assertTrue(app.hasRoadDataAccess())
    }

    // The manifest and the list the config screen requests are two copies of one fact. A
    // permission requested but not declared is refused without a dialog, for good.
    @Test
    fun the_permissions_requested_are_the_ones_the_manifest_declares() {
        val declared =
            app.packageManager
                .getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .filter { it.endsWith("_EXTERNAL_STORAGE") }
                .toSet()
        assertEquals(ROAD_DATA_PERMISSIONS.toSet(), declared)
    }

    @Test
    fun on_an_unsupported_device_the_engine_is_never_started() {
        grantStorage()
        assertNull(matcher(supported = false).match(line))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun without_the_road_data_folder_the_engine_is_never_started() {
        grantStorage()
        assertNull(matcher(tilesDir = File(folder.root, "missing").path).match(line))
        assertNull(matcher(tilesDir = folder.newFile("a-file").path).match(line))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun an_engine_that_fails_gives_no_match() {
        grantStorage()
        assertNull(matcher(engine = { _, _ -> error("engine failed") }).match(line))
        assertNull(
            matcher(engine = { _, _ -> throw UnsatisfiedLinkError("no library") }).match(line)
        )
        assertNull(matcher(engine = { _, _ -> """{"error":"no path"}""" }).match(line))
    }

    @Test
    fun a_line_of_fewer_than_two_points_is_not_matched() {
        grantStorage()
        assertNull(matcher().match(line.take(1)))
        assertTrue(calls.isEmpty())
    }
}
