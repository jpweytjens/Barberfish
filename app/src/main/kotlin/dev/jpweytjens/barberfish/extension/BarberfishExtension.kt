package dev.jpweytjens.barberfish.extension

import dagger.hilt.android.AndroidEntryPoint
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import javax.inject.Inject

@AndroidEntryPoint
class BarberfishExtension : KarooExtension("barberfish", "0.1") {
    @Inject lateinit var karooSystem: KarooSystemService

    override val types by lazy {
        listOf(
                AverageSpeedIncludingDataType(karooSystem, extension),
                AverageSpeedExcludingDataType(karooSystem, extension)
        )
    }

    override fun onCreate() {
        karooSystem.connect { connected ->
            if (connected) {
                // Extension connected successfully
            }
        }
    }
}
