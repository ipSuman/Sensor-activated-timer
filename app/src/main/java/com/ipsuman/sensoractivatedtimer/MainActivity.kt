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
            // Install the Compose content first so the window decor is initialized
            // before accessing WindowInsetsController for immersive fullscreen.
            setContent {
                ClockApp()
            }
            CrashLogger.log("Compose content installed")
            window.decorView.post {
                try {
                    enterFullscreen()
                    CrashLogger.log("Fullscreen setup completed")
                } catch (error: Throwable) {
                    CrashLogger.log("Fullscreen setup failed", error)
                    // Do not crash the entire app if fullscreen setup is unsupported.
                }
            }
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
                // Fullscreen is best-effort; a window UI issue must not close the app.
                CrashLogger.log("Fullscreen update failed", error)
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
