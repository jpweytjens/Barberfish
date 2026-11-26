package com.jpweytjens.barberfish.karoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.mayReadKarooFiles
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
}
