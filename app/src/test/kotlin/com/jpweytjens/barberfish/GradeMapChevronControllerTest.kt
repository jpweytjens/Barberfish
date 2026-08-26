package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ClimbChevronSpec
import com.jpweytjens.barberfish.extension.GradeMapChevronController
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeMapChevronControllerTest {

    private class FakeEmitter : Emitter<MapEffect> {
        val events = mutableListOf<MapEffect>()

        override fun onNext(t: MapEffect) {
            events += t
        }

        override fun onError(t: Throwable) {}

        override fun onComplete() {}

        override fun setCancellable(cancellable: () -> Unit) {}

        override fun cancel() {}
    }

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()

    private fun spec(id: String, bearing: Float = 0f, color: Int = red, distanceM: Double = 0.0) =
        ClimbChevronSpec(
            id = id,
            lat = 50.0,
            lng = 4.0,
            bearingDeg = bearing,
            colorArgb = color,
            distanceM = distanceM,
        )

    private fun FakeEmitter.hiddenIds() =
        events.filterIsInstance<HideSymbols>().flatMap { it.symbolIds }

    private fun FakeEmitter.shownIds() =
        events.filterIsInstance<ShowSymbols>().flatMap { s -> s.symbols.map { it.id } }

    @Test
    fun first_emit_shows_all_and_hides_nothing() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        assertTrue(fake.hiddenIds().isEmpty())
        assertEquals(listOf("a", "b"), fake.shownIds())
    }

    @Test
    fun unchanged_second_emit_is_noop() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        fake.events.clear()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        assertTrue(fake.events.isEmpty())
    }

    @Test
    fun second_emit_hides_only_removed_and_shows_only_new() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        fake.events.clear()
        controller.emit(fake, listOf(spec("a"), spec("c")))
        assertEquals(listOf("b"), fake.hiddenIds())
        assertEquals(listOf("c"), fake.shownIds())
    }

    @Test
    fun changed_spec_is_hidden_then_reshown() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a", bearing = 10f)))
        fake.events.clear()
        controller.emit(fake, listOf(spec("a", bearing = 20f)))
        assertEquals(listOf("a"), fake.hiddenIds())
        assertEquals(listOf("a"), fake.shownIds())
        // Hide must precede show so a stale symbol never survives beside the update.
        assertTrue(fake.events[0] is HideSymbols)
        assertTrue(fake.events[1] is ShowSymbols)
    }

    @Test
    fun removed_ids_are_reissued_as_hides_on_following_emits() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        controller.emit(fake, listOf(spec("a")))
        // "b" was removed last emit; the lost-hide workaround re-issues it even
        // though this emit removes nothing new.
        fake.events.clear()
        controller.emit(fake, listOf(spec("a")))
        assertEquals(listOf("b"), fake.hiddenIds())
        assertTrue(fake.shownIds().isEmpty())
    }

    @Test
    fun reissued_hides_expire_after_three_emits() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        controller.emit(fake, listOf(spec("a")))
        repeat(3) { controller.emit(fake, listOf(spec("a"))) }
        fake.events.clear()
        controller.emit(fake, listOf(spec("a")))
        assertTrue(fake.events.isEmpty())
    }

    @Test
    fun reappearing_id_is_shown_and_not_rehidden() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        controller.emit(fake, listOf(spec("a")))
        fake.events.clear()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        assertFalse(fake.hiddenIds().contains("b"))
        assertEquals(listOf("b"), fake.shownIds())
    }

    @Test
    fun assumeStale_first_emit_hides_unclaimed_and_reshows_redrawn_in_place() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.assumeStale(3)
        controller.emit(fake, listOf(spec("barberfish-chev-1")))
        // Unclaimed stale ids are hidden; a redrawn stale id is re-shown WITHOUT a
        // preceding hide — a hide in the same batch races the show in the rideapp's
        // async symbol processing and blanks the chevron (observed on-device).
        assertEquals(
            setOf("barberfish-chev-0", "barberfish-chev-2"),
            fake.hiddenIds().toSet(),
        )
        assertEquals(listOf("barberfish-chev-1"), fake.shownIds())
        assertTrue(fake.events[0] is HideSymbols)
        assertTrue(fake.events[1] is ShowSymbols)
    }

    @Test
    fun assumeStale_zero_span_changes_nothing() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.assumeStale(0)
        controller.emit(fake, emptyList())
        assertTrue(fake.events.isEmpty())
    }

    @Test
    fun assumeStale_clearAll_hides_the_range() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.assumeStale(2)
        controller.clearAll(fake)
        assertEquals(setOf("barberfish-chev-0", "barberfish-chev-1"), fake.hiddenIds().toSet())
    }

    @Test
    fun assumeStale_hides_are_reissued_like_any_removed_set() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.assumeStale(2)
        controller.emit(fake, listOf(spec("barberfish-chev-0")))
        fake.events.clear()
        controller.emit(fake, listOf(spec("barberfish-chev-0")))
        // chev-1 was hidden on the first emit; the lost-hide workaround re-issues it.
        assertEquals(listOf("barberfish-chev-1"), fake.hiddenIds())
        assertTrue(fake.shownIds().isEmpty())
    }

    @Test
    fun clearAll_hides_previous_and_recently_removed_then_empty_emit_is_noop() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a"), spec("b")))
        controller.emit(fake, listOf(spec("a")))
        fake.events.clear()
        controller.clearAll(fake)
        assertEquals(setOf("a", "b"), fake.hiddenIds().toSet())

        fake.events.clear()
        controller.emit(fake, emptyList())
        assertTrue(fake.events.isEmpty())
    }

    @Test
    fun show_preserves_spec_fields() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a", bearing = 42f, color = green)))
        val icon =
            (fake.events.single() as ShowSymbols).symbols.single()
                as io.hammerhead.karooext.models.Symbol.Icon
        assertEquals("a", icon.id)
        assertEquals(50.0, icon.lat, 0.0)
        assertEquals(4.0, icon.lng, 0.0)
        assertEquals(42f, icon.orientation, 0f)
    }

    @Test
    fun hidePassed_hides_only_chevrons_behind_progress() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(
            fake,
            listOf(
                spec("a", distanceM = 30.0),
                spec("b", distanceM = 90.0),
                spec("c", distanceM = 150.0),
            ),
        )
        fake.events.clear()
        controller.hidePassed(fake, 100.0)
        assertEquals(setOf("a", "b"), fake.hiddenIds().toSet())
        assertTrue(fake.shownIds().isEmpty())
    }

    @Test
    fun hidePassed_with_nothing_behind_emits_nothing() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a", distanceM = 200.0)))
        fake.events.clear()
        controller.hidePassed(fake, 100.0)
        assertTrue(fake.events.isEmpty())
    }

    @Test
    fun hidden_passed_chevrons_are_not_reshown_by_a_filtered_emit() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(
            fake,
            listOf(
                spec("a", distanceM = 30.0),
                spec("b", distanceM = 90.0),
                spec("c", distanceM = 150.0),
            ),
        )
        controller.hidePassed(fake, 100.0)
        fake.events.clear()
        // The rebuild path filters passed specs before emitting (Task 4); the controller
        // must not re-show a and b, and may re-issue their hides (lost-hide robustness).
        controller.emit(fake, listOf(spec("c", distanceM = 150.0)))
        assertTrue(fake.shownIds().isEmpty())
    }

    @Test
    fun hidePassed_reissues_hides_through_the_next_emit() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.emit(fake, listOf(spec("a", distanceM = 30.0), spec("c", distanceM = 150.0)))
        controller.hidePassed(fake, 100.0)
        fake.events.clear()
        controller.emit(fake, listOf(spec("c", distanceM = 150.0)))
        assertTrue(fake.hiddenIds().contains("a"))
    }

    @Test
    fun hidePassed_leaves_stale_sentinels_alone() {
        val controller = GradeMapChevronController()
        val fake = FakeEmitter()
        controller.assumeStale(3)
        controller.hidePassed(fake, 1_000.0)
        assertTrue(fake.events.isEmpty())
    }
}
