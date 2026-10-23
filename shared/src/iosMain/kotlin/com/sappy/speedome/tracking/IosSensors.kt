package com.sappy.speedome.tracking

import com.sappy.speedome.AppContainer
import com.sappy.speedome.LimitVibrator
import com.sappy.speedome.LocationSources
import com.sappy.speedome.MotionSensors
import com.sappy.speedome.engine.Mode
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.StepCountEvent
import com.sappy.speedome.monotonicNanos
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMAttitudeReferenceFrameXTrueNorthZVertical
import platform.CoreMotion.CMDeviceMotion
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyHigh
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyLow
import platform.CoreMotion.CMMagneticFieldCalibrationAccuracyMedium
import platform.CoreMotion.CMMotionManager
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import kotlin.math.sqrt

/**
 * Which sensors run on iOS, like Android's SourceManager: GPS and the pedometer while tracking is
 * active and nothing simulated stands in. During a trip GPS also runs in the background, and the
 * container counts that as "recording", so tracking stays active when the app leaves the screen.
 */
class IosSources(app: AppContainer, private val location: IosLocation, scope: CoroutineScope) : LocationSources {
    private val pedometer = CMPedometer()
    private var steps = false
    private val replaying = MutableStateFlow(false)
    private val permissionEpoch = MutableStateFlow(0)

    private data class Wanted(val active: Boolean, val simulated: Boolean, val mode: Mode, val epoch: Int)

    init {
        scope.launch(Dispatchers.Main) {
            combine(app.trackingActive, app.settings.state, replaying, permissionEpoch) { on, s, replay, epoch ->
                Wanted(on, (s.devMode && s.simulator) || replay, s.mode, epoch)
            }.distinctUntilChanged().collect { w ->
                if (w.active && !w.simulated) location.start() else location.stop()
                setSteps(w.active && !w.simulated && w.mode == Mode.STEP && CMPedometer.isStepCountingAvailable(), app)
            }
        }
        scope.launch(Dispatchers.Main) {
            app.tracking.state.map { it.session.kind == SessionKind.TRIP }.distinctUntilChanged().collect { trip ->
                location.setBackground(trip)
                app.recordingService.value = trip
            }
        }
        scope.launch(Dispatchers.Main) { location.authorization.collect { permissionsChanged() } }
    }

    private fun setSteps(on: Boolean, app: AppContainer) {
        if (on == steps) return
        steps = on
        if (on) {
            // Cumulative steps since start, like Android's step counter.
            pedometer.startPedometerUpdatesFromDate(NSDate()) { data, _ ->
                val total = data?.numberOfSteps?.longValue ?: return@startPedometerUpdatesFromDate
                app.tracking.submit(StepCountEvent(monotonicNanos(), total))
            }
        } else {
            pedometer.stopPedometerUpdates()
        }
    }

    override fun setNmeaEnabled(on: Boolean) {} // iOS doesn't expose NMEA or satellites

    override fun permissionsChanged() {
        permissionEpoch.value++
    }

    override fun setVisible(visible: Boolean) {}

    override fun setReplaying(on: Boolean) {
        replaying.value = on
    }
}

/**
 * Compass heading, yaw rate and horizontal acceleration from CoreMotion's fused device motion,
 * referenced to north with Z vertical, so it doesn't depend on how the phone is mounted.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMotion : MotionSensors {
    private val manager = CMMotionManager()
    private val _motion = MutableStateFlow(Motion())
    override val motion: StateFlow<Motion> = _motion.asStateFlow()
    private var holders = 0
    private var visible = true
    private var running = false

    override fun acquire() {
        holders++
        update()
    }

    override fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        update()
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        update()
    }

    private fun update() {
        val want = holders > 0 && visible && manager.deviceMotionAvailable
        if (want == running) return
        running = want
        if (!want) {
            manager.stopDeviceMotionUpdates()
            _motion.value = Motion()
            return
        }
        manager.deviceMotionUpdateInterval = 1.0 / 30
        val trueNorth = CMMotionManager.availableAttitudeReferenceFrames() and CMAttitudeReferenceFrameXTrueNorthZVertical != 0uL
        val frame = if (trueNorth) CMAttitudeReferenceFrameXTrueNorthZVertical else CMAttitudeReferenceFrameXMagneticNorthZVertical
        manager.startDeviceMotionUpdatesUsingReferenceFrame(frame, NSOperationQueue.mainQueue) { dm, _ -> dm?.let(::onMotion) }
    }

    private fun onMotion(dm: CMDeviceMotion) {
        // userAcceleration is in g, in the device frame; the attitude matrix's transpose takes it to
        // the reference frame (X north, Y west, Z up).
        val (ax, ay, az) = dm.userAcceleration.useContents { Triple(x, y, z) }
        val (north, west) = dm.attitude.rotationMatrix.useContents {
            (ax * m11 + ay * m21 + az * m31) to (ax * m12 + ay * m22 + az * m32)
        }
        // Yaw rate about the vertical: the gyro along "up" (gravity points down in CoreMotion).
        val (gx, gy, gz) = dm.gravity.useContents { Triple(x, y, z) }
        val g = sqrt(gx * gx + gy * gy + gz * gz).takeIf { it > 0.1 } ?: 1.0
        val yaw = -dm.rotationRate.useContents { (x * gx + y * gy + z * gz) / g }
        val accuracy = when (dm.magneticField.useContents { accuracy }) {
            CMMagneticFieldCalibrationAccuracyHigh -> 3
            CMMagneticFieldCalibrationAccuracyMedium -> 2
            CMMagneticFieldCalibrationAccuracyLow -> 1
            else -> 0
        }
        val prev = _motion.value
        _motion.value = Motion(
            headingDeg = dm.heading.takeIf { it >= 0 }?.toFloat(),
            headingAccuracy = accuracy,
            yawRateRadS = (prev.yawRateRadS * .8f + yaw.toFloat() * .2f),
            accelEast = (-west * G).toFloat(),
            accelNorth = (north * G).toFloat(),
        )
    }

    private companion object {
        const val G = 9.80665
    }
}

/**
 * The speed-limit buzz with iOS haptics: a strong notification buzz on reaching the limit, then
 * heavy taps at [hz]. iOS only plays haptics while SpeedoME is on screen.
 */
class IosLimitVibrator(private val scope: CoroutineScope) : LimitVibrator {
    private var job: Job? = null

    override fun play(hit: Boolean, hz: Int) {
        job?.cancel()
        if (!hit && hz == 0) return
        job = scope.launch(Dispatchers.Main) {
            if (hit) {
                UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
                delay(350)
            }
            if (hz > 0) {
                val tap = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
                while (isActive) {
                    tap.impactOccurred()
                    delay(1000L / hz)
                }
            }
        }
    }
}
