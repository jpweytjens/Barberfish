package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.BarberfishExtension
import com.jpweytjens.barberfish.extension.HRZoneFieldConfig
import com.jpweytjens.barberfish.extension.ZoneColorMode
import com.jpweytjens.barberfish.extension.ZoneDisplayMode
import com.jpweytjens.barberfish.extension.saveHRZoneFieldConfig
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A zone field's colour and display mode are decided at render time, so two configs over the same
 * reading draw different pixels. Pixels are compared, so the renderer needs native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoneColorRenderingTest {
    @get:Rule val karoo = FakeKarooRule()

    private lateinit var host: FakeKarooHost

    @Before
    fun startExtension() {
        clearBarberfishSettings(ApplicationProvider.getApplicationContext<Application>())
        karoo.system.setUserProfile(FakeKarooSystem.metricProfile())
        karoo.system.setDataPoint(
            DataPoint(DataType.Type.HR_ZONE, mapOf(DataType.Field.HR_ZONE to 3.0))
        )
        host = karoo.host<BarberfishExtension>()
    }

    private fun render() = host.renderField(karoo.app, "hr-zone").fieldValue()

    @Test
    fun the_same_config_draws_the_same_reading_each_time() {
        val first = render()
        val second = render()
        assertTrue("a fixed reading and config must render identically", first.sameAs(second))
    }

    @Test
    fun colouring_the_zone_apart_from_no_colour_is_visible() {
        val coloured = render()

        // Same text and display mode, so only the colour can differ.
        runBlocking {
            karoo.app.saveHRZoneFieldConfig(
                HRZoneFieldConfig(
                    colorMode = ZoneColorMode.NONE,
                    zoneDisplayMode = ZoneDisplayMode.FLOAT,
                )
            )
        }
        assertFalse("zone colour off should not match the coloured zone", coloured.sameAs(render()))
    }

    @Test
    fun a_decimal_zone_reading_differs_from_an_integer_one() {
        runBlocking {
            karoo.app.saveHRZoneFieldConfig(
                HRZoneFieldConfig(
                    colorMode = ZoneColorMode.TEXT,
                    zoneDisplayMode = ZoneDisplayMode.INTEGER,
                )
            )
        }
        val integer = render()

        runBlocking {
            karoo.app.saveHRZoneFieldConfig(
                HRZoneFieldConfig(
                    colorMode = ZoneColorMode.TEXT,
                    zoneDisplayMode = ZoneDisplayMode.FLOAT,
                )
            )
        }
        assertFalse("3 and 3.0 must not read the same", integer.sameAs(render()))
    }
}
