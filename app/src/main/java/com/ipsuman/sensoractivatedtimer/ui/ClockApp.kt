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
        mutableStateOf(VolumeGestureTarget.entries.getOrElse(volumeGesturePrefs.getInt("target", 0)) { VolumeGestureTarget.TIMER })
    }
    var selectedTab by remember { mutableStateOf(AppTab.TIMER) }
    val clockState = remember { ClockAppState() }
    var currentTime by clockState.currentTime
    var durationInput by clockState.durationInput
    var remainingSeconds by clockState.remainingSeconds
    var timerRunning by clockState.timerRunning
    var stopwatchRunning by clockState.stopwatchRunning
    var stopwatchMillis by clockState.stopwatchMillis
    var proximityAvailable by remember { mutableStateOf(true) }
    var proximityDistanceCm by remember { mutableStateOf<Float?>(null) }
    var microphoneLevel by remember { mutableStateOf(0f) }
    var microphonePitchHz by remember { mutableIntStateOf(0) }
    var microphoneHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var microphoneSpectrum by remember { mutableStateOf<List<Float>>(emptyList()) }
    var microphoneSpectrumMode by remember { mutableStateOf(false) }
    var proximityEnabled by remember { mutableStateOf(true) }
    var proximityTarget by remember { mutableStateOf(ProximityTarget.TIMER) }
    var accelerometerAvailable by remember { mutableStateOf(true) }
    val accelerometerPrefs = remember(context) { context.getSharedPreferences("accelerometer_settings", android.content.Context.MODE_PRIVATE) }
    var accelerometerEnabled by remember { mutableStateOf(accelerometerPrefs.getBoolean("enabled", false)) }
    var accelerometerTarget by remember { mutableStateOf(AccelerometerTarget.entries.getOrElse(accelerometerPrefs.getInt("target", 0)) { AccelerometerTarget.TIMER }) }
    var accelerometerSensitivity by remember { mutableIntStateOf(accelerometerPrefs.getInt("sensitivity", 5).coerceIn(1, 10)) }
    var microphoneEnabled by remember { mutableStateOf(false) }
    var microphonePermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var microphoneTarget by remember { mutableStateOf(MicrophoneTarget.TIMER) }
    var microphoneMode by remember { mutableStateOf(MicrophoneMode.RECORDED_SOUND) }
    var toneHoldEnabled by remember { mutableStateOf(false) }
    var toneHoldWasActive by remember { mutableStateOf(false) }
    var toneMinimumIntensity by remember { mutableIntStateOf(1800) }
    var microphoneError by remember { mutableStateOf<String?>(null) }
    var microphoneMessage by remember { mutableStateOf<String?>(null) }
    var microphoneController by remember { mutableStateOf<MicrophoneTapController?>(null) }
    val microphonePrefs = remember(context) { context.getSharedPreferences("microphone_settings", android.content.Context.MODE_PRIVATE) }
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
    val latestAccelerometerAction by rememberUpdatedState<() -> Unit>({
        when (accelerometerTarget) {
            AccelerometerTarget.TIMER -> {
                if (timerRunning) timerRunning = false else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
                    timerRunning = remainingSeconds > 0
                }
            }
            AccelerometerTarget.STOPWATCH -> stopwatchRunning = !stopwatchRunning
            AccelerometerTarget.BOTH -> {
                if (timerRunning) timerRunning = false else {
                    if (remainingSeconds == 0) remainingSeconds = (durationInput.toIntOrNull() ?: 0).coerceIn(1, 24 * 60 * 60)
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
        if (active) {
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
            }
        } else if (toneHoldWasActive) {
            toneHoldWasActive = false
            when (microphoneTarget) {
                MicrophoneTarget.TIMER -> timerRunning = false
                MicrophoneTarget.STOPWATCH -> stopwatchRunning = false
                MicrophoneTarget.BOTH -> {
                    timerRunning = false
                    stopwatchRunning = false
                }
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
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
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
                            onReset = { timerRunning = false; remainingSeconds = 0 }
                            )
                        }
                        AppTab.STOPWATCH -> Column {
                            SensorStatusPanel(microphoneEnabled && microphonePermissionGranted, microphoneHistory, microphoneSpectrum, microphoneSpectrumMode, { microphoneSpectrumMode = !microphoneSpectrumMode }, microphoneLevel, microphonePitchHz, proximityEnabled && proximityAvailable, proximityDistanceCm, accelerometerEnabled && accelerometerAvailable)
                            StopwatchScreen(
                            elapsedMillis = stopwatchMillis,
                            running = stopwatchRunning,
                            onToggle = { stopwatchRunning = !stopwatchRunning },
                            onReset = { stopwatchRunning = false; stopwatchMillis = 0L }
                            )
                        }
                        AppTab.SETTINGS -> SettingsScreen(
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
