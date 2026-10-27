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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Writes diagnostic messages and uncaught exceptions to an app-specific log file.
 * File I/O is queued on a single background worker so frequent log calls never
 * synchronously open, flush, or fsync the log file on the caller's thread.
 */
object CrashLogger {
    private const val TAG = "SensorTimer"
    private const val MAX_LOG_BYTES = 512 * 1024
    private const val LOG_QUEUE_CAPACITY = 1024

    @Volatile private var logFile: File? = null
    @Volatile private var droppedEntries = 0

    private val writerExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(LOG_QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "SensorTimer-LogWriter").apply { isDaemon = true }
        },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val externalDir = appContext.getExternalFilesDir("logs")
        val directory = externalDir ?: File(appContext.filesDir, "logs")
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            Log.e(TAG, "Could not create diagnostic log directory: ${directory.absolutePath}")
        }
        logFile = File(directory, "crash.log")
        log("---- App process started ----")
    }

    /**
     * Logs to Android logcat immediately and queues file persistence asynchronously.
     * The queue is bounded to prevent logging from growing memory without limit.
     */
    fun log(message: String, error: Throwable? = null) {
        val entry = formatEntry(message, error)
        Log.e(TAG, message, error)

        if (logFile == null) return
        try {
            writerExecutor.execute {
                try {
                    writeEntry(entry)
                } catch (writeError: Exception) {
                    Log.e(TAG, "Could not write diagnostic log file", writeError)
                }
            }
        } catch (rejected: RuntimeException) {
            // Keep logging from interfering with application behavior if the executor
            // has been shut down or rejects work for any unexpected reason.
            Log.e(TAG, "Could not queue diagnostic log entry", rejected)
        }
    }

    private fun formatEntry(message: String, error: Throwable?): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val stackTrace = if (error != null) {
            StringWriter().also { writer ->
                error.printStackTrace(PrintWriter(writer))
            }.toString()
        } else {
            null
        }
        return buildString {
            append(timestamp).append(" [").append(Thread.currentThread().name).append("] ")
            append(message).append('\n')
            if (stackTrace != null) append(stackTrace).append('\n')
        }
    }

    @Synchronized
    private fun writeEntry(entry: String) {
        val file = logFile ?: return
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            val old = File(file.parentFile, "crash.previous.log")
            if (old.exists() && !old.delete()) {
                Log.w(TAG, "Could not delete previous diagnostic log: ${old.absolutePath}")
            }
            if (!file.renameTo(old)) {
                Log.w(TAG, "Could not rotate diagnostic log: ${file.absolutePath}")
            }
        }
        FileOutputStream(file, true).use { stream ->
            stream.write(entry.toByteArray(Charsets.UTF_8))
            // Intentionally no fd.sync(): this runs on the background writer thread.
        }
    }

    fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val fatalEntry = formatEntry("FATAL: uncaught exception on thread ${thread.name}", throwable)
            Log.e(TAG, "FATAL: uncaught exception on thread ${thread.name}", throwable)
            // The process may terminate immediately after this handler returns. Persist
            // the fatal record synchronously as a deliberate exception to normal async logging.
            try {
                writeEntry(fatalEntry)
            } catch (writeError: Exception) {
                Log.e(TAG, "Could not persist fatal diagnostic log entry", writeError)
            }
            try {
                previousHandler?.uncaughtException(thread, throwable)
            } catch (handlerError: Exception) {
                Log.e(TAG, "Default exception handler failed", handlerError)
            }
        }
    }
}
