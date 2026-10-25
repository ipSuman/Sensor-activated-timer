package com.ipsuman.sensoractivatedtimer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object TimerNotificationHelper {
    const val CHANNEL_ID = "timer_completion"
    const val ACTION_TIMER_COMPLETE = "com.ipsuman.sensoractivatedtimer.TIMER_COMPLETE"
    const val EXTRA_CLOCK_NAME = "clock_name"
    private const val NOTIFICATION_ID = 1001
    private const val TEST_NOTIFICATION_ID = 1002
    private const val STATUS_NOTIFICATION_ID = 1003
    private const val REQUEST_CODE = 701

    fun hasPermission(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Timer and stopwatch",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Clock status and timer completion alerts"
                        enableVibration(true)
                    }
                )
            }
        }
    }

    fun scheduleTimerComplete(context: Context, seconds: Int) {
        if (seconds <= 0) return
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = completionPendingIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + seconds.coerceAtLeast(1) * 1000L
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        } catch (error: SecurityException) {
            CrashLogger.log("Unable to schedule timer notification", error)
        }
    }

    fun cancelTimerComplete(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(completionPendingIntent(context))
    }

    private fun completionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerCompletionReceiver::class.java)
            .setAction(ACTION_TIMER_COMPLETE)
            .putExtra(EXTRA_CLOCK_NAME, "Timer")
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showClockStatus(context: Context, clockName: String, timeText: String) {
        ensureChannel(context)
        if (!hasPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$clockName running")
            .setContentText(if (clockName == "Timer") "Time remaining: $timeText" else "Elapsed time: $timeText")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(STATUS_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            CrashLogger.log("Unable to update clock status notification", error)
        }
    }

    fun cancelClockStatus(context: Context) {
        NotificationManagerCompat.from(context).cancel(STATUS_NOTIFICATION_ID)
    }

    fun showClockStopped(context: Context, clockName: String, timeText: String) {
        ensureChannel(context)
        if (!hasPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$clockName paused")
            .setContentText(if (clockName == "Timer") "Time remaining: $timeText" else "Elapsed time: $timeText")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(STATUS_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            CrashLogger.log("Unable to show stopped clock notification", error)
        }
    }

    fun showTimerComplete(context: Context, test: Boolean = false): Boolean {
        ensureChannel(context)
        if (!hasPermission(context)) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return false

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (test) "Notifications are working" else "Timer finished")
            .setContentText(if (test) "Sensor Timer can show timer and stopwatch status." else "Timer completed — 00:00 remaining.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(if (test) TEST_NOTIFICATION_ID else NOTIFICATION_ID, notification)
            true
        } catch (error: SecurityException) {
            CrashLogger.log("Unable to show timer notification", error)
            false
        }
    }
}

class TimerCompletionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == TimerNotificationHelper.ACTION_TIMER_COMPLETE) {
            TimerNotificationHelper.showTimerComplete(context)
        }
    }
}
