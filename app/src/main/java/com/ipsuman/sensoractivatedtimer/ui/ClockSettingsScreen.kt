package com.ipsuman.sensoractivatedtimer.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun SettingsScreen(
    volumeGestureEnabled: Boolean,
    onVolumeGestureChanged: (Boolean) -> Unit,
    volumeGestureTarget: VolumeGestureTarget,
    onVolumeGestureTargetChanged: (VolumeGestureTarget) -> Unit,
    proximityEnabled: Boolean,
    onProximityChanged: (Boolean) -> Unit,
    proximityAvailable: Boolean,
    proximityTarget: ProximityTarget,
    onProximityTargetChanged: (ProximityTarget) -> Unit,
    accelerometerEnabled: Boolean,
    onAccelerometerChanged: (Boolean) -> Unit,
    accelerometerAvailable: Boolean,
    accelerometerTarget: AccelerometerTarget,
    onAccelerometerTargetChanged: (AccelerometerTarget) -> Unit,
    accelerometerSensitivity: Int,
    onAccelerometerSensitivityChanged: (Int) -> Unit,
    microphoneEnabled: Boolean,
    onMicrophoneChanged: (Boolean) -> Unit,
    microphonePermissionGranted: Boolean,
    microphoneTarget: MicrophoneTarget,
    onMicrophoneTargetChanged: (MicrophoneTarget) -> Unit,
    microphoneMode: MicrophoneMode,
    onMicrophoneModeChanged: (MicrophoneMode) -> Unit,
    toneHoldEnabled: Boolean,
    onToneHoldChanged: (Boolean) -> Unit,
    microphoneLevel: Float,
    toneMinimumIntensity: Int,
    onToneMinimumIntensityChanged: (Int) -> Unit,
    microphoneError: String?,
    microphoneMessage: String?,
    referenceIntensitySaved: Boolean,
    referencePitchHz: Int,
    intensityTolerance: Int,
    onIntensityToleranceChanged: (Int) -> Unit,
    durationTolerance: Int,
    pitchTolerance: Int,
    onPitchToleranceChanged: (Int) -> Unit,
    onDurationToleranceChanged: (Int) -> Unit,
    referenceDurationMs: Long,
    onRecordReferenceSound: () -> Unit,
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    onTestNotification: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    notificationTestMessage: String?,
    onNotificationsChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Text("SENSOR SETTINGS", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Button Gestures", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Press Volume Down three times within 0.7 seconds to trigger the selected clock.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (volumeGestureEnabled) "Enabled" else "Disabled", modifier = Modifier.weight(1f))
                Button(onClick = { onVolumeGestureChanged(!volumeGestureEnabled) }) {
                    Text(if (volumeGestureEnabled) "Turn off" else "Turn on")
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Trigger", fontWeight = FontWeight.SemiBold)
            var volumeGestureMenuExpanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { volumeGestureMenuExpanded = true }, enabled = volumeGestureEnabled) {
                Text(volumeGestureTarget.label)
            }
            DropdownMenu(
                expanded = volumeGestureMenuExpanded,
                onDismissRequest = { volumeGestureMenuExpanded = false }
            ) {
                VolumeGestureTarget.entries.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        onClick = {
                            onVolumeGestureTargetChanged(target)
                            volumeGestureMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (volumeGestureEnabled) "While enabled, Volume Down presses are reserved for this gesture and will not change media volume. Volume Up works normally."
                else "Turn this off to restore normal volume-button behavior.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                enabled = volumeGestureEnabled,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enable screen-off gesture in Accessibility settings") }
            Text(
                "Enable Sensor Timer's Volume Gesture service in Android Accessibility settings. Triple Volume Down pauses the selected running clock. Device restrictions may still affect screen-off key events.",
                fontSize = 12.sp,
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
            Text("Accelerometer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (accelerometerAvailable) "Shake the phone to start or pause the selected clock."
                else "This device does not report an accelerometer.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (accelerometerEnabled) "Enabled" else "Disabled", modifier = Modifier.weight(1f))
                Button(onClick = { onAccelerometerChanged(!accelerometerEnabled) }, enabled = accelerometerAvailable) {
                    Text(if (accelerometerEnabled) "Turn off" else "Turn on")
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Activate accelerometer for", fontWeight = FontWeight.SemiBold)
            var accelerometerMenuExpanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { accelerometerMenuExpanded = true }, enabled = accelerometerEnabled && accelerometerAvailable) {
                Text(accelerometerTarget.label)
            }
            DropdownMenu(
                expanded = accelerometerMenuExpanded,
                onDismissRequest = { accelerometerMenuExpanded = false }
            ) {
                AccelerometerTarget.entries.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        onClick = {
                            onAccelerometerTargetChanged(target)
                            accelerometerMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Sensitivity: $accelerometerSensitivity / 10", fontWeight = FontWeight.SemiBold)
            Slider(
                value = accelerometerSensitivity.toFloat(),
                onValueChange = { onAccelerometerSensitivityChanged(it.toInt().coerceIn(1, 10)) },
                valueRange = 1f..10f,
                steps = 8,
                enabled = accelerometerEnabled && accelerometerAvailable
            )
            Text(
                "Higher sensitivity detects gentler shakes; lower sensitivity requires a stronger shake. A short cooldown helps prevent repeated triggers.",
                fontSize = 12.sp,
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
            if (microphoneMode == MicrophoneMode.TONE) {
                Spacer(Modifier.height(12.dp))
                Text("Noise filter — minimum intensity: $toneMinimumIntensity RMS", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = toneMinimumIntensity.toFloat(),
                    onValueChange = { onToneMinimumIntensityChanged(it.toInt().coerceIn(500, 12000)) },
                    valueRange = 500f..12000f,
                    steps = 22,
                    enabled = microphoneEnabled
                )
                Text(
                    "Live intensity: ${microphoneLevel.toInt()} RMS. Raise the threshold to ignore quieter background noise; lower it to detect softer tones.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Run clock while tone plays", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hold the selected clock(s) while sound stays within the intensity threshold or saved reference tolerance.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (referenceIntensitySaved) "Reference intensity saved." else "Record a reference sound to enable intensity tolerance matching.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onRecordReferenceSound, enabled = microphoneEnabled) {
                        Text("Record reference intensity")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Intensity tolerance: ±$intensityTolerance%", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = intensityTolerance.toFloat(),
                        onValueChange = { onIntensityToleranceChanged(it.toInt().coerceIn(0, 100)) },
                        valueRange = 0f..100f,
                        steps = 9,
                        enabled = microphoneEnabled && microphoneMode == MicrophoneMode.TONE && referenceIntensitySaved
                    )
                    Text(
                        "With a saved reference, live intensity must stay within this tolerance band. Without one, the minimum intensity threshold above is used.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (toneHoldEnabled) "Enabled" else "Disabled", modifier = Modifier.weight(1f))
                        Button(
                            modifier = Modifier.padding(start = 12.dp),
                            onClick = { onToneHoldChanged(!toneHoldEnabled) },
                            enabled = microphoneEnabled && microphoneMode == MicrophoneMode.TONE
                        ) { Text(if (toneHoldEnabled) "Turn off" else "Turn on") }
                    }
                    if (toneHoldEnabled && microphoneMode != MicrophoneMode.TONE) {
                        Text("Select Tone / music above to use this option.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
            if (microphoneMode == MicrophoneMode.RECORDED_SOUND) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRecordReferenceSound, enabled = microphoneEnabled) {
                    Text(if (referenceIntensitySaved) "Record sound again" else "Record trigger sound")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (referenceIntensitySaved) "Reference saved (${referenceDurationMs} ms sound duration). Record again to replace it." else "Turn on the microphone, tap Record trigger sound, and make one clear sound during the 2-second recording window.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                Text("Intensity tolerance: ±$intensityTolerance%", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = intensityTolerance.toFloat(),
                    onValueChange = { onIntensityToleranceChanged(it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    steps = 9
                )
                Text("Lower tolerance is more selective; higher tolerance accepts a wider volume range.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Text("Pitch tolerance: ±$pitchTolerance%", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = pitchTolerance.toFloat(),
                    onValueChange = { onPitchToleranceChanged(it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    steps = 9,
                    enabled = microphoneEnabled
                )
                Text("When the recorded sound has a detectable pitch, the live pitch must fall within this percentage of the reference pitch. If no stable reference pitch is detected, pitch matching is skipped.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Text("Duration tolerance: ±$durationTolerance%", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = durationTolerance.toFloat(),
                    onValueChange = { onDurationToleranceChanged(it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    steps = 9
                )
                Text("The sound must last within this percentage of the recorded sound's duration.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
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
                    "A trigger is accepted only when both the sound intensity and its duration fall within the selected tolerances. Background noise and microphone distance can affect matching."
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
}
