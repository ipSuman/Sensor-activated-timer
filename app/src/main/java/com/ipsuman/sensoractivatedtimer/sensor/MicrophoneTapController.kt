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
    initialReferenceDurationMs: Long = 0L,
    initialReferencePitchHz: Int = 0,
    intensityTolerancePercent: Int = 70,
    durationTolerancePercent: Int = 30,
    pitchTolerancePercent: Int = 20,
    private val onAudioMetrics: (Double, Int) -> Unit = { _, _ -> },
    private val onSpectrumMetrics: (List<Float>) -> Unit = {},
    private val onReferenceSoundChanged: (Double, Long, Int) -> Unit = { _, _, _ -> },
    private val holdWhileToneActive: Boolean = false,
    private val onToneActivityChanged: (Boolean) -> Unit = {},
    initialToneMinimumIntensity: Int = 1800
) {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var referenceIntensity = initialReferenceIntensity
    @Volatile private var referenceDurationMs = initialReferenceDurationMs
    @Volatile private var referencePitchHz = initialReferencePitchHz.coerceAtLeast(0)
    @Volatile private var intensityTolerancePercent = intensityTolerancePercent.coerceIn(0, 100)
    @Volatile private var durationTolerancePercent = durationTolerancePercent.coerceIn(0, 100)
    @Volatile private var pitchTolerancePercent = pitchTolerancePercent.coerceIn(0, 100)
    @Volatile private var toneMinimumIntensity = initialToneMinimumIntensity.coerceIn(500, 12000).toDouble()
    @Volatile private var calibrationRequested = false

    fun updateToneMinimumIntensity(value: Int) {
        toneMinimumIntensity = value.coerceIn(500, 12000).toDouble()
    }

    fun updateTolerances(intensityPercent: Int, durationPercent: Int, pitchPercent: Int = pitchTolerancePercent) {
        intensityTolerancePercent = intensityPercent.coerceIn(0, 100)
        durationTolerancePercent = durationPercent.coerceIn(0, 100)
        pitchTolerancePercent = pitchPercent.coerceIn(0, 100)
    }

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

        // 44.1 kHz sampling captures the audible range up to 20 kHz (Nyquist: 22.05 kHz).
        val sampleRate = 44100
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
                maxOf(minBuffer, 8192)
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
                val buffer = ShortArray(2048)
                var noiseFloor = 500.0
                var aboveThreshold = false
                var referenceEventStartedAt = 0L
                var toneStartedAt = 0L
                var toneTriggered = false
                var toneActivityActive = false
                var lastLoudAt = 0L
                var calibrationStartedAt = 0L
                var calibrationSum = 0.0
                var calibrationSamples = 0
                var calibrationDurationMs = 0L
                var calibrationPitchSum = 0L
                var calibrationPitchSamples = 0
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
                        val pitchHz = estimatePitchHz(buffer, count, sampleRate)
                        val spectrum = calculateSpectrum(buffer, count, sampleRate)
                        mainHandler.post {
                            if (running) {
                                onAudioMetrics(rms, pitchHz)
                                onSpectrumMetrics(spectrum)
                            }
                        }
                        val threshold = maxOf(toneMinimumIntensity, noiseFloor * 2.8)
                        val now = System.currentTimeMillis()

                        if (calibrationRequested) {
                            if (calibrationStartedAt == 0L) {
                                calibrationStartedAt = now
                                calibrationSum = 0.0
                                calibrationSamples = 0
                                calibrationDurationMs = 0L
                                calibrationPitchSum = 0L
                                calibrationPitchSamples = 0
                            }
                            if (rms > maxOf(800.0, noiseFloor * 1.8)) {
                                calibrationSum += rms
                                calibrationSamples++
                                calibrationDurationMs += count * 1000L / sampleRate
                                if (pitchHz > 0) { calibrationPitchSum += pitchHz; calibrationPitchSamples++ }
                            }
                            if (now - calibrationStartedAt >= 2000L) {
                                calibrationRequested = false
                                calibrationStartedAt = 0L
                                if (calibrationSamples >= 5) {
                                    referenceIntensity = calibrationSum / calibrationSamples
                                    referenceDurationMs = calibrationDurationMs.coerceAtLeast(1L)
                                    referencePitchHz = if (calibrationPitchSamples > 0) (calibrationPitchSum / calibrationPitchSamples).toInt() else 0
                                    val measuredIntensity = referenceIntensity
                                    val measuredDuration = referenceDurationMs
                                    val measuredPitch = referencePitchHz
                                    mainHandler.post { onReferenceSoundChanged(measuredIntensity, measuredDuration, measuredPitch) }
                                } else {
                                    mainHandler.post { onError("The reference sound was too quiet. Try recording it again closer to the microphone.") }
                                }
                            }
                            continue
                        }

                        when (triggerMode) {
                            MicrophoneTriggerMode.RECORDED_SOUND -> {
                                val reference = referenceIntensity
                                val tolerance = intensityTolerancePercent.coerceIn(0, 100) / 100.0
                                val lowerBound = reference * (1.0 - tolerance)
                                val upperBound = reference * (1.0 + tolerance)
                                val pitchReference = referencePitchHz
                                val pitchTolerance = pitchTolerancePercent.coerceIn(0, 100) / 100.0
                                val pitchMatches = pitchReference <= 0 || (pitchHz > 0 && pitchHz.toDouble() in (pitchReference * (1.0 - pitchTolerance))..(pitchReference * (1.0 + pitchTolerance)))
                                val matchesReference = reference > 100.0 && rms in lowerBound..upperBound && pitchMatches
                                if (matchesReference) {
                                    if (referenceEventStartedAt == 0L) referenceEventStartedAt = now
                                } else if (referenceEventStartedAt != 0L) {
                                    val observedDuration = now - referenceEventStartedAt
                                    val durationTolerance = durationTolerancePercent.coerceIn(0, 100) / 100.0
                                    val minDuration = referenceDurationMs * (1.0 - durationTolerance)
                                    val maxDuration = referenceDurationMs * (1.0 + durationTolerance)
                                    referenceEventStartedAt = 0L
                                    if (referenceDurationMs > 0L && observedDuration.toDouble() in minDuration..maxDuration) {
                                        mainHandler.post { if (running) onTripleTap() }
                                    }
                                }
                            }
                            MicrophoneTriggerMode.TONE -> {
                                // Require 250 ms of sustained sound to activate. In hold mode,
                                // release the timer as soon as the signal drops below the release
                                // threshold; normal trigger mode retains its one-shot behavior.
                                if (rms >= threshold) {
                                    lastLoudAt = now
                                    if (toneStartedAt == 0L) toneStartedAt = now
                                    if (!toneTriggered && now - toneStartedAt >= 250L) {
                                        toneTriggered = true
                                        if (holdWhileToneActive) {
                                            toneActivityActive = true
                                            mainHandler.post { if (running) onToneActivityChanged(true) }
                                        } else {
                                            mainHandler.post { if (running) onTripleTap() }
                                        }
                                    }
                                } else if (rms < threshold * 0.65) {
                                    if (toneActivityActive) {
                                        toneActivityActive = false
                                        mainHandler.post { onToneActivityChanged(false) }
                                    }
                                    if (lastLoudAt != 0L && now - lastLoudAt >= 400L) {
                                        toneStartedAt = 0L
                                        toneTriggered = false
                                        lastLoudAt = 0L
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

    /** Return FFT magnitudes for bins 0..Nyquist; a Hann window reduces spectral leakage. */
    private fun calculateSpectrum(samples: ShortArray, count: Int, sampleRate: Int): List<Float> {
        val n = 2048
        if (count < n) return emptyList()
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        for (i in 0 until n) {
            val hann = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (n - 1))
            real[i] = samples[i].toDouble() * hann
        }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * Math.PI / length
            val wLenReal = kotlin.math.cos(angle)
            val wLenImag = kotlin.math.sin(angle)
            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImag = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * wReal - imag[odd] * wImag
                    val oddImag = real[odd] * wImag + imag[odd] * wReal
                    real[odd] = real[even] - oddReal
                    imag[odd] = imag[even] - oddImag
                    real[even] += oddReal
                    imag[even] += oddImag
                    val nextWReal = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextWReal
                }
                start += length
            }
            length = length shl 1
        }
        return (0..n / 2).map { bin ->
            (kotlin.math.sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]) / n).toFloat()
        }
    }

    private fun estimatePitchHz(samples: ShortArray, count: Int, sampleRate: Int): Int {
        if (count < 512) return 0
        // Autocorrelation pitch estimate; silence/noise naturally produces no stable pitch.
        var bestLag = 0
        var bestCorrelation = 0.0
        val minLag = sampleRate / 1000 // about 1 kHz upper pitch
        val maxLag = minOf(sampleRate / 70, count / 2)
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var energyA = 0.0
            var energyB = 0.0
            for (i in 0 until count - lag) {
                val a = samples[i].toDouble()
                val b = samples[i + lag].toDouble()
                correlation += a * b
                energyA += a * a
                energyB += b * b
            }
            val denominator = kotlin.math.sqrt(energyA * energyB)
            val normalized = if (denominator > 0.0) correlation / denominator else 0.0
            if (normalized > bestCorrelation) {
                bestCorrelation = normalized
                bestLag = lag
            }
        }
        return if (bestCorrelation >= 0.25 && bestLag > 0) sampleRate / bestLag else 0
    }

    fun stop() {
        running = false
        mainHandler.post { onToneActivityChanged(false) }
        try { recorder?.stop() } catch (_: IllegalStateException) { }
        recorder = null
        worker = null
    }
}
