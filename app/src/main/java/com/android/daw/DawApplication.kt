package com.android.daw

import android.app.Application
import android.util.Log
import com.android.daw.diagnostics.CrashLogger

/**
 * DawApplication
 *
 * Core application class for the Android Digital Audio Workstation.
 * Initializes global application context, crash reporting, and native audio engine library.
 */
class DawApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Install Global Crash Watchdog & Diagnostics Logging Hub first
        CrashLogger.install(this)
        CrashLogger.i(TAG, "Initializing Android DAW Application...")

        // Attempt early native library pre-load with graceful fallback
        try {
            System.loadLibrary("daw_audio_engine")
            CrashLogger.i(TAG, "Native audio engine dynamic library (libdaw_audio_engine.so) loaded successfully.")
        } catch (unsatisfiedLink: UnsatisfiedLinkError) {
            CrashLogger.w(
                TAG,
                "Native library 'daw_audio_engine' not yet present in runtime environment. " +
                    "Fallback simulated audio clock will be active until native engine compilation.",
                unsatisfiedLink
            )
        } catch (throwable: Throwable) {
            CrashLogger.e(TAG, "Unexpected error loading native library: ${throwable.message}", throwable)
        }
    }

    companion object {
        private const val TAG = "DawApplication"

        @Volatile
        private var instance: DawApplication? = null

        fun getInstance(): DawApplication {
            return instance ?: throw IllegalStateException("DawApplication instance has not been initialized yet.")
        }
    }
}
