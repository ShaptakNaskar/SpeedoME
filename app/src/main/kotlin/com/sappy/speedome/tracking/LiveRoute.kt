package com.sappy.speedome.tracking

import com.sappy.speedome.engine.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoutePoint(val lat: Double, val lon: Double, val speedMps: Float, val segment: Int)

/**
 * The route of the current session (live meter or trip) for the Map theme, kept in memory from the
 * engine's fixes. It restarts whenever a new session starts (Start, Stop, Reset).
 */
class LiveRoute(scope: CoroutineScope, engine: StateFlow<EngineState>) {
    private val _points = MutableStateFlow<List<RoutePoint>>(emptyList())
    val points: StateFlow<List<RoutePoint>> = _points.asStateFlow()

    init {
        scope.launch {
            var session: Pair<Any, Long>? = null
            var lastFix = Long.MIN_VALUE
            engine.collect { s ->
                val key = s.session.kind to s.session.startedUtc
                if (key != session) {
                    session = key
                    _points.value = emptyList()
                }
                val fix = s.lastFix ?: return@collect
                if (fix.tNanos == lastFix || s.session.paused) return@collect
                lastFix = fix.tNanos
                val p = RoutePoint(fix.lat, fix.lon, s.filter.v.toFloat(), s.session.segment)
                _points.value = (_points.value + p).takeLast(MAX_POINTS)
            }
        }
    }

    private companion object {
        const val MAX_POINTS = 20_000 // about 5.5 hours at 1 Hz
    }
}
