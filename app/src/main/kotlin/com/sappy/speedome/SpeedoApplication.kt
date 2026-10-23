package com.sappy.speedome

import android.app.Application

class SpeedoApplication : Application(), ContainerHolder {
    override lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppInfo.versionName = BuildConfig.VERSION_NAME
        AppInfo.debug = BuildConfig.DEBUG
        AppInfo.backgroundLocationEnabled = BuildConfig.BACKGROUND_LOCATION_ENABLED
        AppInfo.batteryExemptionDialog = BuildConfig.BATTERY_EXEMPTION_DIALOG
        container = AppContainer(AndroidPlatform(this))
    }
}
