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
import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Detects three short, distinct loud acoustic peaks within 1.5 seconds.
 * This detects tap-like sounds near the microphone; it cannot verify that taps
 * physically happened on the back of the phone.
 */
class MicrophoneTapController(
    private val context: Context,
    private val onTripleTap: () -> Unit,
    private val onError: (String) -> Unit = {}
) {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
                val peaks = ArrayDeque<Long>()
                var noiseFloor = 500.0
                var aboveThreshold = false
                var lastPeakAt = 0L
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

                        if (!aboveThreshold && rms >= threshold && now - lastPeakAt >= 120) {
                            aboveThreshold = true
                            lastPeakAt = now
                            peaks.addLast(now)
                            while (peaks.isNotEmpty() && now - peaks.first() > 1500L) {
                                peaks.removeFirst()
                            }
                            if (peaks.size >= 3) {
                                peaks.clear()
                                mainHandler.post { if (running) onTripleTap() }
                            }
                        } else if (aboveThreshold && rms < threshold * 0.55) {
                            aboveThreshold = false
                        }

                        if (!aboveThreshold && rms < threshold) {
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
