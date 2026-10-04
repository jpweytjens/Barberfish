package com.jpweytjens.barberfish

import android.app.Application
import android.util.Log
import timber.log.Timber

/**
 * Tags every line "Barberfish" so `adb logcat -s Barberfish` catches all of it. Release builds keep
 * warnings and errors only.
 */
private class BarberfishTree(private val minPriority: Int) : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String = "Barberfish"

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= minPriority
}

class BarberfishApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(BarberfishTree(if (BuildConfig.DEBUG) Log.VERBOSE else Log.WARN))
    }
}
