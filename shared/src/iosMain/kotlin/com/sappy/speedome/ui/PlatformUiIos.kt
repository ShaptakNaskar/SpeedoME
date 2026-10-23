@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sappy.speedome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappy.speedome.IosPlatform
import com.sappy.speedome.LocalAppContainer
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

/** Short messages shown over the app ([ToastOverlay] in the iOS root); iOS has no system toast. */
object IosToasts {
    val message = MutableStateFlow<String?>(null)
}

/** The view controller on top, to present share sheets and pickers from. */
internal fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private fun openUrlString(url: String) {
    val u = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any>(), null)
}

private fun shareText(fileName: String, text: String) {
    val path = NSTemporaryDirectory() + fileName
    @Suppress("CAST_NEVER_SUCCEEDS")
    (NSString.create(string = text)).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
    topViewController()?.presentViewController(sheet, animated = true, completion = null)
}

private object Actions : PlatformActions {
    override fun toast(message: String, long: Boolean) {
        IosToasts.message.value = message
    }

    override fun confirmHaptic() {
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }

    override fun openUrl(url: String) = openUrlString(url)

    override suspend fun shareFile(fileName: String, mime: String, text: String) = shareText(fileName, text)
}

@Composable
actual fun rememberPlatformActions(): PlatformActions = Actions

/** The share sheet has "Save to Files", so saving goes through it too. */
@Composable
actual fun rememberSaveFile(mime: String): (fileName: String, content: () -> String) -> Unit =
    remember { { name, content -> shareText(name, content()) } }

private class Picker(private val onOpened: (String, String) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val text = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null) ?: return
        onOpened(url.lastPathComponent ?: "file", text)
    }
}

@Composable
actual fun rememberOpenTextFile(onOpened: (name: String, text: String) -> Unit): () -> Unit {
    val picker = remember { Picker(onOpened) }
    return {
        val vc = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        vc.delegate = picker
        topViewController()?.presentViewController(vc, animated = true, completion = null)
    }
}

@Composable
actual fun rememberBatteryLow(): Boolean {
    val low by produceState(false) {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        while (true) {
            val charging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
            value = !charging && device.batteryLevel in 0f..0.149f
            delay(30_000)
        }
    }
    return low
}

@Composable
actual fun rememberAnimationsOff(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

@Composable
actual fun rememberPermissionsUi(): PermissionsUi {
    val location = (LocalAppContainer.current.platform as IosPlatform).location
    val status by location.authorization.collectAsStateWithLifecycle()
    return remember(status) {
        object : PermissionsUi {
            override val canTrack: Boolean = location.allowed

            @Composable
            override fun Cards(mode: Mode, recording: Boolean) {
                when {
                    location.refused -> Card(
                        "Location is off for SpeedoME",
                        "Speed comes from GPS. Allow location in Settings, While Using the App.",
                        "Open Settings",
                    ) { openUrlString(UIApplicationOpenSettingsURLString) }
                    !location.allowed -> Card(
                        "Allow location",
                        "SpeedoME measures your speed and distance with GPS. Nothing leaves your phone.",
                        "Allow",
                    ) { location.requestPermission() }
                }
            }
        }
    }
}

@Composable
private fun Card(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(SpeedoColors.Raised).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = SpeedoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = SpeedoColors.Muted, fontSize = 14.sp)
        FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

actual val hasReliabilityScreen: Boolean = false

@Composable
actual fun ReliabilityScreen(onBack: () -> Unit) {}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

actual val hasSatelliteData: Boolean = false
