package com.ipsuman.sensoractivatedtimer.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ipsuman.sensoractivatedtimer.TimerNotificationHelper
import kotlinx.coroutines.delay

/**
 * Runs timer countdown and keeps timer/stopwatch notifications synchronized
 * with the current clock state.
 */
@Composable
internal fun ClockTimerEffects(
    context: Context,
    timerRunning: Boolean,
    remainingSeconds: Int,
    onRemainingSecondsChanged: (Int) -> Unit,
    onTimerRunningChanged: (Boolean) -> Unit,
    stopwatchRunning: Boolean,
    stopwatchMillis: Long,
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean
) {
    LaunchedEffect(timerRunning, notificationsEnabled, notificationPermissionGranted) {
        if (timerRunning && notificationsEnabled && notificationPermissionGranted && remainingSeconds > 0) {
            TimerNotificationHelper.scheduleTimerComplete(context, remainingSeconds)
        } else {
            TimerNotificationHelper.cancelTimerComplete(context)
        }
    }

    LaunchedEffect(timerRunning) {
        while (timerRunning && remainingSeconds > 0) {
            delay(1000)
            val nextRemaining = (remainingSeconds - 1).coerceAtLeast(0)
            onRemainingSecondsChanged(nextRemaining)
            if (nextRemaining == 0) {
                onTimerRunningChanged(false)
                if (notificationsEnabled && notificationPermissionGranted) {
                    TimerNotificationHelper.showTimerComplete(context)
                }
            }
        }
    }

    LaunchedEffect(timerRunning, stopwatchRunning, notificationsEnabled, notificationPermissionGranted) {
        if (!notificationsEnabled || !notificationPermissionGranted || (!timerRunning && !stopwatchRunning)) {
            TimerNotificationHelper.cancelClockStatus(context)
        } else {
            while (notificationsEnabled && notificationPermissionGranted && (timerRunning || stopwatchRunning)) {
                val timerText = formatTime(remainingSeconds)
                val stopwatchText = formatStopwatch(stopwatchMillis)
                when {
                    timerRunning && stopwatchRunning -> TimerNotificationHelper.showClockStatus(
                        context,
                        "Timer & Stopwatch",
                        "Timer: $timerText remaining • Stopwatch: $stopwatchText elapsed"
                    )
                    timerRunning -> TimerNotificationHelper.showClockStatus(context, "Timer", timerText)
                    stopwatchRunning -> TimerNotificationHelper.showClockStatus(context, "Stopwatch", stopwatchText)
                }
                delay(1000)
            }
            TimerNotificationHelper.cancelClockStatus(context)
        }
    }
}
