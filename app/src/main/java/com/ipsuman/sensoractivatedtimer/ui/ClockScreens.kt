package com.ipsuman.sensoractivatedtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SensorStatusPanel(
    microphoneActive: Boolean,
    microphoneHistory: List<Float>,
    microphoneSpectrum: List<Float>,
    microphoneSpectrumMode: Boolean,
    onGraphTap: () -> Unit,
    microphoneLevel: Float,
    microphonePitchHz: Int,
    proximityActive: Boolean,
    proximityDistanceCm: Float?,
    accelerometerActive: Boolean
) {
    if (!microphoneActive && !proximityActive && !accelerometerActive) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (microphoneActive) {
                Text("🎙 Microphone graph", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Current pitch: ${if (microphonePitchHz > 0) "$microphonePitchHz Hz" else "— Hz"}")
                Text("Intensity (RMS): ${microphoneLevel.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Intensity ↑", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val graphOutlineColor = MaterialTheme.colorScheme.outline
                val graphPrimaryColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(140.dp).clickable(onClick = onGraphTap)) {
                    val left = 34f
                    val right = size.width - 8f
                    val top = 8f
                    val bottom = size.height - 24f
                    val graphHeight = bottom - top
                    val graphWidth = right - left
                    drawLine(graphOutlineColor, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
                    drawLine(graphOutlineColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
                    if (microphoneSpectrumMode) {
                        val sampleRate = 44100f
                        val fftSize = 2048f
                        val minHz = 20f
                        val maxHz = 20000f
                        val points = microphoneSpectrum.mapIndexedNotNull { index, magnitude ->
                            val hz = index * sampleRate / fftSize
                            if (hz < minHz || hz > maxHz) null else hz to magnitude
                        }
                        val maxValue = maxOf(points.maxOfOrNull { it.second } ?: 1f, 1e-6f)
                        if (points.size > 1) {
                            val path = Path()
                            points.forEachIndexed { index, point ->
                                val logPosition = (kotlin.math.ln((point.first / minHz).toDouble()) / kotlin.math.ln((maxHz / minHz).toDouble())).toFloat().coerceIn(0f, 1f)
                                val x = left + graphWidth * logPosition
                                val y = bottom - (point.second / maxValue).coerceIn(0f, 1f) * graphHeight
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = graphPrimaryColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                        }
                    } else {
                        val values = microphoneHistory
                        if (values.size > 1) {
                            val maxValue = maxOf(values.maxOrNull() ?: 1f, 1f)
                            val path = Path()
                            values.forEachIndexed { index, value ->
                                val x = left + graphWidth * index / (values.size - 1).toFloat()
                                val y = bottom - (value / maxValue).coerceIn(0f, 1f) * graphHeight
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = graphPrimaryColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
                if (microphoneSpectrumMode) {
                    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("20 Hz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1 kHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("20 kHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("X axis: frequency (Hz, logarithmic) • Y axis: intensity • Tap graph to show time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Time →", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                    Text("X axis: time • Y axis: intensity • Tap graph to show frequency", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (proximityActive) {
                Text("Proximity distance: ${proximityDistanceCm?.let { "%.1f cm".format(it) } ?: "Reading sensor…"}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            if (accelerometerActive) Text("Accelerometer is being used", fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
internal fun TimerScreen(
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
}

@Composable
internal fun StopwatchScreen(
    elapsedMillis: Long,
    running: Boolean,
    sensorListeningIntervalMs: Int,
    onSensorListeningIntervalChanged: (Int) -> Unit,
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
            Spacer(Modifier.height(28.dp))
            Text("Sensor listening interval", fontWeight = FontWeight.Bold)
            Text(
                "Every " + "%.2f".format(sensorListeningIntervalMs / 1000f) + " sec",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            androidx.compose.material3.Slider(
                value = sensorListeningIntervalMs.toFloat(),
                onValueChange = { onSensorListeningIntervalChanged(it.toInt().coerceIn(10, 1000)) },
                valueRange = 10f..1000f,
                steps = 98
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0.01 sec", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("1 sec", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Applies to proximity, accelerometer and microphone triggers.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


internal fun formatTime(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)

internal fun formatStopwatch(milliseconds: Long): String {
    val totalCentiseconds = milliseconds / 10
    val minutes = totalCentiseconds / 6000
    val seconds = (totalCentiseconds / 100) % 60
    val centiseconds = totalCentiseconds % 100
    return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}

