package com.sappy.speedome.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sappy.speedome.engine.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        object : PlatformActions {
            override fun toast(message: String, long: Boolean) {
                Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }

            override fun confirmHaptic() {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
            }

            override fun openUrl(url: String) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            }

            override suspend fun shareFile(fileName: String, mime: String, text: String) {
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, fileName).apply { writeText(text) }
                    FileProvider.getUriForFile(context, context.packageName + ".files", file)
                }
                val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share $fileName"))
            }
        }
    }
}

@Composable
actual fun rememberSaveFile(mime: String): (fileName: String, content: () -> String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<() -> String>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val content = pending[0]
        if (uri != null && content != null) {
            scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(content().toByteArray()) } }
        }
    }
    return { name, content ->
        pending[0] = content
        launcher.launch(name)
    }
}

@Composable
actual fun rememberOpenTextFile(onOpened: (name: String, text: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            if (text != null) onOpened(name, text)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
actual fun rememberBatteryLow(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val low by produceState(false, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = batteryLow(context)
                delay(30_000)
            }
        }
    }
    return low
}

private fun batteryLow(context: Context): Boolean {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val charging = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    return !charging && level >= 0 && level * 100 / scale.coerceAtLeast(1) < 15
}

@Composable
actual fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
actual fun rememberPermissionsUi(): PermissionsUi {
    val holder = rememberPermissions()
    return remember(holder) {
        object : PermissionsUi {
            override val canTrack: Boolean get() = holder.state.canTrack

            @Composable
            override fun Cards(mode: Mode, recording: Boolean) {
                PermissionCards(holder, mode)
                ReliabilityOfferCard(holder, recording)
            }
        }
    }
}

actual val hasReliabilityScreen: Boolean = true

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

actual val hasSatelliteData: Boolean = true
