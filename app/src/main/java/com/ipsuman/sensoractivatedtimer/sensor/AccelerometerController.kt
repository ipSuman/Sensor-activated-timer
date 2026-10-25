package com.ipsuman.sensoractivatedtimer.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Detects a deliberate shake using acceleration changes (gravity removed approximately
 * by comparing magnitude to Earth's gravity). Sensitivity 1..10: higher is more sensitive.
 */
class AccelerometerController(
    context: Context,
    private val sensitivity: Int,
    private val onShake: () -> Unit,
    private val onAvailabilityChanged: (Boolean) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastTriggerAt = 0L
    private var lastAboveThresholdAt = 0L
    private var armed = true

    fun start() {
        onAvailabilityChanged(sensor != null)
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val deviation = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)
        val threshold = (4.2f - (sensitivity.coerceIn(1, 10) - 1) * 0.28f).coerceAtLeast(1.7f)
        val now = SystemClock.elapsedRealtime()

        if (deviation >= threshold) {
            lastAboveThresholdAt = now
            if (armed && now - lastTriggerAt >= 900L) {
                armed = false
                lastTriggerAt = now
                onShake()
            }
        } else if (!armed && now - lastAboveThresholdAt >= 350L) {
            armed = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
