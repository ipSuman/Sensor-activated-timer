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
    private const val NOTIFICATION_ID = 1001
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
                        "Timer alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerts when the countdown timer finishes"
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
            // Inexact allow-while-idle alarms normally need no special exact-alarm access.
            CrashLogger.log("Unable to schedule timer notification", error)
        }
    }

    fun cancelTimerComplete(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(completionPendingIntent(context))
    }

    private fun completionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerCompletionReceiver::class.java).setAction(ACTION_TIMER_COMPLETE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
            .setContentText(if (test) "Sensor Timer can show timer alerts on this device." else "Your countdown has completed.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(if (test) NOTIFICATION_ID + 1 else NOTIFICATION_ID, notification)
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
