package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.ETAField
import com.jpweytjens.barberfish.datatype.ETAKind
import com.jpweytjens.barberfish.extension.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ETAPreviewTest {
    @Test
    fun arrival_preview_counts_from_the_preview_clock_not_the_device() {
        // 8:00 plus 27'45", 1h23'45" and 10h23'45".
        assertEquals(
            listOf("8:27", "9:23", "18:23"),
            ETAField.previewStates(ETAKind.TIME_OF_ARRIVAL, TimeFormat.entries.first()).map {
                it.primary
            },
        )
    }
}
