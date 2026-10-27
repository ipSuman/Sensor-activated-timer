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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipsuman.sensoractivatedtimer.TimerNotificationHelper
import com.ipsuman.sensoractivatedtimer.sensor.MicrophoneTapController

private val DarkColors = darkColorScheme()
private val LightColors = lightColorScheme()

@Composable
fun ClockApp(
    onVolumeGestureHandlerChanged: ((() -> Unit)?) -> Unit,
    onVolumeGesturePauseHandlerChanged: ((() -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    val volumeGesturePrefs = remember(context) { context.getSharedPreferences("button_gesture_settings", android.content.Context.MODE_PRIVATE) }
    var volumeGestureEnabled by remember { mutableStateOf(volumeGesturePrefs.getBoolean("enabled", false)) }
    var volumeGestureTarget by remember {
        mutableStateOf(VolumeGestureTarget.entries.getOrElse(volumeGesturePrefs.getInt("target", VolumeGestureTarget.ACTIVE_WINDOW.ordinal)) { VolumeGestureTarget.ACTIVE_WINDOW })
    }
    var selectedTab by remember { mutableStateOf(AppTab.TIMER) }
    val sensorActionPrefs = remember(context) { context.getSharedPreferences("sensor_action_settings", android.content.Context.MODE_PRIVATE) }
    var resetOnSensorTrigger by remember { mutableStateOf(sensorActionPrefs.getBoolean("reset_on_trigger", false)) }
    var sensorListeningIntervalMs by remember { mutableIntStateOf(sensorActionPrefs.getInt("listening_interval_ms", 100).coerceIn(10, 1000)) }
    var lastSensorInputAt by remember { mutableLongStateOf(0L) }
    var historyEntries by remember(context) { mutableStateOf(loadClockHistory(context)) }
    fun recordHistory(kind: String, reading: String) {
        if (reading.isBlank()) return
        historyEntries = (listOf(ClockHistoryEntry(System.currentTimeMillis(), kind, reading)) + historyEntries).take(200)
        saveClockHistory(context, historyEntries)
    }
    fun deleteHistoryDate(date: String) {
        historyEntries = historyEntries.filterNot { it.dateLabel() == date }
        saveClockHistory(context, historyEntries)
    }
    fun deleteHistoryEntry(entry: ClockHistoryEntry) {
        historyEntries = historyEntries.filterNot { it.timestamp == entry.timestamp && it.kind == entry.kind && it.reading == entry.reading }
        saveClockHistory(context, historyEntries)
    }
    val clockState = remember { ClockAppState() }
    var currentTime by clockState.currentTime
    var durationInput by clockState.durationInput
    var remainingSeconds by clockState.remainingSeconds
    var timerRunning by clockState.timerRunning
    var stopwatchRunning by clockState.stopwatchRunning
    var stopwatchMillis by clockState.stopwatchMillis
    fun recordTimerResetReading() {
        if (remainingSeconds > 0) recordHistory("Timer", formatTime(remainingSeconds))
    }
    fun recordStopwatchResetReading() {
        if (stopwatchMillis > 0L) recordHistory("Stopwatch", formatStopwatch(stopwatchMillis))
    }
    var proximityAvailable by remember { mutableStateOf(true) }
    var proximityDistanceCm by remember { mutableStateOf<Float?>(null) }
    var microphoneLevel by remember { mutableStateOf(0f) }
    var microphonePitchHz by remember { mutableIntStateOf(0) }
    var microphoneHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var microphoneSpectrum by remember { mutableStateOf<List<Float>>(emptyList()) }
    var microphoneSpectrumMode by remember { mutableStateOf(false) }
    var proximityEnabled by remember { mutableStateOf(true) }
    val proximityPrefs = remember(context) { context.getSharedPreferences("proximity_settings", android.content.Context.MODE_PRIVATE) }
    var proximityTarget by remember { mutableStateOf(ProximityTarget.entries.getOrElse(proximityPrefs.getInt("target", ProximityTarget.ACTIVE_WINDOW.ordinal)) { ProximityTarget.ACTIVE_WINDOW }) }
    var accelerometerAvailable by remember { mutableStateOf(true) }
    val accelerometerPrefs = remember(context) { context.getSharedPreferences("accelerometer_settings", android.content.Context.MODE_PRIVATE) }
    var accelerometerEnabled by remember { mutableStateOf(accelerometerPrefs.getBoolean("enabled", false)) }
    var accelerometerTarget by remember { mutableStateOf(AccelerometerTarget.entries.getOrElse(accelerometerPrefs.getInt("target", AccelerometerTarget.ACTIVE_WINDOW.ordinal)) { AccelerometerTarget.ACTIVE_WINDOW }) }
    var accelerometerSensitivity by remember { mutableIntStateOf(accelerometerPrefs.getInt("sensitivity", 5).coerceIn(1, 10)) }
    var microphoneEnabled by remember { mutableStateOf(false) }
    var microphonePermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var microphoneTarget by remember { mutableStateOf(MicrophoneTarget.ACTIVE_WINDOW) }
    var microphoneMode by remember { mutableStateOf(MicrophoneMode.RECORDED_SOUND) }
    var toneHoldEnabled by remember { mutableStateOf(false) }
    var toneHoldWasActive by remember { mutableStateOf(false) }
    var toneMinimumIntensity by remember { mutableIntStateOf(1800) }
    var microphoneError by remember { mutableStateOf<String?>(null) }
    var microphoneMessage by remember { mutableStateOf<String?>(null) }
    var microphoneController by remember { mutableStateOf<MicrophoneTapController?>(null) }
    val microphonePrefs = remember(context) { context.getSharedPreferences("microphone_settings", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(microphonePrefs) {
        microphoneTarget = MicrophoneTarget.entries.getOrElse(
            microphonePrefs.getInt("target", MicrophoneTarget.ACTIVE_WINDOW.ordinal)
        ) { MicrophoneTarget.ACTIVE_WINDOW }
    }
    LaunchedEffect(microphonePrefs) {
        toneHoldEnabled = microphonePrefs.getBoolean("tone_hold_timer", false)
        toneMinimumIntensity = microphonePrefs.getInt("tone_minimum_intensity", 1800).coerceIn(500, 12000)
    }
    var referenceIntensity by remember { mutableStateOf(microphonePrefs.getFloat("reference_intensity", 0f).toDouble()) }
    var referenceDurationMs by remember { mutableLongStateOf(microphonePrefs.getLong("reference_duration_ms", 0L)) }
    var intensityTolerance by remember { mutableIntStateOf(microphonePrefs.getInt("intensity_tolerance", 70)) }
    var durationTolerance by remember { mutableIntStateOf(microphonePrefs.getInt("duration_tolerance", 30)) }
    var pitchTolerance by remember { mutableIntStateOf(microphonePrefs.getInt("pitch_tolerance", 20).coerceIn(0, 100)) }
    var referencePitchHz by remember { mutableIntStateOf(microphonePrefs.getInt("reference_pitch_hz", 0).coerceAtLeast(0)) }
    var notificationTestMessage by remember { mutableStateOf<String?>(null) }
    val notificationPrefs = remember(context) { context.getSharedPreferences("notification_settings", android.content.Context.MODE_PRIVATE) }
    var notificationsEnabled by remember { mutableStateOf(notificationPrefs.getBoolean("timer_notifications_enabled", false)) }
    var notificationPermissionGranted by remember { mutableStateOf(TimerNotificationHelper.hasPermission(context)) }

    fun triggerActiveWindowClock(reset: Boolean) {
        when (selectedTab) {
            AppTab.TIMER -> {
                if (reset && timerRunning) {
                    // Save the latest countdown reading before resetting it.
                    recordTimerResetReading()
                    timerRunning = false
                    remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                } else if (timerRunning) {
                    timerRunning = false
                } else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
            }
            AppTab.STOPWATCH -> {
                if (reset && stopwatchRunning) {
                    // Save the latest elapsed reading before resetting the stopwatch.
                    recordStopwatchResetReading()
                    stopwatchRunning = false
                    stopwatchMillis = 0L
                } else {
                    stopwatchRunning = !stopwatchRunning
                }
            }
            AppTab.SETTINGS -> Unit
        }
    }
    fun pauseActiveWindowClock() {
        when (selectedTab) {
            AppTab.TIMER -> timerRunning = false
            AppTab.STOPWATCH -> stopwatchRunning = false
            AppTab.SETTINGS -> Unit
        }
    }

    val latestVolumeGestureAction by rememberUpdatedState<() -> Unit>({
        when (volumeGestureTarget) {
            VolumeGestureTarget.TIMER -> {
                if (timerRunning) timerRunning = false else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
            }
            VolumeGestureTarget.STOPWATCH -> stopwatchRunning = !stopwatchRunning
            VolumeGestureTarget.BOTH -> {
                if (timerRunning) timerRunning = false else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
                stopwatchRunning = !stopwatchRunning
            }
            VolumeGestureTarget.ACTIVE_WINDOW -> triggerActiveWindowClock(resetOnSensorTrigger)
        }
    })

    val latestVolumeGesturePauseAction by rememberUpdatedState<() -> Unit>({
        when (volumeGestureTarget) {
            VolumeGestureTarget.TIMER -> if (timerRunning) timerRunning = false
            VolumeGestureTarget.STOPWATCH -> if (stopwatchRunning) stopwatchRunning = false
            VolumeGestureTarget.BOTH -> {
                timerRunning = false
                stopwatchRunning = false
            }
            VolumeGestureTarget.ACTIVE_WINDOW -> pauseActiveWindowClock()
        }
    })

    DisposableEffect(Unit) {
        onVolumeGestureHandlerChanged { latestVolumeGestureAction() }
        onVolumeGesturePauseHandlerChanged { latestVolumeGesturePauseAction() }
        onDispose {
            onVolumeGestureHandlerChanged(null)
            onVolumeGesturePauseHandlerChanged(null)
        }
    }

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

    fun triggerClock(target: ClockTriggerTarget) {
        // Apply one shared minimum interval to proximity, accelerometer and microphone triggers.
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastSensorInputAt != 0L && now - lastSensorInputAt < sensorListeningIntervalMs.toLong()) return
        lastSensorInputAt = now
        if (target == ClockTriggerTarget.ACTIVE_WINDOW) {
            triggerActiveWindowClock(resetOnSensorTrigger)
            return
        }
        val targets = when (target) {
            ClockTriggerTarget.TIMER -> listOf(ClockTriggerTarget.TIMER)
            ClockTriggerTarget.STOPWATCH -> listOf(ClockTriggerTarget.STOPWATCH)
            ClockTriggerTarget.BOTH -> listOf(ClockTriggerTarget.TIMER, ClockTriggerTarget.STOPWATCH)
            ClockTriggerTarget.ACTIVE_WINDOW -> emptyList()
        }
        targets.forEach { clock ->
            when (clock) {
                ClockTriggerTarget.TIMER -> {
                    if (resetOnSensorTrigger && timerRunning) {
                        // Save the latest countdown reading before resetting it.
                        recordTimerResetReading()
                        timerRunning = false
                        remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    } else if (timerRunning) {
                        timerRunning = false
                    } else {
                        if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                        timerRunning = remainingSeconds > 0
                    }
                }
                ClockTriggerTarget.STOPWATCH -> {
                    if (resetOnSensorTrigger && stopwatchRunning) {
                        // Save the latest elapsed reading before resetting the stopwatch.
                        recordStopwatchResetReading()
                        stopwatchRunning = false
                        stopwatchMillis = 0L
                    } else {
                        stopwatchRunning = !stopwatchRunning
                    }
                }
                ClockTriggerTarget.BOTH, ClockTriggerTarget.ACTIVE_WINDOW -> Unit
            }
        }
    }
    val latestProximityAction by rememberUpdatedState<() -> Unit>({ triggerClock(proximityTarget.toClockTriggerTarget()) })
    val latestAccelerometerAction by rememberUpdatedState<() -> Unit>({ triggerClock(accelerometerTarget.toClockTriggerTarget()) })
    val latestMicrophoneAction by rememberUpdatedState<() -> Unit>({ triggerClock(microphoneTarget.toClockTriggerTarget()) })

    ProximityAndAccelerometerEffects(
        context = context,
        proximityEnabled = proximityEnabled,
        accelerometerEnabled = accelerometerEnabled,
        accelerometerSensitivity = accelerometerSensitivity,
        onProximityAction = { latestProximityAction() },
        onProximityAvailabilityChanged = { proximityAvailable = it },
        onProximityDistanceChanged = { proximityDistanceCm = it },
        onAccelerometerAction = { latestAccelerometerAction() },
        onAccelerometerAvailabilityChanged = { accelerometerAvailable = it }
    )

    val latestToneHoldAction by rememberUpdatedState<(Boolean) -> Unit>({ active ->
        if (resetOnSensorTrigger) {
            if (active && !toneHoldWasActive) triggerClock(microphoneTarget.toClockTriggerTarget())
            toneHoldWasActive = active
        } else if (active) {
            toneHoldWasActive = true
            when (microphoneTarget) {
                MicrophoneTarget.TIMER -> {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
                MicrophoneTarget.STOPWATCH -> stopwatchRunning = true
                MicrophoneTarget.BOTH -> {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                    stopwatchRunning = true
                }
                MicrophoneTarget.ACTIVE_WINDOW -> when (selectedTab) {
                    AppTab.TIMER -> {
                        if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                        timerRunning = remainingSeconds > 0
                    }
                    AppTab.STOPWATCH -> stopwatchRunning = true
                    AppTab.SETTINGS -> Unit
                }
            }
        } else if (toneHoldWasActive) {
            toneHoldWasActive = false
            when (microphoneTarget) {
                MicrophoneTarget.TIMER -> timerRunning = false
                MicrophoneTarget.STOPWATCH -> stopwatchRunning = false
                MicrophoneTarget.BOTH -> { timerRunning = false; stopwatchRunning = false }
                MicrophoneTarget.ACTIVE_WINDOW -> pauseActiveWindowClock()
            }
        }
    })

    ClockMicrophoneEffects(
        context = context,
        enabled = microphoneEnabled,
        permissionGranted = microphonePermissionGranted,
        mode = microphoneMode,
        toneHoldEnabled = toneHoldEnabled,
        toneMinimumIntensity = toneMinimumIntensity,
        referenceIntensity = referenceIntensity,
        referenceDurationMs = referenceDurationMs,
        referencePitchHz = referencePitchHz,
        intensityTolerance = intensityTolerance,
        durationTolerance = durationTolerance,
        pitchTolerance = pitchTolerance,
        onTripleTap = { latestMicrophoneAction() },
        onError = { error -> microphoneError = error; microphoneEnabled = false },
        onToneActivityChanged = { active -> latestToneHoldAction(active) },
        onAudioMetrics = { intensity, pitchHz ->
            microphoneLevel = intensity.toFloat()
            microphonePitchHz = pitchHz
            microphoneHistory = (microphoneHistory + intensity.toFloat()).takeLast(48)
        },
        onSpectrumMetrics = { bins -> microphoneSpectrum = bins },
        onReferenceSoundChanged = { intensity, durationMs, pitchHz ->
            referenceIntensity = intensity
            referenceDurationMs = durationMs
            referencePitchHz = pitchHz
            microphonePrefs.edit()
                .putFloat("reference_intensity", intensity.toFloat())
                .putLong("reference_duration_ms", durationMs)
                .putInt("reference_pitch_hz", pitchHz)
                .apply()
            microphoneMessage = "Reference saved: ${durationMs} ms sound duration."
            microphoneError = null
        },
        onControllerChanged = { microphoneController = it }
    )

    ClockTimeEffect(onTimeChanged = { currentTime = it })

    ClockStopwatchEffect(
        running = stopwatchRunning,
        onTick = { deltaMillis -> stopwatchMillis += deltaMillis }
    )

    ClockTimerEffects(
        context = context,
        timerRunning = timerRunning,
        remainingSeconds = remainingSeconds,
        onRemainingSecondsChanged = { remainingSeconds = it },
        onTimerRunningChanged = { timerRunning = it },
        stopwatchRunning = stopwatchRunning,
        stopwatchMillis = stopwatchMillis,
        notificationsEnabled = notificationsEnabled,
        notificationPermissionGranted = notificationPermissionGranted
    )

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
                    modifier = Modifier.fillMaxSize().padding(padding).pointerInput(selectedTab) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (totalDrag < -80f) selectedTab = AppTab.entries[(selectedTab.ordinal + 1).coerceAtMost(AppTab.entries.lastIndex)]
                                else if (totalDrag > 80f) selectedTab = AppTab.entries[(selectedTab.ordinal - 1).coerceAtLeast(0)]
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f }
                        )
                    }.verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    when (selectedTab) {
                        AppTab.TIMER -> Column {
                            SensorStatusPanel(microphoneEnabled && microphonePermissionGranted, microphoneHistory, microphoneSpectrum, microphoneSpectrumMode, { microphoneSpectrumMode = !microphoneSpectrumMode }, microphoneLevel, microphonePitchHz, proximityEnabled && proximityAvailable, proximityDistanceCm, accelerometerEnabled && accelerometerAvailable)
                            TimerScreen(
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
                            onReset = {
                                timerRunning = false
                                if (remainingSeconds > 0) recordHistory("Timer", formatTime(remainingSeconds))
                                remainingSeconds = 0
                            }
                            )
                            ClockHistoryPanel(historyEntries.filter { it.kind == "Timer" }, onDeleteDate = ::deleteHistoryDate, onDeleteEntry = ::deleteHistoryEntry)
                        }
                        AppTab.STOPWATCH -> Column {
                            SensorStatusPanel(microphoneEnabled && microphonePermissionGranted, microphoneHistory, microphoneSpectrum, microphoneSpectrumMode, { microphoneSpectrumMode = !microphoneSpectrumMode }, microphoneLevel, microphonePitchHz, proximityEnabled && proximityAvailable, proximityDistanceCm, accelerometerEnabled && accelerometerAvailable)
                            StopwatchScreen(
                            elapsedMillis = stopwatchMillis,
                            running = stopwatchRunning,
                            sensorListeningIntervalMs = sensorListeningIntervalMs,
                            onSensorListeningIntervalChanged = { value ->
                                sensorListeningIntervalMs = value.coerceIn(10, 1000)
                                sensorActionPrefs.edit().putInt("listening_interval_ms", sensorListeningIntervalMs).apply()
                            },
                            onToggle = { stopwatchRunning = !stopwatchRunning },
                            onReset = {
                                stopwatchRunning = false
                                if (stopwatchMillis > 0L) recordHistory("Stopwatch", formatStopwatch(stopwatchMillis))
                                stopwatchMillis = 0L
                            }
                            )
                            ClockHistoryPanel(historyEntries.filter { it.kind == "Stopwatch" }, onDeleteDate = ::deleteHistoryDate, onDeleteEntry = ::deleteHistoryEntry)
                        }
                        AppTab.SETTINGS -> SettingsScreen(
                            resetOnSensorTrigger = resetOnSensorTrigger,
                            onResetOnSensorTriggerChanged = { enabled ->
                                resetOnSensorTrigger = enabled
                                sensorActionPrefs.edit().putBoolean("reset_on_trigger", enabled).apply()
                            },
                            volumeGestureEnabled = volumeGestureEnabled,
                            onVolumeGestureChanged = { enabled ->
                                volumeGestureEnabled = enabled
                                volumeGesturePrefs.edit().putBoolean("enabled", enabled).apply()
                            },
                            volumeGestureTarget = volumeGestureTarget,
                            onVolumeGestureTargetChanged = { target ->
                                volumeGestureTarget = target
                                volumeGesturePrefs.edit().putInt("target", target.ordinal).apply()
                            },
                            proximityEnabled = proximityEnabled,
                            onProximityChanged = { proximityEnabled = it },
                            proximityAvailable = proximityAvailable,
                            proximityTarget = proximityTarget,
                            accelerometerEnabled = accelerometerEnabled,
                            onAccelerometerChanged = { enabled -> accelerometerEnabled = enabled; accelerometerPrefs.edit().putBoolean("enabled", enabled).apply() },
                            accelerometerAvailable = accelerometerAvailable,
                            accelerometerTarget = accelerometerTarget,
                            onAccelerometerTargetChanged = { target -> accelerometerTarget = target; accelerometerPrefs.edit().putInt("target", target.ordinal).apply() },
                            accelerometerSensitivity = accelerometerSensitivity,
                            onAccelerometerSensitivityChanged = { value -> accelerometerSensitivity = value; accelerometerPrefs.edit().putInt("sensitivity", value).apply() },
                            onProximityTargetChanged = { proximityTarget = it; proximityPrefs.edit().putInt("target", it.ordinal).apply() },
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
                            onMicrophoneTargetChanged = { microphoneTarget = it; microphonePrefs.edit().putInt("target", it.ordinal).apply() },
                            microphoneMode = microphoneMode,
                            onMicrophoneModeChanged = { microphoneMode = it },
                            toneHoldEnabled = toneHoldEnabled,
                            onToneHoldChanged = { enabled ->
                                toneHoldEnabled = enabled
                                microphonePrefs.edit().putBoolean("tone_hold_timer", enabled).apply()
                            },
                                                        microphoneLevel = microphoneLevel,
                            toneMinimumIntensity = toneMinimumIntensity,
                            onToneMinimumIntensityChanged = { value ->
                                toneMinimumIntensity = value.coerceIn(500, 12000)
                                microphoneController?.updateToneMinimumIntensity(toneMinimumIntensity)
                                microphonePrefs.edit().putInt("tone_minimum_intensity", toneMinimumIntensity).apply()
                            },
                            microphoneError = microphoneError,
                            microphoneMessage = microphoneMessage,
                            referenceIntensitySaved = referenceIntensity > 100.0 && referenceDurationMs > 0L,
                            referencePitchHz = referencePitchHz,
                            intensityTolerance = intensityTolerance,
                            onIntensityToleranceChanged = { value ->
                                intensityTolerance = value
                                microphoneController?.updateTolerances(value, durationTolerance, pitchTolerance)
                                microphonePrefs.edit().putInt("intensity_tolerance", value).apply()
                            },
                            durationTolerance = durationTolerance,
                            pitchTolerance = pitchTolerance,
                            onPitchToleranceChanged = { value ->
                                pitchTolerance = value
                                microphoneController?.updateTolerances(intensityTolerance, durationTolerance, value)
                                microphonePrefs.edit().putInt("pitch_tolerance", value).apply()
                            },
                            onDurationToleranceChanged = { value ->
                                durationTolerance = value
                                microphoneController?.updateTolerances(intensityTolerance, value, pitchTolerance)
                                microphonePrefs.edit().putInt("duration_tolerance", value).apply()
                            },
                            referenceDurationMs = referenceDurationMs,
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
