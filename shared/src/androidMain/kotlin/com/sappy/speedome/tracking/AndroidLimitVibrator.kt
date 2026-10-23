package com.sappy.speedome.tracking

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sappy.speedome.LimitVibrator

/**
 * The speed-limit buzz on Android: one waveform per change, with alarm usage so it still buzzes with
 * the ringer on silent (and, during a recording, with the screen off: the foreground service keeps
 * vibration allowed).
 */
class AndroidLimitVibrator private constructor(private val v: Vibrator) : LimitVibrator {
    companion object {
        fun create(context: Context): AndroidLimitVibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }?.let(::AndroidLimitVibrator)

        const val HIT_MS = 350L
        const val PULSE_MS = 80L
        const val MIN_GAP_MS = 150L
    }

    /** One waveform per change: the reaching buzz (if [hit]) followed by pulses at [hz], repeating until replaced. */
    override fun play(hit: Boolean, hz: Int) {
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

}
