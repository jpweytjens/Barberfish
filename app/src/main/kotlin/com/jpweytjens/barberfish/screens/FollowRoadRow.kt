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

private val FOLLOW_ROAD_OPTIONS = listOf(false to "Loosely", true to "Closely")

private const val FOLLOW_ROAD_EXPLANATION =
    "Closely takes the road's shape from the Karoo's offline maps."

/**
 * The help line under FOLLOW THE ROAD. The first case that holds wins. It explains Closely, adds
 * what Android will ask while access is not yet granted, and gives way to what is wrong when
 * Closely is chosen and cannot work.
 */
internal fun followRoadHelp(
    on: Boolean,
    supported: Boolean,
    storageGranted: Boolean,
    status: RoadFollowStatus,
): String =
    when {
        !supported -> "Closely is not available on this Karoo."
        on && !storageGranted ->
            "Storage access is off: allow it for Barberfish in the Karoo's app settings."
        on && status == RoadFollowStatus.NO_MAP ->
            "No offline map for this route, so the band follows it loosely."
        !storageGranted ->
            "$FOLLOW_ROAD_EXPLANATION Android will ask for access to photos and media, " +
                "where the maps are stored."
        else -> FOLLOW_ROAD_EXPLANATION
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
 * The FOLLOW THE ROAD row, Loosely or Closely. [onUpdate] stores a change without waiting, as every
 * other row does; [save] returns once the change is on disk, which turning the setting on needs
 * before it opens the permission dialog.
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
    ChoiceRow(
        label = "FOLLOW THE ROAD",
        options = FOLLOW_ROAD_OPTIONS,
        selected = supported && config.followRoad,
        onSelect = { on ->
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
