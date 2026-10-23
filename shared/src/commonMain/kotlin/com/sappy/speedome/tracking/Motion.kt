package com.sappy.speedome.tracking

/**
 * [accelEast]/[accelNorth]: horizontal linear acceleration (gravity removed) in m/s², rotated into the
 * world frame so it doesn't depend on how the phone is mounted. Null without the sensor.
 */
data class Motion(
    val headingDeg: Float? = null,
    val headingAccuracy: Int = 0,
    val yawRateRadS: Float = 0f,
    val accelEast: Float? = null,
    val accelNorth: Float? = null,
)
