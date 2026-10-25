package com.ipsuman.sensoractivatedtimer

import android.app.Application

class SensorTimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.initialize(this)
        CrashLogger.installUncaughtExceptionHandler()
        CrashLogger.log("Application.onCreate completed")
    }
}
