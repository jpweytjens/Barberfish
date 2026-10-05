package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.screens.PendingHUDWrites
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingHUDWritesTest {

    @Test fun `snapshots are shown when no write is pending`() = assertTrue(PendingHUDWrites().idle)

    @Test
    fun `a snapshot arriving between two quick edits is held back`() {
        val pending = PendingHUDWrites()
        pending.begin() // edit A
        pending.begin() // edit B, before A's snapshot arrives
        pending.end() // A committed; its snapshot lacks B and must not replace the screen
        assertFalse(pending.idle)
        pending.end()
        assertTrue(pending.idle)
    }
}
