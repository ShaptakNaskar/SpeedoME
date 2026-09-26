package com.sappy.speedome.tracking

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.GpsQuality
import com.sappy.speedome.engine.LimitAlarm
import com.sappy.speedome.engine.quality
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Vibrates at the speed limit (docs/plan.md §6): one long buzz on reaching it, then short pulses that
 * quicken the further over you go ([LimitAlarm]: 1 Hz up to 5 % over, 2 Hz to 10 %, 3 Hz to 20 %,
 * 4 Hz beyond). It follows the engine's filtered speed while tracking is active, so it keeps working
 * with the screen off during a recording (the foreground service keeps vibration allowed).
 */
class SpeedLimitAlert(
    context: Context,
    scope: CoroutineScope,
    engine: StateFlow<EngineState>,
    settings: StateFlow<AppSettings>,
    active: StateFlow<Boolean>,
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }?.takeIf { it.hasVibrator() }

    init {
        if (vibrator != null) {
            scope.launch {
                var alarm = LimitAlarm()
                combine(engine, settings, active) { s, st, on ->
                    // Without a fix for a few seconds the speed is stale: go quiet rather than buzz on a guess.
                    val speed = s.filter.output.takeIf { s.quality(SystemClock.elapsedRealtimeNanos()) != GpsQuality.NONE }
                    speed to st.speedLimit?.takeIf { on && st.limitVibrate }
                }.collect { (speed, limit) ->
                    val next = alarm.update(speed, limit)
                    if (next.hit || next.hz != alarm.hz) play(next.hit, next.hz)
                    alarm = next
                }
            }
        }
    }

    /** One waveform per change: the reaching buzz (if [hit]) followed by pulses at [hz], repeating until replaced. */
    private fun play(hit: Boolean, hz: Int) {
        val v = vibrator ?: return
        if (!hit && hz == 0) {
            v.cancel()
            return
        }
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        fun segment(ms: Long, on: Boolean) {
            timings += ms
            amplitudes += if (on) 255 else 0
        }
        segment(0, false) // waveforms start with an off time
        if (hit) segment(HIT_MS, true)
        var repeat = -1
        if (hz > 0) {
            val period = 1000L / hz
            if (hit) segment((period - HIT_MS).coerceAtLeast(MIN_GAP_MS), false)
            repeat = timings.size
            segment(PULSE_MS, true)
            segment(period - PULSE_MS, false)
        }
        val effect = VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), repeat)
        // Alarm usage: an alert the user asked for, which still buzzes with the ringer on silent.
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private companion object {
        const val HIT_MS = 350L
        const val PULSE_MS = 80L
        const val MIN_GAP_MS = 150L
    }
}
