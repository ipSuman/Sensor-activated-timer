package com.ipsuman.sensoractivatedtimer.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    val latestTimerRunning by rememberUpdatedState(timerRunning)
    val latestRemainingSeconds by rememberUpdatedState(remainingSeconds)
    val latestStopwatchRunning by rememberUpdatedState(stopwatchRunning)
    val latestStopwatchMillis by rememberUpdatedState(stopwatchMillis)
    val latestNotificationsEnabled by rememberUpdatedState(notificationsEnabled)
    val latestNotificationPermissionGranted by rememberUpdatedState(notificationPermissionGranted)

    LaunchedEffect(timerRunning, notificationsEnabled, notificationPermissionGranted) {
        if (timerRunning && notificationsEnabled && notificationPermissionGranted && remainingSeconds > 0) {
            TimerNotificationHelper.scheduleTimerComplete(context, remainingSeconds)
        } else {
            TimerNotificationHelper.cancelTimerComplete(context)
        }
    }

    LaunchedEffect(timerRunning) {
        while (latestTimerRunning && latestRemainingSeconds > 0) {
            delay(1000)
            val nextRemaining = (latestRemainingSeconds - 1).coerceAtLeast(0)
            onRemainingSecondsChanged(nextRemaining)
            if (nextRemaining == 0) {
                onTimerRunningChanged(false)
                if (latestNotificationsEnabled && latestNotificationPermissionGranted) {
                    TimerNotificationHelper.showTimerComplete(context)
                }
            }
        }
    }

    LaunchedEffect(timerRunning, stopwatchRunning, notificationsEnabled, notificationPermissionGranted) {
        if (!notificationsEnabled || !notificationPermissionGranted || (!timerRunning && !stopwatchRunning)) {
            TimerNotificationHelper.cancelClockStatus(context)
        } else {
            while (latestNotificationsEnabled && latestNotificationPermissionGranted && (latestTimerRunning || latestStopwatchRunning)) {
                val timerText = formatTime(latestRemainingSeconds)
                val stopwatchText = formatStopwatch(latestStopwatchMillis)
                when {
                    latestTimerRunning && latestStopwatchRunning -> TimerNotificationHelper.showClockStatus(
                        context,
                        "Timer & Stopwatch",
                        "Timer: $timerText remaining • Stopwatch: $stopwatchText elapsed"
                    )
                    latestTimerRunning -> TimerNotificationHelper.showClockStatus(context, "Timer", timerText)
                    latestStopwatchRunning -> TimerNotificationHelper.showClockStatus(context, "Stopwatch", stopwatchText)
                }
                delay(1000)
            }
            TimerNotificationHelper.cancelClockStatus(context)
        }
    }
}
