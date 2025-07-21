package dev.jpweytjens.barberfish.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import timber.log.Timber

class BarberfishExtension : KarooExtension("barberfish", "0.1") {

    lateinit var karooSystem: KarooSystemService

    override val types by lazy { listOf(AverageSpeedIncludingDataType(karooSystem, extension)) }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)

        Timber.d("Service Barberfish created")
        karooSystem.connect { connected ->
            if (connected) {
                Timber.d("Connected to Karoo system")
            }
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}
