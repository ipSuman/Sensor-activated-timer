package com.ipsuman.sensoractivatedtimer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.ipsuman.sensoractivatedtimer.TimerNotificationHelper
import com.ipsuman.sensoractivatedtimer.sensor.ProximitySensorController
import com.ipsuman.sensoractivatedtimer.sensor.MicrophoneTapController
import com.ipsuman.sensoractivatedtimer.sensor.MicrophoneTriggerMode
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DarkColors = darkColorScheme()
private val LightColors = lightColorScheme()

private enum class AppTab(val title: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), SETTINGS("Settings")
}

private enum class ProximityTarget(val label: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), BOTH("Both")
}

private enum class MicrophoneTarget(val label: String) {
    TIMER("Timer"), STOPWATCH("Stopwatch"), BOTH("Both")
}

private enum class MicrophoneMode(val label: String, val description: String) {
    RECORDED_SOUND("Recorded sound", "Record a sound, then trigger when the live microphone intensity is within ±70% of its recorded intensity."),
    TONE("Tone / music", "Triggers when a sustained loud tone or music is heard; it triggers once per sound episode.")
}

@Composable
fun ClockApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.TIMER) }
    var currentTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) }
    var durationInput by remember { mutableStateOf("60") }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var stopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchMillis by remember { mutableLongStateOf(0L) }
    var proximityAvailable by remember { mutableStateOf(true) }
    var proximityEnabled by remember { mutableStateOf(true) }
    var proximityTarget by remember { mutableStateOf(ProximityTarget.TIMER) }
    var microphoneEnabled by remember { mutableStateOf(false) }
    var microphonePermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var microphoneTarget by remember { mutableStateOf(MicrophoneTarget.TIMER) }
    var microphoneMode by remember { mutableStateOf(MicrophoneMode.RECORDED_SOUND) }
    var microphoneError by remember { mutableStateOf<String?>(null) }
    var microphoneMessage by remember { mutableStateOf<String?>(null) }
    var microphoneController by remember { mutableStateOf<MicrophoneTapController?>(null) }
    val microphonePrefs = remember(context) { context.getSharedPreferences("microphone_settings", android.content.Context.MODE_PRIVATE) }
    var referenceIntensity by remember { mutableStateOf(microphonePrefs.getFloat("reference_intensity", 0f).toDouble()) }
    var notificationTestMessage by remember { mutableStateOf<String?>(null) }
    val notificationPrefs = remember(context) { context.getSharedPreferences("notification_settings", android.content.Context.MODE_PRIVATE) }
    var notificationsEnabled by remember { mutableStateOf(notificationPrefs.getBoolean("timer_notifications_enabled", false)) }
    var notificationPermissionGranted by remember { mutableStateOf(TimerNotificationHelper.hasPermission(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        notificationsEnabled = granted
        notificationPrefs.edit().putBoolean("timer_notifications_enabled", granted).apply()
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphonePermissionGranted = granted
        if (granted) {
            microphoneError = null
            microphoneEnabled = true
        } else {
            microphoneEnabled = false
            microphoneError = "Microphone permission was denied. Allow it in Android app permissions to use this trigger."
        }
    }

    // Covering the sensor toggles the selected clock: running clocks pause, paused clocks resume.
    val latestProximityAction by rememberUpdatedState<() -> Unit>({
        when (proximityTarget) {
            ProximityTarget.TIMER -> {
                if (timerRunning) {
                    timerRunning = false
                } else {
                    if (remainingSeconds == 0) {
                        remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    }
                    timerRunning = remainingSeconds > 0
                }
            }
            ProximityTarget.STOPWATCH -> stopwatchRunning = !stopwatchRunning
            ProximityTarget.BOTH -> {
                if (timerRunning) {
                    timerRunning = false
                } else {
                    if (remainingSeconds == 0) {
                        remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    }
                    timerRunning = remainingSeconds > 0
                }
                stopwatchRunning = !stopwatchRunning
            }
        }
    })
    val latestMicrophoneAction by rememberUpdatedState<() -> Unit>({
        when (microphoneTarget) {
            MicrophoneTarget.TIMER -> {
                if (timerRunning) timerRunning = false else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
            }
            MicrophoneTarget.STOPWATCH -> stopwatchRunning = !stopwatchRunning
            MicrophoneTarget.BOTH -> {
                if (timerRunning) timerRunning = false else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
                stopwatchRunning = !stopwatchRunning
            }
        }
    })

    DisposableEffect(context, proximityEnabled) {
        if (!proximityEnabled) {
            onDispose { }
        } else {
            val controller = ProximitySensorController(
                context = context,
                onCovered = { latestProximityAction() },
                onAvailabilityChanged = { proximityAvailable = it }
            )
            controller.start()
            onDispose { controller.stop() }
        }
    }

    DisposableEffect(context, microphoneEnabled, microphonePermissionGranted, microphoneMode) {
        if (!microphoneEnabled || !microphonePermissionGranted) {
            onDispose { }
        } else {
            val controller = MicrophoneTapController(
                context = context,
                onTripleTap = { latestMicrophoneAction() },
                onError = { error -> microphoneError = error; microphoneEnabled = false },
                triggerMode = if (microphoneMode == MicrophoneMode.RECORDED_SOUND) MicrophoneTriggerMode.RECORDED_SOUND else MicrophoneTriggerMode.TONE,
                initialReferenceIntensity = referenceIntensity,
                onReferenceIntensityChanged = { intensity ->
                    referenceIntensity = intensity
                    microphonePrefs.edit().putFloat("reference_intensity", intensity.toFloat()).apply()
                    microphoneMessage = "Reference sound saved. Matching intensity window: ±70%."
                    microphoneError = null
                }
            )
            microphoneController = controller
            controller.start()
            onDispose {
                controller.stop()
                if (microphoneController === controller) microphoneController = null
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            delay(1000)
        }
    }

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
            remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
            if (remainingSeconds == 0) {
                timerRunning = false
                if (notificationsEnabled && notificationPermissionGranted) {
                    TimerNotificationHelper.showTimerComplete(context)
                }
            }
        }
    }

    LaunchedEffect(stopwatchRunning) {
        while (stopwatchRunning) {
            delay(50)
            stopwatchMillis += 50
        }
    }

    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Scaffold(
                containerColor = colors.background,
                topBar = {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                        Text(
                            "SENSOR TIMER",
                            color = colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("Clock", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(currentTime, fontSize = 22.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
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
                            proximityAvailable = proximityAvailable,
                            proximityTarget = proximityTarget,
                            onProximityTargetChanged = { proximityTarget = it },
                            microphoneEnabled = microphoneEnabled,
                            onMicrophoneChanged = { enabled ->
                                if (enabled) {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (granted) { microphonePermissionGranted = true; microphoneEnabled = true }
                                    else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else { microphoneEnabled = false }
                            },
                            microphonePermissionGranted = microphonePermissionGranted,
                            microphoneTarget = microphoneTarget,
                            onMicrophoneTargetChanged = { microphoneTarget = it },
                            microphoneMode = microphoneMode,
                            onMicrophoneModeChanged = { microphoneMode = it },
                            microphoneError = microphoneError,
                            microphoneMessage = microphoneMessage,
                            referenceIntensitySaved = referenceIntensity > 100.0,
                            onRecordReferenceSound = {
                                microphoneMessage = "Recording reference for 2 seconds — make the trigger sound now."
                                microphoneError = null
                                microphoneController?.recordReferenceSound()
                            },
                            notificationsEnabled = notificationsEnabled,
                            notificationPermissionGranted = notificationPermissionGranted,
                            onTestNotification = {
                                val shown = TimerNotificationHelper.showTimerComplete(context, test = true)
                                notificationTestMessage = if (shown) "Test notification sent. Check your notification shade." else "Notification blocked. Check Android notification permission and the Timer alerts channel in system settings."
                            },
                            onOpenNotificationSettings = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            notificationTestMessage = notificationTestMessage,
                            onNotificationsChanged = { enabled ->
                                if (!enabled) {
                                    notificationsEnabled = false
                                    notificationPrefs.edit().putBoolean("timer_notifications_enabled", false).apply()
                                } else if (TimerNotificationHelper.hasPermission(context)) {
                                    notificationPermissionGranted = true
                                    notificationsEnabled = true
                                    notificationPrefs.edit().putBoolean("timer_notifications_enabled", true).apply()
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
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
        "Use the proximity setting to choose whether covering the sensor controls the timer, stopwatch, or both.",
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
    proximityAvailable: Boolean,
    proximityTarget: ProximityTarget,
    onProximityTargetChanged: (ProximityTarget) -> Unit,
    microphoneEnabled: Boolean,
    onMicrophoneChanged: (Boolean) -> Unit,
    microphonePermissionGranted: Boolean,
    microphoneTarget: MicrophoneTarget,
    onMicrophoneTargetChanged: (MicrophoneTarget) -> Unit,
    microphoneMode: MicrophoneMode,
    onMicrophoneModeChanged: (MicrophoneMode) -> Unit,
    microphoneError: String?,
    microphoneMessage: String?,
    referenceIntensitySaved: Boolean,
    onRecordReferenceSound: () -> Unit,
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    onTestNotification: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    notificationTestMessage: String?,
    onNotificationsChanged: (Boolean) -> Unit
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
            Spacer(Modifier.height(16.dp))
            Text("Activate proximity for", fontWeight = FontWeight.SemiBold)
            var targetMenuExpanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { targetMenuExpanded = true }, enabled = proximityEnabled) {
                Text(proximityTarget.label)
            }
            DropdownMenu(
                expanded = targetMenuExpanded,
                onDismissRequest = { targetMenuExpanded = false }
            ) {
                ProximityTarget.entries.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        onClick = {
                            onProximityTargetChanged(target)
                            targetMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Cover the sensor to start or pause the selected clock. Cover it again to resume.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Microphone", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (microphonePermissionGranted) "Uses the microphone to detect the selected sound trigger."
                else "Microphone permission is needed to detect sound triggers.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (microphoneEnabled) "Enabled" else "Disabled", modifier = Modifier.weight(1f))
                Button(onClick = { onMicrophoneChanged(!microphoneEnabled) }) {
                    Text(if (microphoneEnabled) "Turn off" else "Turn on")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Activate by", fontWeight = FontWeight.SemiBold)
            var microphoneModeMenuExpanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { microphoneModeMenuExpanded = true }, enabled = microphoneEnabled) {
                Text(microphoneMode.label)
            }
            DropdownMenu(
                expanded = microphoneModeMenuExpanded,
                onDismissRequest = { microphoneModeMenuExpanded = false }
            ) {
                MicrophoneMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            onMicrophoneModeChanged(mode)
                            microphoneModeMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(microphoneMode.description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            if (microphoneMode == MicrophoneMode.RECORDED_SOUND) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRecordReferenceSound, enabled = microphoneEnabled) {
                    Text(if (referenceIntensitySaved) "Record sound again" else "Record trigger sound")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (referenceIntensitySaved) "A reference intensity is saved. Tap to record a new reference; make the sound for the full 2 seconds." else "Turn on the microphone, tap Record trigger sound, then make your chosen sound for 2 seconds.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (microphoneMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(microphoneMessage, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Activate microphone for", fontWeight = FontWeight.SemiBold)
            var microphoneMenuExpanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { microphoneMenuExpanded = true }, enabled = microphoneEnabled) {
                Text(microphoneTarget.label)
            }
            DropdownMenu(
                expanded = microphoneMenuExpanded,
                onDismissRequest = { microphoneMenuExpanded = false }
            ) {
                MicrophoneTarget.entries.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        onClick = {
                            onMicrophoneTargetChanged(target)
                            microphoneMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (microphoneMode == MicrophoneMode.RECORDED_SOUND)
                    "When listening, the trigger fires when the microphone RMS intensity enters 30%–170% of the saved reference intensity. Background noise and microphone distance can affect matching."
                else
                    "Play music or a tone aloud near the phone. The trigger uses microphone sound level, not the phone's internal audio stream; loud environmental sounds may also trigger it.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (microphoneError != null) {
                Spacer(Modifier.height(8.dp))
                Text(microphoneError, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Notification settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Show a notification when the countdown reaches zero.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (notificationsEnabled) "Timer alerts enabled" else "Timer alerts disabled")
                    Text(
                        if (notificationPermissionGranted) "Notification permission is available."
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Android notification permission is required."
                        else "Notifications are unavailable.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Button(onClick = { onNotificationsChanged(!notificationsEnabled) }) {
                    Text(if (notificationsEnabled) "Turn off" else "Turn on")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTestNotification) { Text("Test notification") }
                Button(onClick = onOpenNotificationSettings) { Text("System settings") }
            }
            if (notificationTestMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(notificationTestMessage, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Accelerometer and camera triggers will be added in later steps.")
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
