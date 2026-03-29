package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.datatype.AvgHRField
import com.jpweytjens.barberfish.datatype.AvgPowerField
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.CadenceField
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HRField
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.LapAvgHRField
import com.jpweytjens.barberfish.datatype.LapPowerField
import com.jpweytjens.barberfish.datatype.NPField
import com.jpweytjens.barberfish.datatype.PowerField
import com.jpweytjens.barberfish.datatype.SpeedField
import com.jpweytjens.barberfish.datatype.TimeField
import com.jpweytjens.barberfish.datatype.TimeKind
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import timber.log.Timber

class BarberfishExtension : KarooExtension("barberfish", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService

    override val types by lazy {
        listOf(
            HUDField(karooSystem),
            PowerField(karooSystem),
            HRField(karooSystem),
            AvgHRField(karooSystem),
            LapAvgHRField(karooSystem),
            SpeedField(karooSystem),
            CadenceField(karooSystem),
            AvgPowerField(karooSystem),
            NPField(karooSystem),
            LapPowerField(karooSystem, isLastLap = false),
            LapPowerField(karooSystem, isLastLap = true),
            GradeField(karooSystem),
            AvgSpeedField(karooSystem, includePaused = true),
            AvgSpeedField(karooSystem, includePaused = false),
            TimeField(karooSystem, TimeKind.TOTAL),
            TimeField(karooSystem, TimeKind.RIDING),
            TimeField(karooSystem, TimeKind.PAUSED),
            TimeField(karooSystem, TimeKind.TIME_TO_DESTINATION),
            TimeField(karooSystem, TimeKind.TIME_OF_ARRIVAL),
            TimeField(karooSystem, TimeKind.TIME_TO_SUNRISE),
            TimeField(karooSystem, TimeKind.TIME_TO_SUNSET),
            TimeField(karooSystem, TimeKind.TIME_TO_CIVIL_DAWN),
            TimeField(karooSystem, TimeKind.TIME_TO_CIVIL_DUSK),
            TimeField(karooSystem, TimeKind.LAP),
            TimeField(karooSystem, TimeKind.LAST_LAP),
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
