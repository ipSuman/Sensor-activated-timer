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

internal enum class AppTab(val title: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), SETTINGS("Settings")
}

internal enum class ProximityTarget(val label: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), BOTH("Both")
}

internal enum class AccelerometerTarget(val label: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), BOTH("Both")
}

internal enum class MicrophoneTarget(val label: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), BOTH("Both")
}

internal enum class VolumeGestureTarget(val label: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), BOTH("Both")
}

internal enum class MicrophoneMode(val label: String, val description: String) {
    RECORDED_SOUND("Recorded sound", "Record a reference sound, then match its intensity and duration using the tolerances below."),
    TONE("Tone / music", "Hold a clock while sound intensity stays above the threshold or within a saved reference tolerance.")
}

