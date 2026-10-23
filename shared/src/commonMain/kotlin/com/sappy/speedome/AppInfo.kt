package com.sappy.speedome

/** Build facts the launcher passes in at startup (a library module has no BuildConfig of its own). */
object AppInfo {
    var versionName: String = ""
    var debug: Boolean = false

    /** Store-policy switches for the two optional reliability upgrades (docs/plan.md §8, Android). */
    var backgroundLocationEnabled: Boolean = true
    var batteryExemptionDialog: Boolean = true
}
