package com.ipsuman.sensoractivatedtimer.ui

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
internal fun CameraMovementPanel(
    frontCamera: Boolean,
    onToggleCamera: () -> Unit,
    onMovementDetected: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var torchEnabled by remember { mutableStateOf(false) }
    var movementSensitivity by remember { mutableStateOf(18) }
    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    DisposableEffect(lifecycleOwner, frontCamera, previewView) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        val listener = Runnable {
            try {
                provider = providerFuture.get()
                val cameraProvider = provider ?: return@Runnable
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                var previousLuma: IntArray? = null
                var lastMovementAt = 0L
                // Temporal filter: require movement in at least 3 of the most recent 5 analyzed frames.
                val movementWindow = ArrayDeque<Boolean>()
                var consecutiveWindowHits = 0
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val current = sampleLuma(image)
                        val previous = previousLuma
                        previousLuma = current
                        if (previous != null && previous.size == current.size) {
                            val sensitivity = movementSensitivity
                            var changedSamples = 0
                            for (i in current.indices) {
                                if (abs(current[i] - previous[i]) >= 18) changedSamples++
                            }
                            // Sensitivity is the percentage of the 16x16 sample grid that changed.
                            val changedPercent = changedSamples * 100 / current.size
                            val frameHasMovement = changedPercent >= sensitivity
                            movementWindow.addLast(frameHasMovement)
                            if (frameHasMovement) consecutiveWindowHits++
                            if (movementWindow.size > 5 && movementWindow.removeFirst()) consecutiveWindowHits--
                            val now = SystemClock.elapsedRealtime()
                            if (movementWindow.size >= 3 && consecutiveWindowHits >= 3 && now - lastMovementAt >= 350L) {
                                lastMovementAt = now
                                movementWindow.clear()
                                consecutiveWindowHits = 0
                                mainExecutor.execute { onMovementDetected() }
                            }
                        }
                    } finally {
                        image.close()
                    }
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(if (frontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                    .build()
                cameraProvider.unbindAll()
                activeCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                activeCamera?.cameraControl?.enableTorch(torchEnabled)
            } catch (_: Exception) {
                // If this camera cannot be opened, the rest of the clock app remains usable.
            }
        }
        providerFuture.addListener(listener, mainExecutor)
        onDispose {
            try { activeCamera?.cameraControl?.enableTorch(false) } catch (_: Exception) { }
            torchEnabled = false
            activeCamera = null
            try { provider?.unbindAll() } catch (_: Exception) { }
            analysisExecutor.shutdown()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Camera movement", style = MaterialTheme.typography.titleSmall)
            }
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Text("Movement sensitivity: $movementSensitivity%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            Slider(
                value = movementSensitivity.toFloat(),
                onValueChange = { movementSensitivity = it.toInt().coerceIn(2, 100) },
                valueRange = 2f..100f,
                steps = 97
            )
            Text("Triggers when at least this percentage of sampled image points change. Temporal filter: movement must be detected in 3 of 5 frames.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onToggleCamera,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(if (frontCamera) "Switch to back camera" else "Switch to front camera") }
            Button(
                onClick = {
                    val nextState = !torchEnabled
                    activeCamera?.cameraControl?.enableTorch(nextState)
                    torchEnabled = nextState
                },
                enabled = activeCamera != null,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) { Text(if (torchEnabled) "Torch: On" else "Torch") }
            Text(
                "Movement in the camera view triggers the selected clock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun sampleLuma(image: ImageProxy): IntArray {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val grid = 16
    val samples = IntArray(grid * grid)
    var index = 0
    for (gy in 0 until grid) {
        val y = (gy * height / grid).coerceAtMost(height - 1)
        for (gx in 0 until grid) {
            val x = (gx * width / grid).coerceAtMost(width - 1)
            val offset = y * rowStride + x * pixelStride
            samples[index++] = buffer.get(offset).toInt() and 0xff
        }
    }
    return samples
}
