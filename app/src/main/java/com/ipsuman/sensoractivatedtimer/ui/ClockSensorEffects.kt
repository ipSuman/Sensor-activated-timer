package com.ipsuman.sensoractivatedtimer.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.ipsuman.sensoractivatedtimer.sensor.AccelerometerController
import com.ipsuman.sensoractivatedtimer.sensor.ProximitySensorController

/**
 * Owns the registration lifecycle for the proximity and accelerometer sensors.
 * Registration and cleanup stay tied to the relevant settings, as before.
 */
@Composable
internal fun ProximityAndAccelerometerEffects(
    context: Context,
    proximityEnabled: Boolean,
    accelerometerEnabled: Boolean,
    accelerometerSensitivity: Int,
    onProximityAction: () -> Unit,
    onProximityAvailabilityChanged: (Boolean) -> Unit,
    onProximityDistanceChanged: (Float) -> Unit,
    onAccelerometerAction: () -> Unit,
    onAccelerometerAvailabilityChanged: (Boolean) -> Unit
) {
    DisposableEffect(context, proximityEnabled) {
        if (!proximityEnabled) {
            onDispose { }
        } else {
            val controller = ProximitySensorController(
                context = context,
                onCovered = onProximityAction,
                onAvailabilityChanged = onProximityAvailabilityChanged,
                onDistanceChanged = onProximityDistanceChanged
            )
            controller.start()
            onDispose { controller.stop() }
        }
    }

    DisposableEffect(context, accelerometerEnabled, accelerometerSensitivity) {
        if (!accelerometerEnabled) {
            onAccelerometerAvailabilityChanged(
                (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            )
            onDispose { }
        } else {
            val controller = AccelerometerController(
                context = context,
                sensitivity = accelerometerSensitivity,
                onShake = onAccelerometerAction,
                onAvailabilityChanged = onAccelerometerAvailabilityChanged
            )
            controller.start()
            onDispose { controller.stop() }
        }
    }
}
