package com.ipsuman.sensoractivatedtimer.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Reports when the proximity sensor changes between covered/near and clear/far.
 * The caller decides what action a covered sensor should trigger.
 */
class ProximitySensorController(
    context: Context,
    private val onCovered: () -> Unit,
    private val onAvailabilityChanged: (Boolean) -> Unit = {}
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var wasCovered = false

    fun start() {
        val sensor = proximitySensor
        onAvailabilityChanged(sensor != null)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY || event.values.isEmpty()) return

        val sensor = proximitySensor ?: return
        val covered = event.values[0] < sensor.maximumRange

        // Trigger only on the transition from clear to covered, not continuously.
        if (covered && !wasCovered) {
            onCovered()
        }
        wasCovered = covered
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
