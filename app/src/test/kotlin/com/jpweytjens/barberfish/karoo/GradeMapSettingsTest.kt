package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradePalette
import com.jpweytjens.barberfish.extension.ZoneConfig
import com.jpweytjens.barberfish.extension.saveGradeMapConfig
import com.jpweytjens.barberfish.extension.saveZoneConfig
import com.jpweytjens.barberfish.extension.streamGradeMapDrawnIdSpans
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.MapRecorder
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowPolyline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings edits drive the map overlay: they turn it off, recolour it, and bound stale ids. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GradeMapSettingsTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    @Before
    fun clean() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        host = karoo.host<BarberfishExtension>()
    }

    private fun drawnMap(): MapRecorder {
        karoo.system.setNavigation(climbRoute().navigating())
        val map = host.startMap()
        map.awaitDrawn(karoo)
        return map
    }

    private fun persistedSpans() = runBlocking { karoo.app.streamGradeMapDrawnIdSpans().first() }

    @Test
    fun turning_the_grade_map_off_removes_the_overlay() {
        val map = drawnMap()
        assertTrue(map.visiblePolylines().isNotEmpty())

        runBlocking { karoo.app.saveGradeMapConfig(GradeMapConfig(enabled = false)) }

        karoo.awaitValue {
            true.takeIf { map.visiblePolylines().isEmpty() && map.visibleSymbols().isEmpty() }
        }
    }

    @Test
    fun turning_it_back_on_redraws_the_route() {
        val map = drawnMap()
        runBlocking { karoo.app.saveGradeMapConfig(GradeMapConfig(enabled = false)) }
        karoo.awaitValue { true.takeIf { map.visiblePolylines().isEmpty() } }

        runBlocking { karoo.app.saveGradeMapConfig(GradeMapConfig(enabled = true)) }
        karoo.awaitValue { map.visibleFills().takeIf { it.isNotEmpty() } }
    }

    @Test
    fun switching_the_grade_palette_recolours_the_bands() {
        val map = drawnMap()
        val before = map.visibleFills().getValue("barberfish-seg-0").color

        runBlocking {
            karoo.app.saveZoneConfig(ZoneConfig(gradePalette = GradePalette.BARBERFISH))
        }

        karoo.awaitValue {
            true.takeIf { map.visibleFills()["barberfish-seg-0"]?.color != before }
        }
    }

    @Test
    fun unsyncing_the_overlay_from_the_sparkline_recolours_the_bands() {
        val map = drawnMap()
        val mark = map.mark()

        // With sync off and every band skipped, the overlay colours ground the sparkline-led
        // default leaves plain, so the fills have to be re-shown.
        runBlocking {
            karoo.app.saveGradeMapConfig(GradeMapConfig(syncWithSparkline = false, skipBands = 0))
        }

        map.await(20_000, mark) { it is ShowPolyline }
    }

    @Test
    fun drawing_a_route_persists_the_id_spans_it_minted() {
        drawnMap()
        val spans = karoo.awaitValue { persistedSpans().takeIf { it.chevrons > 0 } }
        assertTrue(spans.segments > 0)
    }

    @Test
    fun clearing_navigation_zeroes_the_persisted_spans() {
        drawnMap()
        karoo.awaitValue { persistedSpans().takeIf { it.chevrons > 0 } }

        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)

        karoo.awaitValue {
            persistedSpans().takeIf {
                it.segments == 0 && it.chevrons == 0 && it.rejoinChevrons == 0
            }
        }
    }

    @Test
    fun a_process_restart_hides_the_previous_generations_chevrons() {
        drawnMap()
        karoo.awaitValue { persistedSpans().takeIf { it.chevrons > 0 } }

        // A shorter, flatter route than the one the dead generation painted, so the ids it left
        // behind are not all claimed by the new draw.
        karoo.system.setNavigation(flatRoute().navigating())
        val restarted = karoo.restart<BarberfishExtension>()
        val map = restarted.startMap()
        map.awaitDrawn(karoo)
        map.awaitQuiet(quietMs = 800, timeoutMs = 25_000)

        assertTrue(
            "the fresh generation must hide stale chevron ids before drawing",
            map.items.any { it is HideSymbols },
        )
    }
}
