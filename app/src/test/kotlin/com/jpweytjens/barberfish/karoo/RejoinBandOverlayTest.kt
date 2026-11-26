package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.RouteFixtures
import com.jpweytjens.barberfish.RouteFixtures.M_PER_DEG
import com.jpweytjens.barberfish.datatype.shared.LatLng
import com.jpweytjens.barberfish.datatype.shared.MatchedRoad
import com.jpweytjens.barberfish.datatype.shared.RerouteRed
import com.jpweytjens.barberfish.datatype.shared.RoadMatcher
import com.jpweytjens.barberfish.datatype.shared.cumulativeDistancesM
import com.jpweytjens.barberfish.datatype.shared.decodeGpsPolyline
import com.jpweytjens.barberfish.datatype.shared.gradeChevronDrawable
import com.jpweytjens.barberfish.datatype.shared.heightsOnLine
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradeMapWidth
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RejoinRoad
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.rejoinRoad
import com.jpweytjens.barberfish.extension.roadFollowStatus
import com.jpweytjens.barberfish.extension.saveGradeMapConfig
import com.jpweytjens.barberfish.extension.saveKarooFilesConfig
import com.jpweytjens.barberfish.extension.streamGradeMapDrawnIdSpans
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.MapRecorder
import fi.nikosavola.karooext.testing.decodePolyline
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.Symbol
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The rejoin band on the map. Rejoin lines run due south onto the climb route; their road lies 3 m
 * east of them. A test either lets a fake matcher match the line with the Karoo files switch on, or
 * publishes the matched line itself with the switch off, where nothing in the service writes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RejoinBandOverlayTest {
    @get:Rule val karoo = FakeKarooRule()

    private val realFactory = BarberfishExtension.roadMatcherFactory

    // The road is the line 3 m east. Heights only for a line running north to south, the rejoin
    // line: flat for its first half, then an 8% climb landing at 100 m, the route's height there.
    // The route itself, running east, gets its shape alone.
    private var withHeights = true
    private val rejoinAsked = AtomicBoolean(false)
    private val east3 = RoadMatcher { points ->
        val road = points.map { it.copy(lng = it.lng + 3.0 / M_PER_DEG) }
        val southward = points.first().lat > points.last().lat
        if (southward) rejoinAsked.set(true)
        val lengthM = cumulativeDistancesM(points).last()
        MatchedRoad(road, climbToTheRoute(lengthM).takeIf { southward && withHeights })
    }

    private lateinit var host: FakeKarooHost

    @Before
    fun setUp() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        rejoinRoad.value = null
        roadFollowStatus.value = RoadFollowStatus.IDLE
        BarberfishExtension.roadMatcherFactory = { east3 }
        shadowOf(karoo.app).grantPermissions(*ROAD_DATA_PERMISSIONS)
        host = karoo.startBarberfish()
    }

    @After
    fun tearDown() {
        BarberfishExtension.roadMatcherFactory = realFactory
        rejoinRoad.value = null
        roadFollowStatus.value = RoadFollowStatus.IDLE
    }

    private fun save(config: GradeMapConfig) = runBlocking { karoo.app.saveGradeMapConfig(config) }

    private fun save(config: KarooFilesConfig) = runBlocking {
        karoo.app.saveKarooFilesConfig(config)
    }

    private fun awaitTrue(condition: () -> Boolean) = karoo.awaitValue {
        true.takeIf { condition() }
    }

    // --- fixtures ---

    // Flat for the first half of [lengthM], then 8% up to 100 m.
    private fun climbToTheRoute(lengthM: Double): List<Pair<Float, Float>> {
        val halfM = lengthM / 2
        val foot = (100.0 - 0.08 * halfM).toFloat()
        return listOf(0f to foot, halfM.toFloat() to foot, lengthM.toFloat() to 100f)
    }

    // Due south from [lengthM] north of the route onto it, [xM] east of its start.
    private fun rejoinPoints(xM: Double, lengthM: Double): List<LatLng> {
        val steps = (lengthM / 100.0).toInt()
        val drawn = (steps downTo 0).map { RouteFixtures.point(xM, it * 100.0) }
        // Decoded as the follower decodes it, so the matched line belongs to the encoded one.
        return decodeGpsPolyline(RouteFixtures.encoded(drawn))
    }

    private fun encoded(points: List<LatLng>) = RouteFixtures.encoded(points)

    // The line matched as the follower would publish it, its road 3 m east with [heights].
    private fun matched(points: List<LatLng>, heights: List<Pair<Float, Float>>): RejoinRoad {
        val cumDist = cumulativeDistancesM(points)
        val road = MatchedRoad(points.map { it.copy(lng = it.lng + 3.0 / M_PER_DEG) }, heights)
        val onLine = heightsOnLine(points, cumDist, road) ?: error("the fixture road has heights")
        return RejoinRoad(encoded(points), onLine.line, onLine.heights, cumDist.last())
    }

    // Alternately flat and 8% every 200 m: many colour pieces.
    private fun steps(lengthM: Double): List<Pair<Float, Float>> {
        var height = 100f
        return (0..(lengthM / 200.0).toInt()).map { i ->
            if (i > 0 && i % 2 == 0) height += 16f
            (i * 200.0).toFloat() to height
        }
    }

    private val longLine = rejoinPoints(500.0, 2_000.0)
    private val shortLine = rejoinPoints(500.0, 600.0)

    private fun offRoute(line: List<LatLng>) =
        climbRoute().navigating(encoded(line), rejoinDistance = cumulativeDistancesM(line).last())

    // --- reading the map ---

    private fun isRejoinPiece(id: String) = id.startsWith("barberfish-rejoin-seg-")

    private fun MapRecorder.rejoinPieces() = visiblePolylines().filterKeys(::isRejoinPiece)

    private fun MapRecorder.rejoinChevrons() =
        visibleSymbols().filterKeys { it.startsWith("barberfish-rejoin-chev-") }

    private fun MapRecorder.awaitGraded() {
        karoo.awaitValue { rejoinPieces().keys.firstOrNull() }
        awaitQuiet(quietMs = 1_500, timeoutMs = 15_000)
    }

    private fun MapRecorder.awaitSolid() {
        karoo.awaitValue { visiblePolylines()["barberfish-rejoin"] }
        awaitQuiet(quietMs = 1_500, timeoutMs = 15_000)
    }

    // Every rejoin layer on the map as drawn: id to geometry, colour and width.
    private fun MapRecorder.rejoinLayers(): Map<String, Any> =
        visiblePolylines()
            .filterKeys { it.startsWith("barberfish-rejoin") }
            .mapValues { (_, it) -> Triple(it.encodedPolyline, it.color, it.width) } +
            visibleSymbols().filterKeys { it.startsWith("barberfish-rejoin") }

    private fun metresEastOf(encoded: String) =
        decodePolyline(encoded).map { metresEast(it.second) }

    private fun metresNorthOf(encoded: String) =
        decodePolyline(encoded).map { it.first * M_PER_DEG }

    // On the road 3 m east of x = 500, within the polyline grid's 1.1 m.
    private fun onRejoinRoad(eastM: Double) = eastM in 502.4..503.6

    private fun Set<String>.rejoinPieces() = filter(::isRejoinPiece).toSet()

    // --- the band ---

    @Test
    fun with_heights_the_rejoin_band_is_graded_on_the_road_with_a_red_casing() {
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(offRoute(rejoinPoints(500.0, 1_000.0)))
        val map = host.startMap()
        map.awaitGraded()

        val pieces = map.rejoinPieces().values
        assertTrue(pieces.map { it.color }.distinct().size > 1)
        assertTrue(pieces.all { it.width == GradeMapWidth.MEDIUM.fillDp })
        assertTrue(pieces.flatMap { metresEastOf(it.encodedPolyline) }.all(::onRejoinRoad))
        assertFalse(map.visiblePolylines().containsKey("barberfish-rejoin"))

        val casing = map.visiblePolylines().getValue("barberfish-rejoin-casing")
        assertEquals(RerouteRed.toArgb(), casing.color)
        assertEquals(GradeMapWidth.MEDIUM.rejoinCasingDp, casing.width)
        assertTrue(metresEastOf(casing.encodedPolyline).all(::onRejoinRoad))
        // Beneath every piece.
        val order = map.visiblePolylines().keys.toList()
        map.rejoinPieces().keys.forEach {
            assertTrue(order.indexOf("barberfish-rejoin-casing") < order.indexOf(it))
        }

        val chevrons = map.rejoinChevrons().values.filterIsInstance<Symbol.Icon>()
        assertTrue(chevrons.isNotEmpty())
        assertTrue(chevrons.all { onRejoinRoad(metresEast(it.lng)) })
        val gradeIcon = gradeChevronDrawable(ZoneConfig().gradePalette)
        assertTrue(chevrons.all { it.iconRes == gradeIcon })
    }

    @Test
    fun at_narrow_the_graded_rejoin_band_keeps_a_casing() {
        save(GradeMapConfig(width = GradeMapWidth.NARROW))
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(offRoute(rejoinPoints(500.0, 1_000.0)))
        val map = host.startMap()
        map.awaitGraded()

        assertTrue(map.rejoinPieces().values.all { it.width == GradeMapWidth.NARROW.fillDp })
        val casing = map.visiblePolylines().getValue("barberfish-rejoin-casing")
        assertEquals(10, casing.width)
        assertEquals(RerouteRed.toArgb(), casing.color)
        assertTrue(map.rejoinChevrons().isEmpty())
    }

    @Test
    fun without_heights_the_band_is_todays() {
        withHeights = false
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(offRoute(rejoinPoints(500.0, 1_000.0)))
        val map = host.startMap()
        awaitTrue { rejoinAsked.get() }
        map.awaitSolid()
        val withoutHeights = map.rejoinLayers()
        assertTrue(map.rejoinPieces().isEmpty())

        save(KarooFilesConfig(false))
        Thread.sleep(500)
        map.awaitQuiet(quietMs = 1_500, timeoutMs = 15_000)

        assertEquals(map.rejoinLayers(), withoutHeights)
    }

    @Test
    fun a_result_for_another_line_is_not_drawn() {
        val lineA = rejoinPoints(500.0, 1_000.0)
        rejoinRoad.value = matched(lineA, climbToTheRoute(1_000.0))
        karoo.system.setNavigation(offRoute(rejoinPoints(1_500.0, 1_000.0)))
        val map = host.startMap()
        map.awaitSolid()

        assertTrue(map.rejoinPieces().isEmpty())
        val solid = map.visiblePolylines().getValue("barberfish-rejoin")
        assertEquals(RerouteRed.toArgb(), solid.color)
        assertTrue(metresEastOf(solid.encodedPolyline).all { it in 1_499.4..1_500.6 })

        // The same result is drawn on its own line.
        karoo.system.setNavigation(offRoute(lineA))
        map.awaitGraded()
    }

    @Test
    fun the_rejoin_band_and_the_route_band_share_no_id() {
        save(KarooFilesConfig(true))
        karoo.system.setNavigation(offRoute(rejoinPoints(500.0, 1_000.0)))
        val map = host.startMap()
        map.awaitGraded()
        awaitTrue { roadFollowStatus.value == RoadFollowStatus.FOLLOWING }
        map.awaitQuiet(quietMs = 1_500, timeoutMs = 15_000)

        val route = Regex("barberfish-(seg-\\d+(-casing)?|chev-\\d+|casing)")
        val rejoin = Regex("barberfish-rejoin(-seg-\\d+|-chev-\\d+|-casing)?")
        val ids = map.visiblePolylines().keys + map.visibleSymbols().keys
        ids.forEach { assertTrue(it, route.matches(it) || rejoin.matches(it)) }
        // A shared id would put one band's geometry under the other's name.
        map.visiblePolylines()
            .filterKeys { route.matches(it) }
            .values
            .forEach { assertTrue(metresNorthOf(it.encodedPolyline).all { n -> n in -0.6..0.6 }) }
        map.rejoinPieces().values.forEach {
            assertTrue(metresEastOf(it.encodedPolyline).all(::onRejoinRoad))
        }
        map.visibleChevrons().forEach {
            if (route.matches(it.id)) assertEquals(0.0, it.lat * M_PER_DEG, 0.6)
            else assertTrue(onRejoinRoad(metresEast(it.lng)))
        }
        assertTrue(map.visibleChevrons().any { route.matches(it.id) })
        assertTrue(map.rejoinChevrons().isNotEmpty())
    }

    @Test
    fun heights_arriving_redraw_the_band_without_a_zoom_or_a_fix() {
        karoo.system.setNavigation(offRoute(longLine))
        val map = host.startMap()
        map.awaitSolid()

        rejoinRoad.value = matched(longLine, steps(2_000.0))

        awaitTrue {
            map.rejoinPieces().isNotEmpty() && "barberfish-rejoin" !in map.visiblePolylines()
        }
    }

    // --- across a restart ---

    // A first session draws the long line graded and dies, leaving its pieces and stored spans.
    private fun aSessionThatDied(): MapRecorder {
        rejoinRoad.value = matched(longLine, steps(2_000.0))
        karoo.system.setNavigation(offRoute(longLine))
        val first = host.startMap()
        first.awaitGraded()
        assertTrue(first.rejoinPieces().size > 4)
        assertTrue(first.rejoinChevrons().isNotEmpty())
        host.stopMap(first)
        return first
    }

    @Test
    fun a_restart_into_a_shorter_rejoin_line_hides_the_surplus_pieces() {
        val first = aSessionThatDied()

        rejoinRoad.value = matched(shortLine, climbToTheRoute(600.0))
        karoo.system.setNavigation(offRoute(shortLine))
        val second = host.startMap()
        second.awaitGraded()

        val (polylines, _) = paintedAfter(first, second)
        assertTrue(second.rejoinPieces().size < first.rejoinPieces().size)
        assertEquals(second.rejoinPieces().keys, polylines.rejoinPieces())
    }

    @Test
    fun a_restart_into_the_solid_band_hides_every_graded_piece() {
        val first = aSessionThatDied()

        rejoinRoad.value = null
        val second = host.startMap()
        second.awaitSolid()

        val (polylines, _) = paintedAfter(first, second)
        assertTrue(polylines.rejoinPieces().isEmpty())
        assertTrue("barberfish-rejoin" in polylines)
    }

    @Test
    fun a_restart_with_no_rejoin_line_hides_fills_casing_and_chevrons() {
        val first = aSessionThatDied()

        rejoinRoad.value = null
        karoo.system.setNavigation(climbRoute().navigating())
        val second = host.startMap()
        second.awaitDrawn(karoo)
        second.awaitQuiet(quietMs = 1_500, timeoutMs = 15_000)

        val (polylines, symbols) = paintedAfter(first, second)
        assertTrue(polylines.none { it.startsWith("barberfish-rejoin") })
        assertTrue(symbols.none { it.startsWith("barberfish-rejoin") })
        assertTrue(second.visibleFills().isNotEmpty())
    }

    @Test
    fun the_stored_span_is_never_below_the_painted_count() {
        // Every effect is held at the emitter while the stored spans are read.
        val painted = linkedSetOf<String>()
        val shortfalls = CopyOnWriteArrayList<String>()
        val gate =
            object : Emitter<MapEffect> {
                override fun onNext(t: MapEffect) {
                    // The highest painted piece's index bounds the span that must cover it.
                    val count =
                        synchronized(painted) {
                            when (t) {
                                is ShowPolyline -> painted += t.id
                                is HidePolyline -> painted -= t.id
                                else -> {}
                            }
                            painted.filter(::isRejoinPiece).maxOfOrNull {
                                it.substringAfterLast('-').toInt() + 1
                            } ?: 0
                        }
                    val stored = runBlocking {
                        karoo.app.streamGradeMapDrawnIdSpans().first().rejoinSegments
                    }
                    if (stored < count) shortfalls += "$t: stored $stored, painted $count"
                }

                override fun onError(t: Throwable) {}

                override fun onComplete() {}

                override fun setCancellable(cancellable: () -> Unit) {
                    stop = cancellable
                }

                override fun cancel() {}

                var stop: () -> Unit = {}
            }
        // The service the host started stays idle: this one draws into the gate.
        val service = Robolectric.buildService(BarberfishExtension::class.java).create()
        awaitTrue {
            karoo.system.consumerParams.count { it is ActiveRideProfile.Params } >= 2
        }
        fun paintedPieces() = synchronized(painted) { painted.count(::isRejoinPiece) }
        fun paintedSolid() = synchronized(painted) { "barberfish-rejoin" in painted }
        fun quiet() = Thread.sleep(2_500)

        karoo.system.setNavigation(offRoute(longLine))
        service.get().startMap(gate)
        awaitTrue { paintedSolid() }
        quiet()
        // Solid to many pieces: the span is raised before the first piece is shown.
        rejoinRoad.value = matched(longLine, steps(2_000.0))
        awaitTrue { paintedPieces() > 4 }
        quiet()
        val many = paintedPieces()
        // Many to few: the span stays up until the surplus is hidden.
        rejoinRoad.value = matched(shortLine, climbToTheRoute(600.0))
        karoo.system.setNavigation(offRoute(shortLine))
        awaitTrue { paintedPieces() in 1 until many }
        quiet()
        // Few to the solid band.
        rejoinRoad.value = null
        awaitTrue { paintedPieces() == 0 && paintedSolid() }
        quiet()
        gate.stop()
        service.destroy()

        assertEquals(emptyList<String>(), shortfalls)
        assertEquals(
            0,
            runBlocking { karoo.app.streamGradeMapDrawnIdSpans().first() }.rejoinSegments,
        )
    }
}
