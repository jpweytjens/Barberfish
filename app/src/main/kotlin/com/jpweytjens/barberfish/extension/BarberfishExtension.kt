package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.ThreeColumnField
import com.jpweytjens.barberfish.datatype.TimeField
import com.jpweytjens.barberfish.datatype.TimeKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import timber.log.Timber

class BarberfishExtension : KarooExtension("barberfish", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService

    override val types by lazy {
        listOf(
            ThreeColumnField(karooSystem),
            AvgSpeedField(karooSystem, includePaused = true),
            AvgSpeedField(karooSystem, includePaused = false),
            TimeField(karooSystem, TimeKind.TOTAL),
            TimeField(karooSystem, TimeKind.RIDING),
            TimeField(karooSystem, TimeKind.PAUSED),
            TimeField(karooSystem, TimeKind.TIME_TO_SUNRISE),
            TimeField(karooSystem, TimeKind.TIME_TO_SUNSET),
            TimeField(karooSystem, TimeKind.TIME_TO_CIVIL_DAWN),
            TimeField(karooSystem, TimeKind.TIME_TO_CIVIL_DUSK),
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { Timber.d("Karoo system connected") }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}
