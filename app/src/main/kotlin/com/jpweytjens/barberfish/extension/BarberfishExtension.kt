package com.jpweytjens.barberfish.extension

import com.jpweytjens.barberfish.BuildConfig
import com.jpweytjens.barberfish.datatype.AvgHRField
import com.jpweytjens.barberfish.datatype.AvgPowerField
import com.jpweytjens.barberfish.datatype.AvgSpeedField
import com.jpweytjens.barberfish.datatype.CadenceField
import com.jpweytjens.barberfish.datatype.ETAField
import com.jpweytjens.barberfish.datatype.ETAKind
import com.jpweytjens.barberfish.datatype.GradeField
import com.jpweytjens.barberfish.datatype.HRField
import com.jpweytjens.barberfish.datatype.HUDField
import com.jpweytjens.barberfish.datatype.LapAvgHRField
import com.jpweytjens.barberfish.datatype.LapPowerField
import com.jpweytjens.barberfish.datatype.LastLapAvgHRField
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

    // Order matches extension_info.xml — keep in sync when adding fields.
    override val types by lazy {
        listOf(
            HUDField(karooSystem),
            // Power
            PowerField(karooSystem),
            AvgPowerField(karooSystem),
            NPField(karooSystem),
            LapPowerField(karooSystem, isLastLap = false),
            LapPowerField(karooSystem, isLastLap = true),
            // HR
            HRField(karooSystem),
            AvgHRField(karooSystem),
            LapAvgHRField(karooSystem),
            LastLapAvgHRField(karooSystem),
            // Speed
            SpeedField(karooSystem),
            AvgSpeedField(karooSystem, includePaused = true),
            AvgSpeedField(karooSystem, includePaused = false),
            // Other
            CadenceField(karooSystem),
            GradeField(karooSystem),
            // Time
            TimeField(karooSystem, TimeKind.TOTAL),
            TimeField(karooSystem, TimeKind.RIDING),
            TimeField(karooSystem, TimeKind.PAUSED),
            TimeField(karooSystem, TimeKind.LAP),
            TimeField(karooSystem, TimeKind.LAST_LAP),
            ETAField(karooSystem, ETAKind.REMAINING_RIDE_TIME),
            ETAField(karooSystem, ETAKind.TIME_TO_DESTINATION),
            ETAField(karooSystem, ETAKind.TIME_OF_ARRIVAL),
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
