package com.sappy.speedome.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Compass heading and yaw rate for the nerd page and the heading / G-force switches.
 * Runs only while someone holds it ([acquire]/[release]); never in the background.
 *
 * Yaw rate is the gyroscope projected onto gravity, so it doesn't matter how the phone is mounted.
 * Lateral G then comes from speed × yaw rate, and longitudinal G from the speed filter's acceleration.
 */
class MotionSource(context: Context) : SensorEventListener {
    data class Motion(val headingDeg: Float? = null, val headingAccuracy: Int = 0, val yawRateRadS: Float = 0f)

    private val sm = context.applicationContext.getSystemService(SensorManager::class.java)
    private val rotation: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _motion = MutableStateFlow(Motion())
    val motion: StateFlow<Motion> = _motion.asStateFlow()

    private var holders = 0
    private val rot = FloatArray(9)
    private val gravity = floatArrayOf(0f, 0f, 9.81f)
    private var yawSmoothed = 0f

    @Synchronized
    fun acquire() {
        if (holders++ > 0) return
        rotation?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    @Synchronized
    fun release() {
        if (holders == 0 || --holders > 0) return
        sm.unregisterListener(this)
        _motion.value = Motion()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                // World frame: X east, Y north, Z up. Use whichever device axis lies flatter:
                // the top edge (phone flat) or the back camera direction (phone upright in a mount).
                val yE = rot[1]
                val yN = rot[4]
                val yUp = rot[7]
                val backE = -rot[2]
                val backN = -rot[5]
                val backUp = -rot[8]
                val (e, n) = if (abs(yUp) < abs(backUp)) yE to yN else backE to backN
                val deg = ((Math.toDegrees(atan2(e, n).toDouble()) + 360) % 360).toFloat()
                _motion.value = _motion.value.copy(headingDeg = deg)
            }
            Sensor.TYPE_GRAVITY -> event.values.copyInto(gravity)
            Sensor.TYPE_GYROSCOPE -> {
                val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).coerceAtLeast(1e-3f)
                val yaw = (event.values[0] * gravity[0] + event.values[1] * gravity[1] + event.values[2] * gravity[2]) / g
                yawSmoothed += 0.2f * (yaw - yawSmoothed)
                _motion.value = _motion.value.copy(yawRateRadS = yawSmoothed)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) _motion.value = _motion.value.copy(headingAccuracy = accuracy)
    }
}
