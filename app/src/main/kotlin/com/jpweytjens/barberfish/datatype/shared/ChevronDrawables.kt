package com.jpweytjens.barberfish.datatype.shared

import androidx.annotation.DrawableRes
import com.jpweytjens.barberfish.R
import com.jpweytjens.barberfish.extension.GradePalette

/**
 * The grade map chevron for [palette]: the palette's yellow climb band colour with a black outline,
 * so the glyph reads as the palette's own on every band. On the yellow band itself only the outline
 * shows, as the native chevron does on the native yellow line. HSLuv has no yellow and takes white.
 */
@DrawableRes
internal fun gradeChevronDrawable(palette: GradePalette): Int =
    when (palette) {
        GradePalette.BARBERFISH,
        GradePalette.KAROO -> R.drawable.ic_climber_chevron_f0d800
        GradePalette.SURGEONFISH -> R.drawable.ic_climber_chevron_e0cf10
        GradePalette.WAHOO -> R.drawable.ic_climber_chevron_feff00
        GradePalette.GARMIN -> R.drawable.ic_climber_chevron_f9ee44
        GradePalette.ZWIFT -> R.drawable.ic_climber_chevron_f2c510
        GradePalette.TURBO -> R.drawable.ic_climber_chevron_f1d749
        GradePalette.HSLUV -> R.drawable.ic_climber_chevron
    }
