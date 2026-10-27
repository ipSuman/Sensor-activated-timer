package com.ipsuman.sensoractivatedtimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Keeps the on-screen clock display refreshed once per second. */
@Composable
internal fun ClockTimeEffect(onTimeChanged: (String) -> Unit) {
    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        while (true) {
            onTimeChanged(LocalTime.now().format(formatter))
            delay(1000)
        }
    }
}
