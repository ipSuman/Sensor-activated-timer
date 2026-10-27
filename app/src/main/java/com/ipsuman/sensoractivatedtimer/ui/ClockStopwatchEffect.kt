package com.ipsuman.sensoractivatedtimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/** Advances the stopwatch while it is running, preserving the existing 50 ms tick. */
@Composable
internal fun ClockStopwatchEffect(
    running: Boolean,
    onTick: (Long) -> Unit
) {
    LaunchedEffect(running) {
        while (running) {
            delay(50)
            onTick(50L)
        }
    }
}
