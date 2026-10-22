package com.sappy.speedome.ios

import com.sappy.speedome.engine.FixEvent
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLActivityTypeAutomotiveNavigation
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * GPS through CoreLocation, every fix as a [FixEvent] on the main thread. iOS reports invalid
 * fields as negative numbers; they become nulls like Android's missing fields.
 */
class IosGps(private val onFix: (FixEvent) -> Unit, private val onDenied: (Boolean) -> Unit) :
    NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()

    fun start() {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = CLActivityTypeAutomotiveNavigation
        manager.pausesLocationUpdatesAutomatically = false
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    fun stop() = manager.stopUpdatingLocation()

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        for (l in didUpdateLocations) (l as? CLLocation)?.toFix()?.let(onFix)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val s = manager.authorizationStatus
        onDenied(s == kCLAuthorizationStatusDenied || s == kCLAuthorizationStatusRestricted)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toFix(): FixEvent? {
    if (horizontalAccuracy < 0) return null
    val ageNanos = ((NSDate().timeIntervalSince1970 - timestamp.timeIntervalSince1970) * 1e9).toLong().coerceAtLeast(0)
    val (lat, lon) = coordinate.useContents { latitude to longitude }
    return FixEvent(
        tNanos = Clocks.nowNanos() - ageNanos,
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
