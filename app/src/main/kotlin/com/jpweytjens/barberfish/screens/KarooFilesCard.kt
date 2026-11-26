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
import com.jpweytjens.barberfish.extension.KarooFilesConfig
import com.jpweytjens.barberfish.extension.ROAD_DATA_PERMISSIONS
import com.jpweytjens.barberfish.extension.RoadFollowStatus
import com.jpweytjens.barberfish.extension.hasRoadDataAccess
import com.jpweytjens.barberfish.extension.roadFollowStatus
import com.jpweytjens.barberfish.extension.roadMatchingSupported
import java.io.IOException
import kotlinx.coroutines.launch
import timber.log.Timber

private const val KAROO_FILES_EXPLANATION =
    "Reads the Karoo's offline maps. The grade map follows the road through bends, and off the " +
        "route the grade map and Profile show the climb back to it."

/**
 * The help line under OFFLINE MAPS. The first case that holds wins. It explains the switch, adds
 * what Android will ask while access is not yet granted, and gives way to what is wrong when the
 * switch is on and cannot work.
 */
internal fun karooFilesHelp(
    on: Boolean,
    supported: Boolean,
    storageGranted: Boolean,
    status: RoadFollowStatus,
): String =
    when {
        !supported -> "Not available on this Karoo."
        on && !storageGranted ->
            "Storage access is off: allow it for Barberfish in the Karoo's app settings."
        on && status == RoadFollowStatus.NO_MAP ->
            "No offline map for this route, so the grade map keeps to the route line."
        !storageGranted ->
            "$KAROO_FILES_EXPLANATION Android will ask for access to photos and media, " +
                "where the maps are stored."
        else -> KAROO_FILES_EXPLANATION
    }

/**
 * Stores the switch as on and only then asks for storage access. Android restarts the app's process
 * when the rider grants it, so the switch has to be on disk before the dialog opens: nothing after
 * the dialog can be relied on. Returns once [save] has returned; [requestStorage] is not called
 * when access is already granted or when [save] throws.
 */
internal suspend fun turnKarooFilesOn(
    storageGranted: Boolean,
    save: suspend (KarooFilesConfig) -> Unit,
    requestStorage: () -> Unit,
) {
    save(KarooFilesConfig(enabled = true))
    if (!storageGranted) requestStorage()
}

/**
 * The KAROO FILES card, one OFFLINE MAPS switch. [onUpdate] stores a change without waiting, as
 * every other card does; [save] returns once the change is on disk, which turning the switch on
 * needs before it opens the permission dialog.
 */
@Composable
internal fun KarooFilesCard(
    config: KarooFilesConfig,
    selected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (KarooFilesConfig) -> Unit,
    save: suspend (KarooFilesConfig) -> Unit,
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
            // again. Refused for good: the switch stays on, and the help line says where to
            // allow it.
            val canAskAgain =
                (context as? Activity)?.let { activity ->
                    ROAD_DATA_PERMISSIONS.any { activity.shouldShowRequestPermissionRationale(it) }
                } ?: false
            if (!storageGranted && canAskAgain) onUpdate(latest.copy(enabled = false))
        }
    ExpandableCard(title = "KAROO FILES", selected = selected, onSelect = onSelect) {
        BoolToggleRow(
            label = "OFFLINE MAPS",
            value = supported && config.enabled,
            onChange = { on ->
                when {
                    !supported || turningOn -> Unit
                    !on -> onUpdate(config.copy(enabled = false))
                    else ->
                        scope.launch {
                            turningOn = true
                            try {
                                turnKarooFilesOn(storageGranted, save) {
                                    storageDialog.launch(ROAD_DATA_PERMISSIONS)
                                }
                            } catch (e: IOException) {
                                // Not stored, so not turned on: no dialog, the toggle stays off.
                                Timber.w(e, "Could not store the Karoo files setting")
                            } finally {
                                turningOn = false
                            }
                        }
                }
            },
            help = karooFilesHelp(config.enabled, supported, storageGranted, status),
        )
    }
}
