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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner, frontCamera, previewView) {
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
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val current = sampleLuma(image)
                        val previous = previousLuma
                        previousLuma = current
                        if (previous != null && previous.size == current.size) {
                            var difference = 0L
                            for (i in current.indices) difference += abs(current[i] - previous[i])
                            val averageDifference = difference.toFloat() / current.size
                            val now = SystemClock.elapsedRealtime()
                            if (averageDifference >= 18f && now - lastMovementAt >= 350L) {
                                lastMovementAt = now
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
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (_: Exception) {
                // If this camera cannot be opened, the rest of the clock app remains usable.
            }
        }
        providerFuture.addListener(listener, mainExecutor)
        onDispose {
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
                Button(onClick = onToggleCamera) { Text(if (frontCamera) "Front camera" else "Back camera") }
            }
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
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
