package com.sappy.speedome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.ui.IosToasts
import com.sappy.speedome.ui.SpeedoApp
import com.sappy.speedome.ui.theme.SpeedoColors
import com.sappy.speedome.ui.theme.SpeedoTheme
import kotlinx.coroutines.delay
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/** The app's one container on iOS (Android keeps its own in the Application). */
object IosApp {
    val container: AppContainer by lazy { AppContainer(IosPlatform()) }
}

/**
 * The whole iOS app: the shared [SpeedoApp] with the iOS container. "Visible" follows the app going
 * to and from the background, which ends the live meter like leaving the app on Android.
 */
@Composable
fun SpeedoIosApp() {
    val app = IosApp.container
    DisposableEffect(Unit) {
        app.visible.value = true
        UIApplication.sharedApplication.idleTimerDisabled = true // the screen stays on while SpeedoME is open (D24)
        val center = NSNotificationCenter.defaultCenter
        val toBackground = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            app.visible.value = false
        }
        val toForeground = center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            app.visible.value = true
        }
        onDispose {
            center.removeObserver(toBackground)
            center.removeObserver(toForeground)
        }
    }
    CompositionLocalProvider(LocalAppContainer provides app) {
        SpeedoTheme {
            Box(Modifier.fillMaxSize().background(SpeedoColors.Background)) {
                SpeedoApp()
                ToastOverlay(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun ToastOverlay(modifier: Modifier) {
    val message by IosToasts.message.collectAsState()
    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            IosToasts.message.value = null
        }
    }
    AnimatedVisibility(message != null, modifier.safeDrawingPadding().padding(bottom = 88.dp), enter = fadeIn(), exit = fadeOut()) {
        Text(
            message.orEmpty(), color = Color.White, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xE6202423)).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
