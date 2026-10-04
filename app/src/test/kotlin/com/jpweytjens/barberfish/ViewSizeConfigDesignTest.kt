package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.ViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.toViewSizeConfig
import com.jpweytjens.barberfish.datatype.shared.withDesign
import com.jpweytjens.barberfish.extension.DataFieldDesignConfig
import com.jpweytjens.barberfish.extension.LabelSize
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewSizeConfigDesignTest {

    private fun viewConfig(colSpan: Int, rowSpan: Int, textSize: Int = 41) =
        ViewConfig(
            gridSize = colSpan to rowSpan,
            viewSize = 238 to 126,
            textSize = textSize,
            alignment = ViewConfig.Alignment.RIGHT,
            boundariesEnabled = true,
            preview = false,
        )

    @Test
    fun `2-col 4-row header is 17_6sp at both settings`() {
        val small = DataFieldDesignConfig(labelSize = LabelSize.SMALL)
        val large = DataFieldDesignConfig(labelSize = LabelSize.LARGE)
        assertEquals(
            17.6f,
            viewConfig(30, 15).toViewSizeConfig(design = small).headerFontSize.value,
        )
        assertEquals(
            17.6f,
            viewConfig(30, 15).toViewSizeConfig(design = large).headerFontSize.value,
        )
        assertEquals(
            17.6f,
            viewConfig(30, 20).toViewSizeConfig(design = small).headerFontSize.value,
        )
        assertEquals(
            17.6f,
            viewConfig(30, 30).toViewSizeConfig(design = small).headerFontSize.value,
        )
    }

    @Test
    fun `2-col 5-row header follows the label size setting`() {
        val small = DataFieldDesignConfig(labelSize = LabelSize.SMALL)
        val large = DataFieldDesignConfig(labelSize = LabelSize.LARGE)
        assertEquals(
            15.5f,
            viewConfig(30, 12).toViewSizeConfig(design = small).headerFontSize.value,
        )
        assertEquals(
            17.6f,
            viewConfig(30, 12).toViewSizeConfig(design = large).headerFontSize.value,
        )
    }

    @Test
    fun `1-col header is unaffected by label size`() {
        val small =
            viewConfig(60, 15)
                .toViewSizeConfig(design = DataFieldDesignConfig(labelSize = LabelSize.SMALL))
        val large =
            viewConfig(60, 15)
                .toViewSizeConfig(design = DataFieldDesignConfig(labelSize = LabelSize.LARGE))
        assertEquals(small.headerFontSize.value, large.headerFontSize.value, 0.001f)
    }

    @Test
    fun `header icon size tracks header font size`() {
        val large =
            viewConfig(30, 12)
                .toViewSizeConfig(design = DataFieldDesignConfig(labelSize = LabelSize.LARGE))
        assertEquals(17.6f, large.headerIconSize.value)
    }

    @Test
    fun `showIcons propagates from design`() {
        assertFalse(
            viewConfig(30, 12)
                .toViewSizeConfig(design = DataFieldDesignConfig(showIcons = false))
                .showIcons
        )
        assertTrue(
            viewConfig(30, 12)
                .toViewSizeConfig(design = DataFieldDesignConfig(showIcons = true))
                .showIcons
        )
    }

    @Test
    fun `default design keeps icons on`() {
        assertTrue(viewConfig(30, 12).toViewSizeConfig().showIcons)
    }

    @Test
    fun `withDesign Large sets header 17_6 and value base 41`() {
        val designed =
            ViewSizeConfig.STANDARD.withDesign(DataFieldDesignConfig(labelSize = LabelSize.LARGE))
        assertEquals(17.6f, designed.headerFontSize.value, 0.001f)
        assertEquals(41, designed.valueFontSizeBase)
    }

    @Test
    fun `withDesign Small sets header 15_5 and value base 47`() {
        val designed =
            ViewSizeConfig.STANDARD.withDesign(DataFieldDesignConfig(labelSize = LabelSize.SMALL))
        assertEquals(15.5f, designed.headerFontSize.value, 0.001f)
        assertEquals(47, designed.valueFontSizeBase)
    }

    @Test
    fun `withDesign propagates showIcons`() {
        assertFalse(
            ViewSizeConfig.STANDARD.withDesign(DataFieldDesignConfig(showIcons = false)).showIcons
        )
    }
}
