package com.jpweytjens.barberfish.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jpweytjens.barberfish.extension.GradeMapConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.hasRoadDataAccess
import com.jpweytjens.barberfish.extension.roadFollowStatus
import com.jpweytjens.barberfish.extension.roadMatchingSupported
import java.io.IOException
import kotlinx.coroutines.launch
import timber.log.Timber

/** The help line under FOLLOW THE ROAD. The first case that holds wins. */
internal fun followRoadHelp(
    on: Boolean,
    supported: Boolean,
    storageGranted: Boolean,
    status: RoadFollowStatus,
): String =
    when {
        !supported -> "Not available on this Karoo."
        !on ->
            "Draws the band along the road in bends, from the Karoo's offline maps. " +
                "Android will ask for access to photos and media: that is where the maps " +
                "are stored."
        !storageGranted ->
            "Storage access is off. Allow it for Barberfish in the Karoo's app settings."
        status == RoadFollowStatus.FOLLOWING -> "Following the road."
        status == RoadFollowStatus.NO_MAP ->
            "No offline map for this area; bends are cut straight here."
        else -> "The band follows the road where the Karoo has offline maps."
    }

/**
 * Stores the setting as on and only then asks for storage access. Android restarts the app's
 * process when the rider grants it, so the setting has to be on disk before the dialog opens:
 * nothing after the dialog can be relied on. Returns once [save] has returned; [requestStorage] is
 * not called when access is already granted or when [save] throws.
 */
internal suspend fun turnFollowRoadOn(
    config: GradeMapConfig,
    storageGranted: Boolean,
    save: suspend (GradeMapConfig) -> Unit,
    requestStorage: () -> Unit,
) {
    save(config.copy(followRoad = true))
    if (!storageGranted) requestStorage()
}

/**
 * The FOLLOW THE ROAD toggle. [onUpdate] stores a change without waiting, as every other row does;
 * [save] returns once the change is on disk, which turning the setting on needs before it opens the
 * permission dialog.
 */
@Composable
internal fun FollowRoadRow(
    config: GradeMapConfig,
    onUpdate: (GradeMapConfig) -> Unit,
    save: suspend (GradeMapConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supported = remember { roadMatchingSupported() }
    val status by roadFollowStatus.collectAsState()
    val latest by rememberUpdatedState(config)
    var storageGranted by remember { mutableStateOf(context.hasRoadDataAccess()) }
    var turningOn by remember { mutableStateOf(false) }
    // The rider can change the permission in the Karoo's settings and come back.
    LifecycleResumeEffect(Unit) {
        storageGranted = context.hasRoadDataAccess()
        onPauseOrDispose {}
    }
    val storageDialog =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            storageGranted = context.hasRoadDataAccess()
            // Refused while Android would still ask again: back to off, so the next tap asks
            // again. Refused for good: the setting stays on, and the help line says where to
            // allow it.
            val canAskAgain =
                (context as? Activity)?.let { activity ->
                    ROAD_DATA_PERMISSIONS.any { activity.shouldShowRequestPermissionRationale(it) }
                } ?: false
            if (!storageGranted && canAskAgain) onUpdate(latest.copy(followRoad = false))
        }
    BoolToggleRow(
        label = "FOLLOW THE ROAD",
        value = supported && config.followRoad,
        onChange = { on ->
            when {
                !supported || turningOn -> Unit
                !on -> onUpdate(config.copy(followRoad = false))
                else ->
                    scope.launch {
                        turningOn = true
                        try {
                            turnFollowRoadOn(config, storageGranted, save) {
                                storageDialog.launch(ROAD_DATA_PERMISSIONS)
                            }
                        } catch (e: IOException) {
                            // Not stored, so not turned on: no dialog, the toggle stays off.
                            Timber.w(e, "Could not store the follow the road setting")
                        } finally {
                            turningOn = false
                        }
                    }
            }
        },
        help = followRoadHelp(config.followRoad, supported, storageGranted, status),
    )
}
