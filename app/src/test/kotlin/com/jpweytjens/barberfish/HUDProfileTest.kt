package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDOverride
import com.jpweytjens.barberfish.extension.HUDScope
import com.jpweytjens.barberfish.extension.HUDSlots
import com.jpweytjens.barberfish.extension.ProfileHUDs
import com.jpweytjens.barberfish.extension.SparklineMode
import com.jpweytjens.barberfish.extension.applyHUDEdit
import com.jpweytjens.barberfish.extension.hudScopeFor
import com.jpweytjens.barberfish.extension.initialHUDSelection
import com.jpweytjens.barberfish.extension.mergeDiscovered
import com.jpweytjens.barberfish.extension.profileMenuIds
import com.jpweytjens.barberfish.extension.resolveHUDConfig
import com.jpweytjens.barberfish.extension.setCustom
import com.jpweytjens.barberfish.extension.sharedProfileCount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HUDProfileTest {

    private val shared = HUDConfig()
    private val mine = HUDConfig(slots = HUDSlots(columns = 4))

    // HUDSlots defaults to 3 columns, so edits set 4 to be distinguishable from a lost edit.
    private val fourColumns: (HUDConfig) -> HUDConfig = {
        it.copy(slots = it.slots.copy(columns = 4))
    }

    @Test
    fun `null id resolves to Shared`() =
        assertEquals(HUDScope.Shared, hudScopeFor(null, mapOf("a" to HUDOverride(true, mine))))

    @Test
    fun `unknown id resolves to Shared`() =
        assertEquals(HUDScope.Shared, hudScopeFor("b", mapOf("a" to HUDOverride(true, mine))))

    @Test
    fun `custom id resolves to its profile`() =
        assertEquals(HUDScope.Profile("a"), hudScopeFor("a", mapOf("a" to HUDOverride(true, mine))))

    @Test
    fun `profile switched back to Shared resolves to Shared`() =
        assertEquals(HUDScope.Shared, hudScopeFor("a", mapOf("a" to HUDOverride(false, mine))))

    @Test
    fun `resolve gives custom settings`() =
        assertEquals(mine, resolveHUDConfig("a", mapOf("a" to HUDOverride(true, mine)), shared))

    @Test
    fun `resolve ignores kept settings while on Shared`() =
        assertEquals(
            shared,
            resolveHUDConfig("a", mapOf("a" to HUDOverride(false, mine)), shared),
        )

    @Test
    fun `resolve with null id gives Shared`() =
        assertEquals(shared, resolveHUDConfig(null, emptyMap(), shared))

    @Test
    fun `switching to Custom copies Shared when new`() =
        assertEquals(HUDOverride(true, shared), setCustom(emptyMap(), "a", true, shared)["a"])

    @Test
    fun `switching back to Custom restores kept settings`() =
        assertEquals(
            HUDOverride(true, mine),
            setCustom(mapOf("a" to HUDOverride(false, mine)), "a", true, shared)["a"],
        )

    @Test
    fun `switching to Shared keeps settings`() =
        assertEquals(
            HUDOverride(false, mine),
            setCustom(mapOf("a" to HUDOverride(true, mine)), "a", false, shared)["a"],
        )

    @Test
    fun `setCustom leaves other profiles alone`() {
        val others = mapOf("b" to HUDOverride(true, mine))
        assertEquals(others["b"], setCustom(others, "a", true, shared)["b"])
    }

    @Test
    fun `mergeDiscovered adds new id at the end`() =
        assertEquals(
            listOf("a", "b"),
            mergeDiscovered(mapOf("a" to "Road+"), "b", "Gravel+")?.keys?.toList(),
        )

    @Test
    fun `mergeDiscovered renames in place`() {
        val merged = mergeDiscovered(mapOf("a" to "Road+", "b" to "Gravel+"), "a", "Road")
        assertEquals(mapOf("a" to "Road", "b" to "Gravel+"), merged)
        assertEquals(listOf("a", "b"), merged?.keys?.toList())
    }

    @Test
    fun `mergeDiscovered returns null when unchanged`() =
        assertNull(mergeDiscovered(mapOf("a" to "Road+"), "a", "Road+"))

    @Test
    fun `shared count excludes Custom profiles only`() =
        assertEquals(
            2,
            sharedProfileCount(
                mapOf("a" to "A", "b" to "B", "c" to "C"),
                mapOf("a" to HUDOverride(true, mine), "b" to HUDOverride(false, mine)),
            ),
        )

    @Test
    fun `menu lists discovered then orphaned overrides`() =
        assertEquals(
            listOf("a", "b", "x"),
            profileMenuIds(
                mapOf("a" to "A", "b" to "B"),
                mapOf("x" to HUDOverride(true, mine), "a" to HUDOverride(true, mine)),
            ),
        )

    @Test
    fun `column edit then strip edit keeps both`() {
        val once = applyHUDEdit(ProfileHUDs(shared, emptyMap()), HUDScope.Shared, fourColumns)
        val twice =
            applyHUDEdit(once, HUDScope.Shared) {
                it.copy(strip = it.strip.copy(mode = SparklineMode.CLIMBS))
            }
        assertEquals(4, twice.shared.slots.columns)
        assertEquals(SparklineMode.CLIMBS, twice.shared.strip.mode)
    }

    @Test
    fun `Shared edit leaves overrides untouched`() {
        val overrides = mapOf("a" to HUDOverride(true, mine))
        assertEquals(
            overrides,
            applyHUDEdit(ProfileHUDs(shared, overrides), HUDScope.Shared, fourColumns).overrides,
        )
    }

    @Test
    fun `profile edit keeps its custom flag`() {
        val huds = ProfileHUDs(shared, mapOf("a" to HUDOverride(false, shared)))
        val edited = applyHUDEdit(huds, HUDScope.Profile("a"), fourColumns).overrides.getValue("a")
        assertEquals(HUDOverride(false, mine), edited)
    }

    @Test
    fun `profile edit without entry creates one instead of dropping it`() {
        val edited =
            applyHUDEdit(ProfileHUDs(shared, emptyMap()), HUDScope.Profile("a"), fourColumns)
        assertEquals(HUDOverride(true, mine), edited.overrides["a"])
        assertEquals(shared, edited.shared)
    }

    @Test
    fun `card opens on a Custom current profile`() =
        assertEquals("a", initialHUDSelection("a", mapOf("a" to HUDOverride(true, mine))))

    @Test
    fun `card opens on Shared when the current profile is on Shared`() {
        assertNull(initialHUDSelection("a", mapOf("a" to HUDOverride(false, mine))))
        assertNull(initialHUDSelection(null, emptyMap()))
    }

    @Test
    fun `a stored HUD with the old embedded strip still decodes`() {
        val json = Json { ignoreUnknownKeys = true }
        val stored = """{"columns":4,"sparkline":{"mode":"CLIMBS","lookaheadKm":10}}"""
        assertEquals(4, json.decodeFromString<HUDSlots>(stored).columns)
    }

    @Test
    fun `override map round-trips through json`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val map = mapOf("a" to HUDOverride(false, mine))
        assertEquals(map, json.decodeFromString<Map<String, HUDOverride>>(json.encodeToString(map)))
    }
}
