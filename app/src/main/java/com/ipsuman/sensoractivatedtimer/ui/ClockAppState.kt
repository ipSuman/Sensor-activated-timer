package com.ipsuman.sensoractivatedtimer.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Owns the primary clock values independently of the screen's UI.
 *
 * Keeping these observable values together makes the timer/stopwatch state
 * easier to test and maintain without changing Compose's reactive updates.
 */
internal class ClockAppState {
    val currentTime = mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
    val durationInput = mutableStateOf("60")
    val remainingSeconds = mutableIntStateOf(0)
    val timerRunning = mutableStateOf(false)
    val stopwatchRunning = mutableStateOf(false)
    val stopwatchMillis = mutableLongStateOf(0L)
}
