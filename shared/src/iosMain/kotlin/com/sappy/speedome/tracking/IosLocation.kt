package com.sappy.speedome.tracking

import com.sappy.speedome.engine.FixEvent
import com.sappy.speedome.monotonicNanos
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLActivityTypeAutomotiveNavigation
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * GPS through CoreLocation for the shared engine. It runs while tracking is active; during a trip it
 * keeps running with the app in the background (UIBackgroundModes: location), which iOS shows as the
 * blue location pill. CoreLocation marks invalid fields with negative numbers; they become nulls,
 * like Android's missing fields.
 */
class IosLocation(private val onFix: (FixEvent) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private val _authorization = MutableStateFlow(manager.authorizationStatus)
    val authorization: StateFlow<CLAuthorizationStatus> = _authorization.asStateFlow()
    private var running = false

    init {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = CLActivityTypeAutomotiveNavigation
        manager.pausesLocationUpdatesAutomatically = false
    }

    val allowed: Boolean get() = _authorization.value.let { it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways }
    val refused: Boolean get() = _authorization.value.let { it == kCLAuthorizationStatusDenied || it == kCLAuthorizationStatusRestricted }

    fun requestPermission() = manager.requestWhenInUseAuthorization()

    fun start() {
        if (running) return
        running = true
        manager.startUpdatingLocation()
    }

    fun stop() {
        if (!running) return
        running = false
        manager.stopUpdatingLocation()
    }

    /** During a trip: keep receiving fixes with the app in the background, with the blue pill showing. */
    fun setBackground(on: Boolean) {
        manager.allowsBackgroundLocationUpdates = on
        manager.showsBackgroundLocationIndicator = on
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        for (l in didUpdateLocations) (l as? CLLocation)?.toFix()?.let(onFix)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        _authorization.value = manager.authorizationStatus
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toFix(): FixEvent? {
    if (horizontalAccuracy < 0) return null
    val ageNanos = ((NSDate().timeIntervalSince1970 - timestamp.timeIntervalSince1970) * 1e9).toLong().coerceAtLeast(0)
    val (lat, lon) = coordinate.useContents { latitude to longitude }
    return FixEvent(
        tNanos = monotonicNanos() - ageNanos,
        utcMillis = (timestamp.timeIntervalSince1970 * 1000).toLong(),
        lat = lat,
        lon = lon,
        hAcc = horizontalAccuracy.toFloat(),
        altM = altitude.takeIf { verticalAccuracy > 0 },
        vAcc = verticalAccuracy.takeIf { it > 0 }?.toFloat(),
        speed = speed.takeIf { it >= 0 }?.toFloat(),
        speedAcc = speedAccuracy.takeIf { it >= 0 }?.toFloat(),
        bearing = course.takeIf { it >= 0 }?.toFloat(),
        bearingAcc = courseAccuracy.takeIf { it >= 0 }?.toFloat(),
        isMock = sourceInformation?.isSimulatedBySoftware == true,
    )
}
