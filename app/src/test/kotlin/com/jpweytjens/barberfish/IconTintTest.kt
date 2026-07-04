package com.jpweytjens.barberfish

import androidx.compose.ui.graphics.Color
import com.jpweytjens.barberfish.datatype.shared.FieldColor
import com.jpweytjens.barberfish.datatype.shared.ICON_TINT_TEAL
import com.jpweytjens.barberfish.datatype.shared.ICON_TINT_TEAL_DAY
import com.jpweytjens.barberfish.datatype.shared.toColorConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the connected icon tint: teal per theme when the field's data is live, theme foreground
 * when it isn't (ride-clock fields before the ride starts) or when the field shows a placeholder.
 */
class IconTintTest {

    @Test
    fun liveIcon_usesThemeTeal() {
        val night = FieldColor.Default.toColorConfig(ZoneColorMode.NONE, isNightMode = true)
        val day = FieldColor.Default.toColorConfig(ZoneColorMode.NONE, isNightMode = false)
        assertEquals(ICON_TINT_TEAL, night.iconTint)
        assertEquals(ICON_TINT_TEAL_DAY, day.iconTint)
    }

    @Test
    fun notLiveIcon_dropsToForeground() {
        val night =
            FieldColor.Default.toColorConfig(ZoneColorMode.NONE, isNightMode = true, liveIcon = false)
        val day =
            FieldColor.Default.toColorConfig(ZoneColorMode.NONE, isNightMode = false, liveIcon = false)
        assertEquals(Color.White, night.iconTint)
        assertEquals(Color.Black, day.iconTint)
    }

    @Test
    fun streamStatePlaceholder_usesForegroundRegardless() {
        val night = FieldColor.StreamState.toColorConfig(ZoneColorMode.NONE, isNightMode = true)
        assertEquals(Color.White, night.iconTint)
    }
}
