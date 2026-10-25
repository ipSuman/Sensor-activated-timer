package com.ipsuman.sensoractivatedtimer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes diagnostic messages and uncaught exceptions to an app-specific log file.
 * Preferred location: Android/data/<package>/files/logs/crash.log
 * Internal storage is used if external app-specific storage is unavailable.
 */
object CrashLogger {
    private const val TAG = "SensorTimer"
    private const val MAX_LOG_BYTES = 512 * 1024
    @Volatile private var logFile: File? = null

    @Synchronized
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val externalDir = appContext.getExternalFilesDir("logs")
        val directory = externalDir ?: File(appContext.filesDir, "logs")
        if (!directory.exists()) directory.mkdirs()
        logFile = File(directory, "crash.log")
        log("---- App process started ----")
    }

    @Synchronized
    fun log(message: String, error: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val writer = StringWriter()
        if (error != null) {
            error.printStackTrace(PrintWriter(writer))
        }
        val entry = buildString {
            append(timestamp).append(" [").append(Thread.currentThread().name).append("] ")
            append(message).append('\n')
            if (error != null) append(writer.toString()).append('\n')
        }
        Log.e(TAG, message, error)
        try {
            val file = logFile ?: return
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                val old = File(file.parentFile, "crash.previous.log")
                if (old.exists()) old.delete()
                file.renameTo(old)
            }
            FileOutputStream(file, true).use { stream ->
                stream.write(entry.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
        } catch (writeError: Exception) {
            Log.e(TAG, "Could not write diagnostic log file", writeError)
        }
    }

    fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("FATAL: uncaught exception on thread ${thread.name}", throwable)
            try {
                previousHandler?.uncaughtException(thread, throwable)
            } catch (handlerError: Exception) {
                Log.e(TAG, "Default exception handler failed", handlerError)
            }
        }
    }
}
