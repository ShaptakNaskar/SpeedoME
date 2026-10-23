package com.sappy.speedome.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.sappy.speedome.SpeedoIosApp
import platform.UIKit.UIViewController

/** Entry point for the Swift host (`MainViewControllerKt.MainViewController()`): the whole app is Compose. */
fun MainViewController(): UIViewController = ComposeUIViewController { SpeedoIosApp() }
