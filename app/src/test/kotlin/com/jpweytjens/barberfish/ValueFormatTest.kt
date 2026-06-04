package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.formatFixed
import org.junit.Assert.assertEquals
import org.junit.Test

class ValueFormatTest {
    @Test fun one_decimal() = assertEquals("47.2", formatFixed(47.234, 1))
    @Test fun zero_decimals_rounds() = assertEquals("540", formatFixed(539.6, 0))
    @Test fun zero_decimals_truncates_toward_round() = assertEquals("12", formatFixed(12.4, 0))
    @Test fun negative_clamped_to_zero() = assertEquals("0.0", formatFixed(-3.0, 1))
    @Test fun exact_zero() = assertEquals("0", formatFixed(0.0, 0))
}
