package com.sappy.speedome.ui

import com.sappy.speedome.engine.TrackView
import com.sappy.speedome.tracking.MotionSource
import kotlin.math.cos
import kotlin.math.sin

/** Lateral G (positive = accelerating to the right, i.e. turning right) and forward G (positive = speeding up). */
data class GForce(val lateral: Double, val forward: Double, val source: String)

private const val G = 9.81

/**
 * Accelerometer when available: the world-frame horizontal acceleration split along the direction
 * of travel (GPS course while moving, compass heading when slow), so any phone mount works.
 * Otherwise speed × yaw rate (sideways) and the GPS speed filter's acceleration (forward).
 */
fun gForce(view: TrackView, motion: MotionSource.Motion): GForce {
    val e = motion.accelEast
    val n = motion.accelNorth
    val course = view.lastFix?.bearing?.takeIf { view.speedMps > 2.0 } ?: motion.headingDeg?.toDouble()
    if (e != null && n != null && course != null) {
        val c = Math.toRadians(course)
        val forward = (e * sin(c) + n * cos(c)) / G
        val right = (e * cos(c) - n * sin(c)) / G
        return GForce(right, forward, "accel")
    }
    // Yaw rate about "up" is positive turning left (counter-clockwise), so rightward G is −v·ω.
    return GForce(-view.speedMps * motion.yawRateRadS / G, view.accelMps2 / G, "gps+gyro")
}
