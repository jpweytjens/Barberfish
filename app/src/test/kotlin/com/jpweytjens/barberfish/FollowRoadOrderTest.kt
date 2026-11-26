package com.jpweytjens.barberfish

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.GradeMapWidth
import com.jpweytjens.barberfish.extension.dataStore
import com.jpweytjens.barberfish.extension.saveGradeMapConfig
import com.jpweytjens.barberfish.extension.streamGradeMapConfig
import com.jpweytjens.barberfish.screens.turnFollowRoadOn
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Turning FOLLOW THE ROAD on: the setting is stored before the permission dialog opens. Android
 * restarts the app's process when the rider grants storage access, so a save still under way when
 * the dialog opens could be lost, and the toggle would come back off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowRoadOrderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val config = GradeMapConfig(width = GradeMapWidth.NARROW)

    @Before fun clean() = runBlocking<Unit> { app.dataStore.edit { it.clear() } }

    @Test
    fun the_dialog_waits_for_the_save() = runTest {
        val saving = CompletableDeferred<Unit>()
        val saved = mutableListOf<GradeMapConfig>()
        var asked = 0
        launch {
            turnFollowRoadOn(
                config,
                storageGranted = false,
                save = {
                    saved += it
                    saving.await()
                },
                requestStorage = { asked++ },
            )
        }
        runCurrent()
        assertEquals(listOf(config.copy(followRoad = true)), saved)
        assertEquals(0, asked)

        saving.complete(Unit)
        runCurrent()
        assertEquals(1, asked)
    }

    @Test
    fun the_setting_is_readable_from_the_store_when_the_dialog_opens() = runBlocking {
        assertFalse(app.streamGradeMapConfig().first().followRoad)
        var storedWhenAsked: GradeMapConfig? = null
        turnFollowRoadOn(
            config,
            storageGranted = false,
            save = { app.saveGradeMapConfig(it) },
            requestStorage = {
                storedWhenAsked = runBlocking { app.streamGradeMapConfig().first() }
            },
        )
        assertEquals(config.copy(followRoad = true), storedWhenAsked)
    }

    @Test
    fun a_failed_save_opens_no_dialog() = runTest {
        var asked = 0
        try {
            turnFollowRoadOn(
                config,
                storageGranted = false,
                save = { throw IOException("disk full") },
                requestStorage = { asked++ },
            )
            fail("the failure should reach the caller")
        } catch (_: IOException) {}
        assertEquals(0, asked)
    }

    @Test
    fun with_access_already_granted_it_saves_and_asks_nothing() = runTest {
        val saved = mutableListOf<GradeMapConfig>()
        var asked = 0
        turnFollowRoadOn(config, storageGranted = true, save = { saved += it }) { asked++ }
        assertTrue(saved.single().followRoad)
        assertEquals(0, asked)
    }
}
