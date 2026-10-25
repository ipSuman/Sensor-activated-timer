package com.ipsuman.sensoractivatedtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipsuman.sensoractivatedtimer.sensor.ProximitySensorController
import kotlinx.coroutines.delay

private val AppColors = darkColorScheme(
    primary = Color(0xFF70E1D1),
    onPrimary = Color(0xFF062B29),
    secondary = Color(0xFFFFB86B),
    background = Color(0xFF101719),
    surface = Color(0xFF1A2427),
    onBackground = Color(0xFFEAF4F3),
    onSurface = Color(0xFFEAF4F3)
)

private enum class AppTab(val title: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), SETTINGS("Settings")
}

@Composable
fun ClockApp() {
    var selectedTab by remember { mutableStateOf(AppTab.TIMER) }
    var durationInput by remember { mutableStateOf("60") }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var stopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchMillis by remember { mutableLongStateOf(0L) }
    var proximityAvailable by remember { mutableStateOf(true) }
    var proximityEnabled by remember { mutableStateOf(true) }

    val latestTimerState by rememberUpdatedState(
        Triple(timerRunning, remainingSeconds, durationInput.toIntOrNull() ?: 0)
    )
    val latestStartTimer by rememberUpdatedState<() -> Unit>({
        val state = latestTimerState
        if (!state.first && state.second == 0 && state.third > 0) {
            remainingSeconds = state.third.coerceAtMost(24 * 60 * 60)
            timerRunning = true
        }
    })
    val context = LocalContext.current

    DisposableEffect(context, proximityEnabled) {
        if (!proximityEnabled) {
            onDispose { }
        } else {
            val controller = ProximitySensorController(
                context = context,
                onCovered = { latestStartTimer() },
                onAvailabilityChanged = { proximityAvailable = it }
            )
            controller.start()
            onDispose { controller.stop() }
        }
    }

    LaunchedEffect(timerRunning) {
        while (timerRunning && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
            if (remainingSeconds == 0) timerRunning = false
        }
    }

    LaunchedEffect(stopwatchRunning) {
        while (stopwatchRunning) {
            delay(50)
            stopwatchMillis += 50
        }
    }

    MaterialTheme(colorScheme = AppColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = AppColors.background) {
            Scaffold(
                containerColor = AppColors.background,
                topBar = {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                        Text(
                            "SENSOR TIMER",
                            color = AppColors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("Clock", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                },
                bottomBar = {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        AppTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.title) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    when (selectedTab) {
                        AppTab.TIMER -> TimerScreen(
                            durationInput = durationInput,
                            onDurationChange = { durationInput = it.filter(Char::isDigit).take(5) },
                            remainingSeconds = remainingSeconds,
                            timerRunning = timerRunning,
                            onStart = {
                                if (!timerRunning) {
                                    if (remainingSeconds == 0) {
                                        remainingSeconds = (durationInput.toIntOrNull() ?: 0)
                                            .coerceIn(1, 24 * 60 * 60)
                                    }
                                    timerRunning = remainingSeconds > 0
                                }
                            },
                            onPause = { timerRunning = false },
                            onReset = { timerRunning = false; remainingSeconds = 0 }
                        )
                        AppTab.STOPWATCH -> StopwatchScreen(
                            elapsedMillis = stopwatchMillis,
                            running = stopwatchRunning,
                            onToggle = { stopwatchRunning = !stopwatchRunning },
                            onReset = { stopwatchRunning = false; stopwatchMillis = 0L }
                        )
                        AppTab.SETTINGS -> SettingsScreen(
                            proximityEnabled = proximityEnabled,
                            onProximityChanged = { proximityEnabled = it },
                            proximityAvailable = proximityAvailable
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerScreen(
    durationInput: String,
    onDurationChange: (String) -> Unit,
    remainingSeconds: Int,
    timerRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit
) {
    Text("COUNTDOWN", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(formatTime(remainingSeconds), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = durationInput,
                onValueChange = onDurationChange,
                label = { Text("Duration in seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStart, enabled = !timerRunning) {
                    Text(if (remainingSeconds > 0) "Resume" else "Start")
                }
                Button(onClick = onPause, enabled = timerRunning) { Text("Pause") }
                Button(onClick = onReset) { Text("Reset") }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        "Proximity trigger: cover your phone's proximity sensor to start the configured countdown.",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    )
}

@Composable
private fun StopwatchScreen(
    elapsedMillis: Long,
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    Text("STOPWATCH", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(formatStopwatch(elapsedMillis), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onToggle) { Text(if (running) "Pause" else "Start") }
                Button(onClick = onReset) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    proximityEnabled: Boolean,
    onProximityChanged: (Boolean) -> Unit,
    proximityAvailable: Boolean
) {
    Text("SENSOR SETTINGS", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Proximity sensor", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (proximityAvailable) "Sensor detected on this device."
                else "This device does not report a proximity sensor.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (proximityEnabled) "Enabled" else "Disabled", modifier = Modifier.weight(1f))
                Button(onClick = { onProximityChanged(!proximityEnabled) }) {
                    Text(if (proximityEnabled) "Turn off" else "Turn on")
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Accelerometer, microphone and camera triggers will be added in later steps.")
}

private fun formatTime(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)

private fun formatStopwatch(milliseconds: Long): String {
    val totalCentiseconds = milliseconds / 10
    val minutes = totalCentiseconds / 6000
    val seconds = (totalCentiseconds / 100) % 60
    val centiseconds = totalCentiseconds % 100
    return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}
