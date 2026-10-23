package com.sappy.speedome.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.sappy.speedome.engine.StepCountEvent
import com.sappy.speedome.engine.StepDetectedEvent
import kotlin.math.abs

/** Hardware step counter (exact totals) and step detector (live cadence) for step mode. */
class StepSource(context: Context, private val tracking: TrackingEngine) : SensorEventListener {
    private val sm = context.applicationContext.getSystemService(SensorManager::class.java)
    private val counter: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val detector: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private var running = false

    val available: Boolean get() = counter != null || detector != null

    /** Caller has checked the activity-recognition permission. */
    fun start() {
        if (running || !available) return
        counter?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        detector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        running = true
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val t = sensorTime(event.timestamp)
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> tracking.submit(StepCountEvent(t, event.values[0].toLong()))
            Sensor.TYPE_STEP_DETECTOR -> tracking.submit(StepDetectedEvent(t))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Sensor timestamps share elapsedRealtimeNanos on almost every phone; fall back to "now" if one doesn't. */
internal fun sensorTime(timestamp: Long): Long {
    val now = SystemClock.elapsedRealtimeNanos()
    return if (abs(now - timestamp) > 30_000_000_000L) now else timestamp
}
