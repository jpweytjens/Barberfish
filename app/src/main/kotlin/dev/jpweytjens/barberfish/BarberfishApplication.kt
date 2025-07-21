package dev.jpweytjens.barberfish

import android.app.Application
import timber.log.Timber

class BarberfishApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
