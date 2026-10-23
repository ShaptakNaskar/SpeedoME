package com.sappy.speedome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.sappy.speedome.engine.Mode

// What the shared screens need from the platform's UI layer. Android, iOS and the desktop preview
// each implement these in their PlatformUi file.

/** Small system services the screens call. */
interface PlatformActions {
    fun toast(message: String, long: Boolean = false)

    /** One confirming buzz (target reached). */
    fun confirmHaptic()

    fun openUrl(url: String)

    /** Hands [text] to the system share sheet as a file named [fileName]. */
    suspend fun shareFile(fileName: String, mime: String, text: String)
}

@Composable
expect fun rememberPlatformActions(): PlatformActions

/** A "save as" picker: call it with a suggested file name and the content to write there. */
@Composable
expect fun rememberSaveFile(mime: String): (fileName: String, content: () -> String) -> Unit

/** A file picker for text files (developer replay): calls [onOpened] with the file's name and text. */
@Composable
expect fun rememberOpenTextFile(onOpened: (name: String, text: String) -> Unit): () -> Unit

/** True while the battery is under 15 % and not charging; checked every 30 s while on screen. */
@Composable
expect fun rememberBatteryLow(): Boolean

/** True when the system has animations turned off (the startup sweep and range animations skip). */
@Composable
expect fun rememberAnimationsOff(): Boolean

/** Location (and other) permissions: whether tracking can run, and the cards that ask for what's missing. */
@Stable
interface PermissionsUi {
    val canTrack: Boolean

    @Composable
    fun Cards(mode: Mode, recording: Boolean)
}

@Composable
expect fun rememberPermissionsUi(): PermissionsUi

/** Android's background-reliability screen (battery and OEM settings). Other platforms don't need one. */
expect val hasReliabilityScreen: Boolean

@Composable
expect fun ReliabilityScreen(onBack: () -> Unit)

/** The Map theme: the live route coloured by speed, a heading puck and a follow camera. */
@Composable
expect fun MapPage(driver: GaugeDriver, northUp: Boolean, modifier: Modifier = Modifier)

/** Handles the system back gesture/button (Android); iOS has no system back, so it does nothing there. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** False where the platform gives apps no satellite list or NMEA (iOS); the Nerd page says so. */
expect val hasSatelliteData: Boolean
