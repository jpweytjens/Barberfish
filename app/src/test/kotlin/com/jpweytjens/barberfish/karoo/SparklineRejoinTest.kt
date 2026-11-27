package com.jpweytjens.barberfish.karoo

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.datatype.shared.KAROO_DESTINATION_PURPLE
import com.jpweytjens.barberfish.datatype.shared.KAROO_REJOIN_RED
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.MatchedRoadLine
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeElevationPolyline
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.rejoinView
import com.jpweytjens.barberfish.datatype.shared.renderElevationSparkline
import com.jpweytjens.barberfish.datatype.shared.visvalingamWhyatt
import com.jpweytjens.barberfish.datatype.sparklineImageSize
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDScope
import com.jpweytjens.barberfish.extension.HUDSlotConfig
import com.jpweytjens.barberfish.extension.HUDSlotField
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RejoinRoad
import com.jpweytjens.barberfish.extension.SparklineConfig
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.rejoinRoad
import com.jpweytjens.barberfish.extension.saveGradeMapConfig
import com.jpweytjens.barberfish.extension.saveKarooFilesConfig
import com.jpweytjens.barberfish.extension.updateHUDConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.ViewRecorder
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.Symbol
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

/**
 * The Profile off the route: the rejoin line's terrain when its heights are in, today's frozen view
 * otherwise. A test either publishes the matched line itself with the Karoo files switch off, where
 * nothing in the service writes it, or lets a fake matcher match the line with the switch on. The
 * rider sits 2 km into the 4 km climb route throughout, and strips are compared pixel for pixel
 * with what the renderer draws for the expected view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.hammerhead.karooext.internal"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SparklineRejoinTest {
    @get:Rule val karoo = FakeKarooRule()
    private lateinit var host: FakeKarooHost

    private val realFactory = BarberfishExtension.roadMatcherFactory

    // The road under a line is the line itself. Heights only for a line running north to south,
    // the rejoin line: flat for its first half, then 8% up to 180 m, the route's height where it
    // lands. The route itself gets its shape alone.
    private val rejoinAsked = AtomicBoolean(false)
    private val echo = RoadMatcher { points ->
        val southward = points.first().lat > points.last().lat
        if (southward) rejoinAsked.set(true)
        val lengthM = cumulativeDistancesM(points).last().toFloat()
        val heights = listOf(0f to 144f, lengthM / 2 to 144f, lengthM to 180f)
        MatchedRoad(points, heights.takeIf { southward })
    }

    @Before
    fun startOnTheRoute() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        rejoinRoad.value = null
        BarberfishExtension.roadMatcherFactory = { echo }
        shadowOf(karoo.app).grantPermissions(*ROAD_DATA_PERMISSIONS)
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setDistanceRemaining(REMAINING_M)
        RobolectricPump.advanceBy(1.seconds)
        host = karoo.host<BarberfishExtension>()
    }

    @After
    fun tearDown() {
        BarberfishExtension.roadMatcherFactory = realFactory
        rejoinRoad.value = null
    }

    // --- fixtures ---

    // Due south from [lengthM] north of the route onto it, at the rider's distance along it.
    private fun rejoinLine(lengthM: Double): String =
        RouteFixtures.encoded(
            ((lengthM / 100.0).toInt() downTo 0).map { RouteFixtures.point(2_000.0, it * 100.0) }
        )

    // The line as the follower would publish it, with [heights] as fractions of its length.
    private fun road(encoded: String, vararg heights: Pair<Double, Float>): RejoinRoad {
        val points = decodeGpsPolyline(encoded)
        val cumDist = cumulativeDistancesM(points)
        val lengthM = cumDist.last()
        return RejoinRoad(
            encoded,
            MatchedRoadLine(points, cumDist),
            heights.map { (fraction, height) -> (fraction * lengthM).toFloat() to height },
            lengthM,
        )
    }

    // Flat for half its length, then 8% to the route.
    private val lineM = 900.0
    private val line = rejoinLine(lineM)
    private val gradedRoad = road(line, 0.0 to 64f, 0.5 to 64f, 1.0 to 100f)

    // A 400 m climb, and a 20 m rise that stays below the configured minimum height range.
    private val steepLine = rejoinLine(1_500.0)
    private val steepRoad = road(steepLine, 0.0 to 100f, 1.0 to 500f)
    private val gentleLine = rejoinLine(1_200.0)
    private val gentleRoad = road(gentleLine, 0.0 to 100f, 1.0 to 120f)

    private fun offRoute(encoded: String, rejoinDistance: Double) {
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setNavigation(climbRoute().navigating(encoded, rejoinDistance))
        karoo.system.setDistanceRemaining(REMAINING_M, onRoute = false)
    }

    private fun backOnRoute() {
        RobolectricPump.advanceBy(1.seconds)
        rejoinRoad.value = null
        karoo.system.setNavigation(climbRoute().navigating())
        karoo.system.setDistanceRemaining(REMAINING_M)
    }

    private fun publish(road: RejoinRoad?) {
        RobolectricPump.advanceBy(1.seconds)
        rejoinRoad.value = road
    }

    // --- the views ---

    private fun openField(): ViewRecorder =
        host.startView("elevation-sparkline", TALL_FIELD_CONFIG).also { it.awaitFrame(15_000) }

    private fun ViewRecorder.fieldStrip(): Bitmap =
        frames.last().inflate(karoo.app, TALL_FIELD_CONFIG).sparklineValue()

    private fun ViewRecorder.awaitFieldStrip(expected: Bitmap): Bitmap = karoo.awaitValue {
        fieldStrip().takeIf { it.sameAs(expected) }
    }

    private fun saveHud(strip: SparklineConfig = SparklineConfig()) = runBlocking {
        karoo.app.updateHUDConfig(HUDScope.Shared) {
            HUDConfig(
                slots =
                    HUDSlots(
                        columns = 3,
                        leftSlot = HUDSlotConfig(field = HUDSlotField.Distance),
                        middleSlot = HUDSlotConfig(field = HUDSlotField.Distance),
                        rightSlot = HUDSlotConfig(field = HUDSlotField.Distance),
                        fourthSlot = HUDSlotConfig(field = HUDSlotField.Distance),
                    ),
                strip = strip,
            )
        }
    }

    // Every slot on distance, so one data point lights the whole HUD.
    private fun openHud(strip: SparklineConfig = SparklineConfig()): ViewRecorder {
        saveHud(strip)
        karoo.system.setDataPoint(
            DataPoint(DataType.Type.DISTANCE, mapOf(DataType.Field.DISTANCE to 1_500.0))
        )
        return host.startView("three-column", TALL_FIELD_CONFIG).also { it.awaitFrame(20_000) }
    }

    private fun ViewRecorder.hud(): View = frames.last().inflate(karoo.app, TALL_FIELD_CONFIG)

    private fun View.hudStrip(): Bitmap? =
        findViewById<View>(R.id.hud_sparkline_container)
            .takeIf { it.visibility == View.VISIBLE }
            ?.let { findViewById<ImageView>(R.id.hud_elevation_sparkline) }
            ?.takeIf { it.visibility == View.VISIBLE }
            ?.let { (it.drawable as? BitmapDrawable)?.bitmap }

    // --- what the renderer draws, with every setting at its default ---

    private val config = SparklineConfig()
    private val palette = ZoneConfig().gradePalette

    private fun fieldSize() = sparklineImageSize(TALL_FIELD_CONFIG, karoo.app)

    private fun hudSize() =
        karoo.app.resources.displayMetrics.widthPixels to
            karoo.app.resources.getDimensionPixelSize(R.dimen.hud_sparkline_height)

    private fun render(
        points: List<Pair<Float, Float>>,
        positionM: Float,
        size: Pair<Int, Int>,
        windowOverride: Pair<Float, Float>? = null,
        dotColor: Int = KAROO_REJOIN_RED.toArgb(),
    ): Bitmap {
        val (climbEdge, descentEdge) = config.gradeEdges(palette)
        val night =
            (karoo.app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return renderElevationSparkline(
                elevationPoints = visvalingamWhyatt(points, config.simplification.minAreaM2),
                positionM = positionM,
                widthPx = size.first,
                heightPx = size.second,
                density = karoo.app.resources.displayMetrics.density,
                palette = palette,
                readable = false,
                lookaheadM = config.lookaheadKm * 1000f,
                climbEdge = climbEdge,
                descentEdge = descentEdge,
                dotColor = dotColor,
                isNightMode = night,
                minElevRangeM = config.yZoom.minRangeM,
                logWarpK = config.warp.k,
                positionFraction = config.warp.positionFraction,
                windowOverride = windowOverride,
                clampWindowOverride = false,
            )
            .bitmap!!
    }

    /** The rejoin view of [road] with [rejoinDistance] still to go, fresh height range. */
    private fun rejoinStrip(
        road: RejoinRoad,
        rejoinDistance: Double,
        size: Pair<Int, Int> = fieldSize(),
    ): Bitmap {
        val view =
            rejoinView(
                road.lengthM.toFloat(),
                rejoinDistance,
                config.lookaheadKm * 1000f,
                size.first,
            )
        return render(road.heights, view.positionM, size, view.windowOverride)
    }

    private val routeHeights =
        decodeElevationPolyline(climbRoute().navigating().routeElevationPolyline!!)

    /** Today's frozen view: the route at the last position on it, the dot in rejoin red. */
    private fun frozenStrip(): Bitmap =
        render(routeHeights, (4_000.0 - REMAINING_M).toFloat(), fieldSize())

    private fun Bitmap.has(color: Int): Boolean =
        (0 until width).any { x -> (0 until height).any { y -> getPixel(x, y) == color } }

    // --- tests ---

    @Test
    fun off_the_route_with_heights_the_profile_is_the_rejoin_lines() {
        val field = openField()
        val hud = openHud()
        publish(gradedRoad)

        offRoute(line, rejoinDistance = lineM)

        val strip = field.awaitFieldStrip(rejoinStrip(gradedRoad, lineM))
        assertFalse(strip.sameAs(frozenStrip()))
        val hudExpected = rejoinStrip(gradedRoad, lineM, hudSize())
        karoo.awaitValue { hud.hud().hudStrip()?.takeIf { it.sameAs(hudExpected) } }
    }

    @Test
    fun the_dot_is_the_rejoin_red_and_moves_with_rejoin_distance() {
        val field = openField()
        publish(gradedRoad)

        offRoute(line, rejoinDistance = lineM)
        val atStart = field.awaitFieldStrip(rejoinStrip(gradedRoad, lineM))
        assertTrue(atStart.has(KAROO_REJOIN_RED.toArgb()))

        offRoute(line, rejoinDistance = 300.0)
        val nearLanding = field.awaitFieldStrip(rejoinStrip(gradedRoad, 300.0))
        assertFalse(nearLanding.sameAs(atStart))
    }

    @Test
    fun off_the_route_without_heights_the_view_is_todays_frozen_one() {
        val field = openField()

        offRoute(line, rejoinDistance = lineM)

        field.awaitFieldStrip(frozenStrip())
    }

    @Test
    fun a_result_for_another_line_leaves_the_frozen_view() {
        val field = openField()
        publish(steepRoad)

        offRoute(line, rejoinDistance = lineM)
        field.awaitFieldStrip(frozenStrip())

        // The same view takes the line's own result once it is in.
        publish(gradedRoad)
        field.awaitFieldStrip(rejoinStrip(gradedRoad, lineM))
    }

    @Test
    fun back_on_the_route_the_route_profile_returns_with_its_state() {
        val field = openField()
        val before = karoo.awaitValue { field.fieldStrip() }

        // A 400 m climb fills the rejoin view's height range.
        publish(steepRoad)
        offRoute(steepLine, rejoinDistance = 1_500.0)
        field.awaitFieldStrip(rejoinStrip(steepRoad, 1_500.0))

        // Its heights withdrawn mid-detour: the frozen view, at the last position on the route
        // and in the route's own height range.
        publish(null)
        field.awaitFieldStrip(frozenStrip())

        publish(steepRoad)
        field.awaitFieldStrip(rejoinStrip(steepRoad, 1_500.0))
        backOnRoute()
        field.awaitFieldStrip(before)
    }

    @Test
    fun each_rejoin_line_starts_its_height_range_fresh() {
        val field = openField()

        // Drawn in the configured minimum range, as on a fresh line.
        publish(gentleRoad)
        offRoute(gentleLine, rejoinDistance = 1_200.0)
        val gentle = field.awaitFieldStrip(rejoinStrip(gentleRoad, 1_200.0))

        publish(steepRoad)
        offRoute(steepLine, rejoinDistance = 1_500.0)
        field.awaitFieldStrip(rejoinStrip(steepRoad, 1_500.0))

        // A range carried over from the climb would flatten the rise.
        publish(gentleRoad)
        offRoute(gentleLine, rejoinDistance = 1_200.0)
        field.awaitFieldStrip(gentle)
    }

    @Test
    fun in_climbs_mode_a_rejoin_profile_does_not_reveal_the_strip() {
        val hud = openHud(SparklineConfig(mode = SparklineMode.CLIMBS))
        publish(gradedRoad)
        offRoute(line, rejoinDistance = lineM)
        // The route has no climbs, so the rider is between climbs. Two render ticks let the
        // detour reach the HUD.
        Thread.sleep(2_500)
        assertEquals(
            View.GONE,
            hud.hud().findViewById<View>(R.id.hud_sparkline_container).visibility,
        )

        // The same HUD in On mode draws the rejoin line, so it was there to be drawn.
        RobolectricPump.advanceBy(1.seconds)
        saveHud(SparklineConfig(mode = SparklineMode.ON))
        val expected = rejoinStrip(gradedRoad, lineM, hudSize())
        karoo.awaitValue { hud.hud().hudStrip()?.takeIf { it.sameAs(expected) } }
    }

    @Test
    fun on_a_pin_route_there_is_no_rejoin_view() {
        val field = openField()
        RobolectricPump.advanceBy(1.seconds)
        karoo.system.setNavigation(
            OnNavigationState.NavigationState.NavigatingToDestination(
                destination = Symbol.POI(id = "pin", lat = 0.0, lng = 0.0),
                polyline = climbRoute().navigating().routePolyline,
                elevationPolyline = climbRoute().navigating().routeElevationPolyline,
                climbs = emptyList(),
            )
        )
        karoo.system.setDistanceRemaining(REMAINING_M, onRoute = false)
        val pinStrip =
            render(
                routeHeights,
                (4_000.0 - REMAINING_M).toFloat(),
                fieldSize(),
                dotColor = KAROO_DESTINATION_PURPLE.toArgb(),
            )
        val pin = field.awaitFieldStrip(pinStrip)
        val framesBefore = field.frames.size

        publish(gradedRoad)

        karoo.awaitValue { true.takeIf { field.frames.size > framesBefore } }
        assertTrue(field.fieldStrip().sameAs(pin))
    }

    // --- the Karoo files switch, end to end ---

    private fun save(config: GradeMapConfig) = runBlocking { karoo.app.saveGradeMapConfig(config) }

    private fun save(config: KarooFilesConfig) = runBlocking {
        karoo.app.saveKarooFilesConfig(config)
    }

    // The rejoin view of the line as the service matched it, once it has.
    private fun matchedStrip(rejoinDistance: Double): Bitmap =
        rejoinStrip(karoo.awaitValue { rejoinRoad.value }, rejoinDistance)

    // Asserts the strip off the route is the frozen view and that it never showed anything else.
    private fun ViewRecorder.assertOnlyEverFrozen(onRoute: Bitmap, framesBefore: Int) {
        awaitFieldStrip(frozenStrip())
        val frozen = frozenStrip()
        frames.drop(framesBefore).forEach {
            val strip = it.inflate(karoo.app, TALL_FIELD_CONFIG).sparklineValue()
            assertTrue(strip.sameAs(frozen) || strip.sameAs(onRoute))
        }
    }

    @Test
    fun with_the_grade_map_off_and_the_switch_on_the_profile_is_the_rejoin_lines() {
        save(GradeMapConfig(enabled = false))
        save(KarooFilesConfig(true))
        val field = openField()

        offRoute(line, rejoinDistance = lineM)

        val strip = field.awaitFieldStrip(matchedStrip(lineM))
        assertTrue(rejoinAsked.get())
        assertFalse(strip.sameAs(frozenStrip()))
    }

    @Test
    fun turning_the_switch_off_returns_the_profile_to_the_frozen_view() {
        save(GradeMapConfig(enabled = false))
        save(KarooFilesConfig(true))
        val field = openField()
        offRoute(line, rejoinDistance = lineM)
        field.awaitFieldStrip(matchedStrip(lineM))

        RobolectricPump.advanceBy(1.seconds)
        save(KarooFilesConfig(false))

        field.awaitFieldStrip(frozenStrip())
    }

    @Test
    fun without_storage_access_the_profile_is_frozen() {
        shadowOf(karoo.app).denyPermissions(*ROAD_DATA_PERMISSIONS)
        save(GradeMapConfig(enabled = false))
        save(KarooFilesConfig(true))
        val field = openField()
        val onRoute = karoo.awaitValue { field.fieldStrip() }
        val framesBefore = field.frames.size

        offRoute(line, rejoinDistance = lineM)

        field.assertOnlyEverFrozen(onRoute, framesBefore)
        assertFalse(rejoinAsked.get())
    }

    @Test
    fun on_an_unsupported_device_the_profile_is_frozen() {
        ShadowBuild.setSupportedAbis(arrayOf("x86_64"))
        save(GradeMapConfig(enabled = false))
        save(KarooFilesConfig(true))
        val field = openField()
        val onRoute = karoo.awaitValue { field.fieldStrip() }
        val framesBefore = field.frames.size

        offRoute(line, rejoinDistance = lineM)

        field.assertOnlyEverFrozen(onRoute, framesBefore)
        assertFalse(rejoinAsked.get())
    }

    private companion object {
        const val REMAINING_M = 2_000.0
    }
}
