package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.HUDConfig
import com.jpweytjens.barberfish.extension.HUDOverride
import com.jpweytjens.barberfish.screens.profileTag
import com.jpweytjens.barberfish.screens.sharedTag
import org.junit.Assert.assertEquals
import org.junit.Test

class HUDProfileTagTest {

    @Test
    fun `custom and current combine`() =
        assertEquals(
            "custom · current",
            profileTag("a", mapOf("a" to HUDOverride(true, HUDConfig())), "a"),
        )

    @Test
    fun `custom profile that is not current`() =
        assertEquals("custom", profileTag("a", mapOf("a" to HUDOverride(true, HUDConfig())), "b"))

    @Test
    fun `profile on Shared that is current`() =
        assertEquals(
            "current",
            profileTag("a", mapOf("a" to HUDOverride(false, HUDConfig())), "a"),
        )

    @Test fun `profile on Shared is bare`() = assertEquals("", profileTag("a", emptyMap(), "b"))

    @Test
    fun `shared tag singular and plural`() {
        assertEquals("1 profile", sharedTag(1))
        assertEquals("0 profiles", sharedTag(0))
        assertEquals("4 profiles", sharedTag(4))
    }
}
