package com.sappy.speedome.tracking

import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.RouteSampler
import com.sappy.speedome.engine.thinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoutePoint(val lat: Double, val lon: Double, val speedMps: Float, val segment: Int)

/**
 * The route of the current session (live meter or trip) for the Map theme, kept in memory from the
 * engine's fixes with the trip recorder's sampling, so it draws the same line the trip will store.
 * It restarts whenever a new session starts (Start, Stop, Reset).
 */
class LiveRoute(scope: CoroutineScope, engine: StateFlow<EngineState>) {
    private val _points = MutableStateFlow<List<RoutePoint>>(emptyList())
    val points: StateFlow<List<RoutePoint>> = _points.asStateFlow()

    init {
        scope.launch {
            var session: Pair<Any, Long>? = null
            val sampler = RouteSampler()
            engine.collect { s ->
                val key = s.session.kind to s.session.startedUtc
                if (key != session) {
                    session = key
                    sampler.reset()
                    _points.value = emptyList()
                }
                val fix = sampler.take(s) ?: return@collect
                var route = _points.value + RoutePoint(fix.lat, fix.lon, s.filter.v.toFloat(), s.session.segment)
                if (route.size > MAX_POINTS) route = route.thinned { it.segment }
                _points.value = route
            }
        }
    }

    private companion object {
        const val MAX_POINTS = 20_000 // about 5.5 hours of driving before the route thins out
    }
}
