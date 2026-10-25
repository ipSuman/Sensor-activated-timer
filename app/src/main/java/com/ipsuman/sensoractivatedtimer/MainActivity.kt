package com.ipsuman.sensoractivatedtimer

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ipsuman.sensoractivatedtimer.ui.ClockApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.log("MainActivity.onCreate started; SDK=${Build.VERSION.SDK_INT}")
        try {
            enterFullscreen()
            CrashLogger.log("Fullscreen setup completed")
            setContent {
                ClockApp()
            }
            CrashLogger.log("Compose content installed")
        } catch (error: Throwable) {
            CrashLogger.log("FATAL: MainActivity.onCreate failed", error)
            throw error
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                enterFullscreen()
            } catch (error: Throwable) {
                CrashLogger.log("Fullscreen update failed", error)
                throw error
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enterFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}
