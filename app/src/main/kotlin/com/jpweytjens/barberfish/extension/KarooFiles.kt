package com.jpweytjens.barberfish.extension

import android.content.Context

/**
 * True when Barberfish may read the Karoo's files now: the rider has turned KAROO FILES on, the
 * device can run the road engine and both storage permissions are granted. The one answer to that
 * question: nothing starts reading the Karoo's files except through it.
 */
internal fun Context.mayReadKarooFiles(
    config: KarooFilesConfig,
    supported: Boolean = roadMatchingSupported(),
): Boolean = config.enabled && supported && hasRoadDataAccess()
