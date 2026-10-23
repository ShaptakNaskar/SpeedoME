package com.sappy.speedome.tracking

import com.sappy.speedome.LimitVibrator
import com.sappy.speedome.monotonicNanos
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
 * with the screen off during a recording (on Android the foreground service keeps vibration allowed).
 */
class SpeedLimitAlert(
    scope: CoroutineScope,
    engine: StateFlow<EngineState>,
    settings: StateFlow<AppSettings>,
    active: StateFlow<Boolean>,
    private val vibrator: LimitVibrator?,
) {
    init {
        if (vibrator != null) {
            scope.launch {
                var alarm = LimitAlarm()
                combine(engine, settings, active) { s, st, on ->
                    // Without a fix for a few seconds the speed is stale: go quiet rather than buzz on a guess.
                    val speed = s.filter.output.takeIf { s.quality(monotonicNanos()) != GpsQuality.NONE }
                    speed to st.speedLimit?.takeIf { on && st.limitVibrate }
                }.collect { (speed, limit) ->
                    val next = alarm.update(speed, limit)
                    if (next.hit || next.hz != alarm.hz) vibrator.play(next.hit, next.hz)
                    alarm = next
                }
            }
        }
    }
}
