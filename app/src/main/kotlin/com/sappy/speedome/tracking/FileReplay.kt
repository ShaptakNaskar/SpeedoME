package com.sappy.speedome.tracking

import android.os.SystemClock
import com.sappy.speedome.engine.FixEvent
import com.sappy.speedome.engine.replay.Gpx
import com.sappy.speedome.engine.replay.Nmea
import com.sappy.speedome.engine.replay.RawLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Developer replay (docs/plan.md §11): plays a GPX, NMEA or raw-log file into the live engine at
 * its original pace, re-timed to now, while real GPS is paused. Everything downstream (gauges,
 * recording, notification) behaves as on the original drive.
 */
class FileReplay(private val scope: CoroutineScope, private val tracking: TrackingEngine) {
    data class Progress(val running: Boolean = false, val name: String = "", val done: Int = 0, val total: Int = 0, val error: String? = null)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    private var job: Job? = null

    fun play(name: String, text: String) {
        stop()
        val fixes = parse(text)
        if (fixes.size < 2) {
            _progress.value = Progress(name = name, error = "No fixes found in $name")
            return
        }
        _progress.value = Progress(running = true, name = name, total = fixes.size)
        job = scope.launch {
            val t0 = fixes.first().tNanos
            val base = SystemClock.elapsedRealtimeNanos()
            val utcBase = System.currentTimeMillis()
            fixes.forEachIndexed { i, f ->
                val offset = f.tNanos - t0
                val wait = (base + offset - SystemClock.elapsedRealtimeNanos()) / 1_000_000
                if (wait > 0) delay(wait)
                tracking.submit(f.copy(tNanos = base + offset, utcMillis = utcBase + offset / 1_000_000))
                _progress.value = _progress.value.copy(done = i + 1)
            }
            _progress.value = _progress.value.copy(running = false)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (_progress.value.running) _progress.value = _progress.value.copy(running = false)
    }

    companion object {
        /** Detects the format from the content: GPX (XML), NMEA ($-sentences) or the raw-log CSV. */
        fun parse(text: String): List<FixEvent> {
            val head = text.take(4000)
            return when {
                "<gpx" in head -> Gpx.parse(text)
                head.lineSequence().any { it.startsWith("$") } -> Nmea.parse(text)
                else -> RawLog.parse(text)
            }.sortedBy { it.tNanos }
        }
    }
}
