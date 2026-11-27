package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.hasRoadDataAccess
import com.jpweytjens.barberfish.extension.mayReadKarooFiles
import com.jpweytjens.barberfish.screens.karooFilesHelp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** When Barberfish may read the Karoo's files: the switch, the device and both permissions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KarooFilesAccessTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val on = KarooFilesConfig(enabled = true)
    private val off = KarooFilesConfig(enabled = false)

    private fun grantStorage() = shadowOf(app).grantPermissions(*ROAD_DATA_PERMISSIONS)

    @Test
    fun with_the_switch_on_a_supported_device_and_both_permissions_it_may() {
        grantStorage()
        assertTrue(app.mayReadKarooFiles(on, supported = true))
    }

    @Test
    fun with_the_switch_off_it_may_not() {
        grantStorage()
        assertFalse(app.mayReadKarooFiles(off, supported = true))
    }

    @Test
    fun on_an_unsupported_device_it_may_not() {
        grantStorage()
        assertFalse(app.mayReadKarooFiles(on, supported = false))
    }

    @Test
    fun without_storage_access_it_may_not() {
        assertFalse(app.mayReadKarooFiles(on, supported = true))
    }

    @Test
    fun with_one_of_the_two_permissions_it_may_not() {
        ROAD_DATA_PERMISSIONS.forEach { permission ->
            shadowOf(app).denyPermissions(*ROAD_DATA_PERMISSIONS)
            shadowOf(app).grantPermissions(permission)
            assertFalse(permission, app.mayReadKarooFiles(on, supported = true))
        }
    }

    private val cannotWork =
        setOf(
            "Not available on this Karoo.",
            "Storage access is off: allow it for Barberfish in the Karoo's app settings.",
        )

    // The help under every road-follow status, against the access answer for the same inputs.
    private fun assertHelpAgreesWithAccess(config: KarooFilesConfig, supported: Boolean) {
        val may = app.mayReadKarooFiles(config, supported)
        val case = "on=${config.enabled} supported=$supported granted=${app.hasRoadDataAccess()}"
        RoadFollowStatus.entries.forEach { status ->
            val help = karooFilesHelp(config.enabled, supported, app.hasRoadDataAccess(), status)
            if (config.enabled && !may) assertTrue(case, help in cannotWork)
            if (may) assertFalse(case, help in cannotWork)
        }
    }

    @Test
    fun the_help_says_it_cannot_work_exactly_when_the_switch_is_on_and_it_may_not() {
        for (granted in listOf(true, false)) {
            if (granted) grantStorage() else shadowOf(app).denyPermissions(*ROAD_DATA_PERMISSIONS)
            for (config in listOf(on, off)) {
                for (supported in listOf(true, false)) assertHelpAgreesWithAccess(config, supported)
            }
        }
    }
}
