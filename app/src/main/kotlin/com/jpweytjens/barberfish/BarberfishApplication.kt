package com.jpweytjens.barberfish

import android.app.Application
import timber.log.Timber

class BarberfishApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
