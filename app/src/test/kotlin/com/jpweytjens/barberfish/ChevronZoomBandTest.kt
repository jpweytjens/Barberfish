package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.extension.ChevronZoomBand
import org.junit.Assert.assertEquals
import org.junit.Test

class ChevronZoomBandTest {

    @Test
    fun first_real_value_replaces_the_seed_even_inside_the_same_band() {
        val band = ChevronZoomBand()
        assertEquals(15.0, band.effectiveZoom(15.0), 0.0)
        assertEquals(15.4, band.effectiveZoom(15.4), 0.0)
    }

    @Test
    fun holds_the_frozen_value_while_the_band_holds() {
        val band = ChevronZoomBand()
        band.effectiveZoom(15.0)
        band.effectiveZoom(15.4)
        assertEquals(15.4, band.effectiveZoom(15.1), 0.0)
        assertEquals(15.4, band.effectiveZoom(15.9), 0.0)
        assertEquals(15.4, band.effectiveZoom(15.0), 0.0)
    }

    @Test
    fun refreezes_on_a_band_crossing_in_either_direction() {
        val band = ChevronZoomBand()
        band.effectiveZoom(15.0)
        band.effectiveZoom(15.4)
        assertEquals(14.9, band.effectiveZoom(14.9), 0.0)
        assertEquals(14.9, band.effectiveZoom(14.2), 0.0)
        assertEquals(16.3, band.effectiveZoom(16.3), 0.0)
    }
}
