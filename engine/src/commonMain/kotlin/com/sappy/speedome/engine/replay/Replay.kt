package com.sappy.speedome.engine.replay

import com.sappy.speedome.engine.Engine
import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.EngineSettings
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.FixEvent
import com.sappy.speedome.engine.TickEvent

/** Feeds recorded events through [Engine.reduce], the same way the tracking service does. */
object Replay {
    data class Row(val tNanos: Long, val rawMps: Double?, val filteredMps: Double, val rejectTotal: Int)

    data class Result(val state: EngineState, val rows: List<Row>)

    fun run(events: List<EngineEvent>, settings: EngineSettings = EngineSettings(), start: EngineState = EngineState()): Result {
        val rows = ArrayList<Row>(events.size)
        var s = start
        for (e in withTicks(events)) {
            s = Engine.reduce(s, e, settings)
            if (e is FixEvent) rows += Row(e.tNanos, e.speed?.toDouble(), s.filter.output, s.filter.rejectTotal)
        }
        return Result(s, rows)
    }

    /** Inserts ~1 Hz ticks, like the service's housekeeping timer, wherever the input has none. */
    fun withTicks(events: List<EngineEvent>): List<EngineEvent> {
        if (events.isEmpty() || events.any { it is TickEvent }) return events
        val out = ArrayList<EngineEvent>(events.size * 2)
        var nextTick = events.first().tNanos
        var utc = (events.first() as? FixEvent)?.utcMillis ?: 0L
        for (e in events) {
            while (nextTick <= e.tNanos) {
                out += TickEvent(nextTick, utc)
                nextTick += 1_000_000_000L
            }
            if (e is FixEvent) utc = e.utcMillis
            out += e
        }
        return out
    }
}
