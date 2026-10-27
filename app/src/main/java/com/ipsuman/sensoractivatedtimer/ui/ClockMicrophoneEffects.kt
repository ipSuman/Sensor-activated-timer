package com.ipsuman.sensoractivatedtimer.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.ipsuman.sensoractivatedtimer.sensor.MicrophoneTapController
import com.ipsuman.sensoractivatedtimer.sensor.MicrophoneTriggerMode

/**
 * Manages microphone controller startup and cleanup while keeping the screen
 * responsible for app state and user-facing messages.
 */
@Composable
internal fun ClockMicrophoneEffects(
    context: Context,
    enabled: Boolean,
    permissionGranted: Boolean,
    mode: MicrophoneMode,
    toneHoldEnabled: Boolean,
    toneMinimumIntensity: Int,
    referenceIntensity: Double,
    referenceDurationMs: Long,
    referencePitchHz: Int,
    intensityTolerance: Int,
    durationTolerance: Int,
    pitchTolerance: Int,
    onTripleTap: () -> Unit,
    onError: (String) -> Unit,
    onToneActivityChanged: (Boolean) -> Unit,
    onAudioMetrics: (Double, Int) -> Unit,
    onSpectrumMetrics: (List<Float>) -> Unit,
    onReferenceSoundChanged: (Double, Long, Int) -> Unit,
    onControllerChanged: (MicrophoneTapController?) -> Unit
) {
    DisposableEffect(context, enabled, permissionGranted, mode, toneHoldEnabled) {
        if (!enabled || !permissionGranted) {
            onControllerChanged(null)
            onDispose { }
        } else {
            val controller = MicrophoneTapController(
                context = context,
                onTripleTap = onTripleTap,
                onError = onError,
                triggerMode = if (mode == MicrophoneMode.RECORDED_SOUND) {
                    MicrophoneTriggerMode.RECORDED_SOUND
                } else {
                    MicrophoneTriggerMode.TONE
                },
                holdWhileToneActive = toneHoldEnabled && mode == MicrophoneMode.TONE,
                onToneActivityChanged = onToneActivityChanged,
                initialToneMinimumIntensity = toneMinimumIntensity,
                initialReferenceIntensity = referenceIntensity,
                initialReferenceDurationMs = referenceDurationMs,
                initialReferencePitchHz = referencePitchHz,
                intensityTolerancePercent = intensityTolerance,
                durationTolerancePercent = durationTolerance,
                pitchTolerancePercent = pitchTolerance,
                onAudioMetrics = onAudioMetrics,
                onSpectrumMetrics = onSpectrumMetrics,
                onReferenceSoundChanged = onReferenceSoundChanged
            )
            onControllerChanged(controller)
            controller.start()
            onDispose {
                controller.stop()
                onControllerChanged(null)
            }
        }
    }
}
