package com.stille.messenger

import android.app.Application
import com.stille.messenger.diag.StilleLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StilleMessengerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StilleLog.init(this)
        installCrashLogging()
    }

    /**
     * Records the exception's class name into the diagnostics log, then
     * hands over to the platform handler so the crash behaves normally.
     */
    private fun installCrashLogging() {
        val platform = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                StilleLog.e("app", "uncaught-exception", error, "thread=${thread.name}")
            } catch (_: Throwable) { }
            platform?.uncaughtException(thread, error)
        }
    }
}
