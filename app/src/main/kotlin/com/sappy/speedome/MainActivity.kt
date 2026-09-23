package com.sappy.speedome

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.sappy.speedome.tracking.PermissionState
import com.sappy.speedome.tracking.TrackingService
import com.sappy.speedome.ui.SpeedoApp
import com.sappy.speedome.ui.theme.SpeedoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // The screen stays on while SpeedoME is visible; no wake lock or permission needed (D24).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as SpeedoApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                SpeedoTheme {
                    SpeedoApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Starting the location service while visible keeps GPS alive in the background later.
        if (PermissionState.of(this).canTrack) TrackingService.start(this)
    }
}
