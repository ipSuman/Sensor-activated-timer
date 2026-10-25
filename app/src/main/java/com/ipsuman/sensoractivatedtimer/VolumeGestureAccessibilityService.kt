package com.ipsuman.sensoractivatedtimer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock
import android.content.Intent

class VolumeGestureAccessibilityService : AccessibilityService() {
    private var pressCount = 0
    private var firstPressAt = 0L

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
        }
        CrashLogger.log("Volume gesture accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        pressCount = 0
        firstPressAt = 0L
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val enabled = getSharedPreferences("button_gesture_settings", MODE_PRIVATE)
            .getBoolean("enabled", false)
        if (!enabled || event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) return true
            val now = SystemClock.elapsedRealtime()
            if (pressCount == 0 || now - firstPressAt > 700L) {
                pressCount = 1
                firstPressAt = now
            } else {
                pressCount++
            }
            if (pressCount >= 3) {
                pressCount = 0
                firstPressAt = 0L
                sendBroadcast(Intent(ACTION_PAUSE_CLOCK).setPackage(packageName))
                CrashLogger.log("Accessibility service detected triple Volume Down")
            }
            return true
        }
        return event.action == KeyEvent.ACTION_UP
    }

    companion object {
        const val ACTION_PAUSE_CLOCK = "com.ipsuman.sensoractivatedtimer.ACTION_PAUSE_CLOCK"
    }
}
