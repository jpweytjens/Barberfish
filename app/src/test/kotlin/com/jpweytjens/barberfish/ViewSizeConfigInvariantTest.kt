package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the invariant that `valueBitmapHeightDp` for every named ViewSizeConfig is
 * large enough to hold the cap height of digits rendered at `valueFontSizeBase`.
 *
 * `renderValueBitmap` (BitmapValue.kt) draws text with the baseline near the bottom
 * of a bitmap sized `valueBitmapHeightDp * density`. The visible cap height of
 * digits at font size `valueFontSizeBase * density` is ~`0.74 * valueFontSizeBase *
 * density` (see `valueBitmapHeightPx`). If `valueBitmapHeightDp < 0.74 *
 * valueFontSizeBase`, the top of the digit caps is clipped before reaching the
 * RemoteViews layout.
 */
class ViewSizeConfigInvariantTest {

    private fun assertFits(name: String, cfg: ViewSizeConfig) {
        val required = (0.74f * cfg.valueFontSizeBase).toInt()
        assertTrue(
            "$name: valueBitmapHeightDp=${cfg.valueBitmapHeightDp} must be >= $required " +
                "(0.74 * valueFontSizeBase=${cfg.valueFontSizeBase}) to fit the digit cap height",
            cfg.valueBitmapHeightDp >= required,
        )
    }

    @Test fun standard_bitmap_fits_value_font() {
        assertFits("STANDARD", ViewSizeConfig.STANDARD)
    }

    @Test fun hud_three_bitmap_fits_value_font() {
        assertFits("HUD_THREE", ViewSizeConfig.HUD_THREE)
    }

    @Test fun hud_four_bitmap_fits_value_font() {
        assertFits("HUD_FOUR", ViewSizeConfig.HUD_FOUR)
    }

    @Test fun preview_hud_three_bitmap_fits_value_font() {
        assertFits("PREVIEW_HUD_THREE", ViewSizeConfig.PREVIEW_HUD_THREE)
    }

    @Test fun preview_hud_four_bitmap_fits_value_font() {
        assertFits("PREVIEW_HUD_FOUR", ViewSizeConfig.PREVIEW_HUD_FOUR)
    }
}
