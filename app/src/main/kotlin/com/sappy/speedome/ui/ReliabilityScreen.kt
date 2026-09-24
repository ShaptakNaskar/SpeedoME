package com.sappy.speedome.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.sappy.speedome.BuildConfig
import com.sappy.speedome.ui.theme.SpeedoColors

/** One phone maker's extra battery settings, with the screens we can try to open directly. */
private data class Oem(val name: String, val steps: String, val screens: List<ComponentName>, val slug: String)

private val OEMS = mapOf(
    "xiaomi" to Oem(
        "Xiaomi / Redmi / POCO",
        "Turn on Autostart for SpeedoME, then in Battery saver choose \"No restrictions\".",
        listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        ),
        "xiaomi",
    ),
    "samsung" to Oem(
        "Samsung",
        "Battery → Background usage limits: add SpeedoME to \"Never sleeping apps\", and make sure it is not in \"Sleeping apps\".",
        listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
        "samsung",
    ),
    "oneplus" to Oem(
        "OnePlus / OPPO / Realme",
        "Battery → set SpeedoME to \"Don't optimize\" (or \"Allow background activity\"), and allow Auto-launch.",
        listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        ),
        "oneplus",
    ),
    "vivo" to Oem(
        "Vivo / iQOO",
        "Battery → High background power consumption: allow SpeedoME. Also allow Auto-start.",
        listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ),
        "vivo",
    ),
    "huawei" to Oem(
        "Huawei / Honor",
        "App launch: switch SpeedoME to \"Manage manually\" and turn on Auto-launch, Secondary launch and Run in background.",
        listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ),
        "huawei",
    ),
)

private fun oemFor(manufacturer: String): Oem? {
    val m = manufacturer.lowercase()
    return when {
        m in listOf("xiaomi", "redmi", "poco") -> OEMS["xiaomi"]
        m == "samsung" -> OEMS["samsung"]
        m in listOf("oneplus", "oppo", "realme") -> OEMS["oneplus"]
        m in listOf("vivo", "iqoo") -> OEMS["vivo"]
        m in listOf("huawei", "honor") -> OEMS["huawei"]
        else -> null
    }
}

/**
 * Background reliability checklist (docs/plan.md §8): a tick per permission, the two optional
 * upgrades, and phone-maker instructions with deep links where they exist.
 */
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val permissions = rememberPermissions()
    val p = permissions.state
    var explainBackground by rememberSaveable { mutableStateOf(false) }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions.refresh() }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissions.refresh() }
    val mine = oemFor(Build.MANUFACTURER)
    var allBrands by rememberSaveable { mutableStateOf(false) }

    if (explainBackground) {
        AlertDialog(
            onDismissRequest = { explainBackground = false },
            title = { Text("Allow all the time?") },
            text = {
                Text(
                    "If Android closes SpeedoME during a trip, this lets it restart and keep recording without you opening it. " +
                        "Location is only used while tracking is on, and never leaves your phone. " +
                        if (Build.VERSION.SDK_INT >= 30) "On the next screen, choose \"Allow all the time\"." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainBackground = false
                    if (Build.VERSION.SDK_INT >= 29) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explainBackground = false }) { Text("Not now") } },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = SpeedoColors.Text, fontSize = 26.sp) }
            Text("Background reliability", style = MaterialTheme.typography.titleLarge, color = SpeedoColors.Text)
        }
        Text(
            "Android may close apps to save battery. These keep SpeedoME tracking when the screen is off, and let it restart itself mid-trip.",
            color = SpeedoColors.Muted, fontSize = 14.sp,
        )
        Check("Precise location", "Needed for speed.", p.fineLocation)
        Check("Notifications", "Shows live speed and trip controls while tracking.", p.notifications)
        if (BuildConfig.BACKGROUND_LOCATION_ENABLED) {
            Check("Location: Allow all the time", "Optional. Lets tracking restart itself after Android closes the app.", p.backgroundLocation) {
                if (p.fineLocation) explainBackground = true
            }
        }
        if (BuildConfig.BATTERY_EXEMPTION_DIALOG) {
            Check("Battery: unrestricted", "Optional. Stops Android pausing GPS to save battery.", p.batteryExempt) {
                battery.launch(batteryIntent(context))
            }
        }
        Text(
            if (p.canSelfRestart) "All set: tracking restarts itself if Android closes SpeedoME." else "Without both optional items, a trip resumes when you reopen the app.",
            color = if (p.canSelfRestart) Color(0xFF3ECF7A) else SpeedoColors.Muted, fontSize = 13.sp,
        )

        Section("YOUR PHONE")
        if (mine != null) {
            OemCard(mine, context)
        } else {
            Text(
                "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)}: no extra steps are known for this phone beyond the items above.",
                color = SpeedoColors.Text, fontSize = 14.sp,
            )
        }
        TextButton(onClick = { allBrands = !allBrands }) { Text(if (allBrands) "Hide other brands" else "Other brands") }
        if (allBrands) OEMS.values.filter { it != mine }.forEach { OemCard(it, context) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = SpeedoColors.Accent, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Check(title: String, body: String, ok: Boolean, onFix: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (ok) Color(0xFF1E5B3A) else SpeedoColors.Raised),
            contentAlignment = Alignment.Center,
        ) { Text(if (ok) "✓" else "", color = Color(0xFF3ECF7A), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = SpeedoColors.Text, fontSize = 16.sp)
            Text(body, color = SpeedoColors.Muted, fontSize = 13.sp)
        }
        if (!ok && onFix != null) FilledTonalButton(onClick = onFix) { Text("Allow") }
    }
}

@Composable
private fun OemCard(oem: Oem, context: Context) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SpeedoColors.Raised).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(oem.name, color = SpeedoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(oem.steps, color = SpeedoColors.Muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { openOemScreen(context, oem) }) { Text("Open settings") }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://dontkillmyapp.com/${oem.slug}".toUri())) }
            }) { Text("More help") }
        }
    }
}

/** Tries each known OEM screen, then falls back to this app's system settings page. */
private fun openOemScreen(context: Context, oem: Oem) {
    for (c in oem.screens) {
        val i = Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("package_name", context.packageName).putExtra("package_label", "SpeedoME")
        if (runCatching { context.startActivity(i) }.isSuccess) return
    }
    appDetails(context)
}

private fun appDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** The direct exemption dialog, or the system list when a phone doesn't offer it. */
@SuppressLint("BatteryLife")
private fun batteryIntent(context: Context): Intent {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    return if (direct.resolveActivity(context.packageManager) != null) direct else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
