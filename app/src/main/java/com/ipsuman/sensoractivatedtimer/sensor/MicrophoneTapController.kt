package com.ipsuman.sensoractivatedtimer.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * RECORDED_SOUND compares live microphone RMS intensity with a user-recorded
 * reference intensity. TONE remains a sustained-loud-sound detector.
 */
enum class MicrophoneTriggerMode {
    RECORDED_SOUND, TONE
}

class MicrophoneTapController(
    private val context: Context,
    private val onTripleTap: () -> Unit,
    private val onError: (String) -> Unit = {},
    private val triggerMode: MicrophoneTriggerMode = MicrophoneTriggerMode.RECORDED_SOUND,
    initialReferenceIntensity: Double = 0.0,
    private val onReferenceIntensityChanged: (Double) -> Unit = {}
) {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var referenceIntensity = initialReferenceIntensity
    @Volatile private var calibrationRequested = false

    /** Record ambient sound intensity for 2 seconds while the microphone listener is active. */
    fun recordReferenceSound() {
        if (!running) {
            mainHandler.post { onError("Turn on the microphone trigger before recording a reference sound.") }
            return
        }
        calibrationRequested = true
    }

    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            mainHandler.post { onError("Microphone permission is required.") }
            return
        }

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            mainHandler.post { onError("Microphone recording is unavailable on this device.") }
            return
        }

        try {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 2048)
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                mainHandler.post { onError("Could not initialize microphone recording.") }
                return
            }
            recorder = audioRecord
            running = true
            audioRecord.startRecording()
            worker = Thread({
                val buffer = ShortArray(1024)
                var noiseFloor = 500.0
                var aboveThreshold = false
                var toneStartedAt = 0L
                var toneTriggered = false
                var lastLoudAt = 0L
                var calibrationStartedAt = 0L
                var calibrationSum = 0.0
                var calibrationSamples = 0
                try {
                    while (running) {
                        val count = audioRecord.read(buffer, 0, buffer.size)
                        if (count <= 0) continue
                        var sumSquares = 0.0
                        for (i in 0 until count) {
                            val value = buffer[i].toDouble()
                            sumSquares += value * value
                        }
                        val rms = sqrt(sumSquares / count)
                        val threshold = maxOf(1800.0, noiseFloor * 2.8)
                        val now = System.currentTimeMillis()

                        if (calibrationRequested) {
                            if (calibrationStartedAt == 0L) {
                                calibrationStartedAt = now
                                calibrationSum = 0.0
                                calibrationSamples = 0
                            }
                            if (rms > 100.0) {
                                calibrationSum += rms
                                calibrationSamples++
                            }
                            if (now - calibrationStartedAt >= 2000L) {
                                calibrationRequested = false
                                calibrationStartedAt = 0L
                                if (calibrationSamples >= 5) {
                                    referenceIntensity = calibrationSum / calibrationSamples
                                    val measured = referenceIntensity
                                    mainHandler.post { onReferenceIntensityChanged(measured) }
                                } else {
                                    mainHandler.post { onError("The reference sound was too quiet. Try recording it again closer to the microphone.") }
                                }
                            }
                            continue
                        }

                        when (triggerMode) {
                            MicrophoneTriggerMode.RECORDED_SOUND -> {
                                val reference = referenceIntensity
                                // ±70% intensity window: 30% to 170% of the recorded RMS.
                                val lowerBound = reference * 0.30
                                val upperBound = reference * 1.70
                                val matchesReference = reference > 100.0 && rms in lowerBound..upperBound
                                if (matchesReference && !aboveThreshold) {
                                    aboveThreshold = true
                                    mainHandler.post { if (running) onTripleTap() }
                                } else if (!matchesReference) {
                                    aboveThreshold = false
                                }
                            }
                            MicrophoneTriggerMode.TONE -> {
                                // Require sustained sound, trigger once per sound episode, and
                                // re-arm after at least 400 ms without a loud signal.
                                if (rms >= threshold) {
                                    lastLoudAt = now
                                    if (toneStartedAt == 0L) toneStartedAt = now
                                    if (!toneTriggered && now - toneStartedAt >= 250L) {
                                        toneTriggered = true
                                        mainHandler.post { if (running) onTripleTap() }
                                    }
                                } else if (rms < threshold * 0.65) {
                                    if (lastLoudAt != 0L && now - lastLoudAt >= 400L) {
                                        toneStartedAt = 0L
                                        toneTriggered = false
                                    }
                                }
                            }
                        }

                        if (rms < threshold * 0.55) {
                            noiseFloor = noiseFloor * 0.97 + rms * 0.03
                        }
                    }
                } catch (_: SecurityException) {
                    mainHandler.post { onError("Microphone permission was revoked.") }
                } catch (_: IllegalStateException) {
                    if (running) mainHandler.post { onError("Microphone recording stopped unexpectedly.") }
                } finally {
                    try { audioRecord.stop() } catch (_: IllegalStateException) { }
                    audioRecord.release()
                }
            }, "MicrophoneTapDetector").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            running = false
            recorder = null
            mainHandler.post { onError("Unable to start microphone: ${e.message ?: "unknown error"}") }
        }
    }

    fun stop() {
        running = false
        try { recorder?.stop() } catch (_: IllegalStateException) { }
        recorder = null
        worker = null
    }
}
