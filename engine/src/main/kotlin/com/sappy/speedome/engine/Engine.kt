package com.sappy.speedome.engine

import com.sappy.speedome.engine.Tuning.BRIDGE_MAX_HACC
import com.sappy.speedome.engine.Tuning.DEFAULT_HACC
import com.sappy.speedome.engine.Tuning.GAP_S
import com.sappy.speedome.engine.Tuning.MOVING_DRIVE_MPS
import com.sappy.speedome.engine.Tuning.POS_MIN_SPAN_S
import com.sappy.speedome.engine.Tuning.POS_STILL_FACTOR
import com.sappy.speedome.engine.Tuning.POS_WINDOW_S
import com.sappy.speedome.engine.Tuning.STEP_MOVING_WINDOW_S
import com.sappy.speedome.engine.Tuning.STILL_AFTER_S
import kotlin.math.hypot
import kotlin.math.max

/** The pure, deterministic heart of SpeedoME: `state + event → state` (docs/plan.md §4–§5). */
object Engine {
    fun reduce(state: EngineState, event: EngineEvent, settings: EngineSettings): EngineState {
        val s = state.advanceClock(event.tNanos).anchor(event)
        val next = when (event) {
            is FixEvent -> s.onFix(event, settings)
            is StepCountEvent -> s.onStepCount(event)
            is StepDetectedEvent -> s.onStepDetected(event)
            is TickEvent -> s.onTick(event, settings)
            is CommandEvent -> s.onCommand(event)
        }
        return when (event) {
            is FixEvent, is TickEvent -> next.sampleTrend(event.tNanos).copy(
                range = AutoRange.update(
                    next.range, next.filter.output * 3.6, if (next.filter.zero) 0.0 else next.filter.a * 3.6,
                    event.tNanos, settings.mode, settings.autoRange,
                ),
            )
            is CommandEvent -> if (event.command.keepsRange) {
                next
            } else {
                next.copy(range = AutoRange.initial(settings.mode, settings.autoRange)) // a fresh session starts small
            }
            else -> next
        }
    }

    fun reduceAll(state: EngineState, events: Iterable<EngineEvent>, settings: EngineSettings): EngineState =
        events.fold(state) { s, e -> reduce(s, e, settings) }
}

private val Command.keepsRange
    get() = this == Command.Pause || this == Command.Resume || this is Command.SetTarget || this == Command.ClearTarget

private fun EngineState.anchor(e: EngineEvent): EngineState {
    val utc = when (e) {
        is FixEvent -> e.utcMillis
        is TickEvent -> e.utcMillis
        is CommandEvent -> e.utcMillis
        else -> return this
    }
    return copy(clock = UtcAnchor(e.tNanos, utc))
}

/** One trend sample per second while not paused, covering the last [Tuning.TREND_WINDOW_S]. */
private fun EngineState.sampleTrend(t: Long): EngineState {
    if (session.paused) return this
    val last = trend.lastOrNull()
    if (last != null && (t - last.tNanos) / 1e9 < 1.0) return this
    val window = Tuning.TREND_WINDOW_S
    return copy(trend = (trend + TrendSample(t, stats.distanceM)).filter { (t - it.tNanos) / 1e9 <= window })
}

private data class Measurement(val z: Double, val r: Double, val source: SpeedSource)

private fun EngineState.advanceClock(t: Long): EngineState {
    val last = lastEventNanos ?: return copy(lastEventNanos = t)
    if (t <= last) return this
    val add = if (session.paused) 0.0 else (t - last) / 1e9
    return copy(lastEventNanos = t, stats = stats.copy(elapsedS = stats.elapsedS + add))
}

private fun EngineState.onFix(e: FixEvent, cfg: EngineSettings): EngineState {
    val t = e.tNanos
    if (lastFix != null && t <= lastFix.tNanos) return this // stale or out of order
    val hAcc = e.hAcc?.toDouble() ?: DEFAULT_HACC
    val sample = PosSample(t, e.lat, e.lon, hAcc)
    val window = (recent + sample).filter { (t - it.tNanos) / 1e9 <= POS_WINDOW_S }
    val m = when {
        e.speed != null && e.speedAcc != null -> {
            val sd = max(0.1, e.speedAcc.toDouble())
            Measurement(e.speed.toDouble(), sd * sd, SpeedSource.DOPPLER)
        }
        e.speed != null -> {
            val sd = max(0.5, 0.15 * hAcc)
            Measurement(e.speed.toDouble(), sd * sd, SpeedSource.DOPPLER_NO_ACCURACY)
        }
        else -> positionSpeed(window, t)
    }
    var s = copy(
        recent = window,
        lastFix = LastFix(
            tNanos = t, utcMillis = e.utcMillis, lat = e.lat, lon = e.lon, hAcc = hAcc,
            altM = e.altM, vAcc = e.vAcc?.toDouble(), bearing = e.bearing?.toDouble(),
            rawSpeed = e.speed?.toDouble(), speedAcc = e.speedAcc?.toDouble(),
            source = m?.source ?: SpeedSource.POSITION, isMock = e.isMock,
        ),
    )
    var accepted = false
    if (m != null) {
        val u = s.filter.update(m.z, m.r, t)
        s = s.copy(filter = u.filter, lastSource = if (u.accepted) m.source else s.lastSource)
        accepted = u.accepted
    }
    if (cfg.mode == Mode.STEP && m?.source == SpeedSource.DOPPLER) s = s.learnStride(m.z, t)
    return s.integrate(t, sample.takeIf { hAcc < BRIDGE_MAX_HACC }, accepted, cfg)
}

/** Fallback speed from displacement over the last few seconds; movement inside the noise reads as 0. */
private fun positionSpeed(window: List<PosSample>, t: Long): Measurement? {
    val oldest = window.first()
    val newest = window.last()
    val dt = (t - oldest.tNanos) / 1e9
    if (dt < POS_MIN_SPAN_S) return null
    val d = Geo.distanceM(oldest.lat, oldest.lon, newest.lat, newest.lon)
    val z = if (d < POS_STILL_FACTOR * hypot(oldest.hAcc, newest.hAcc)) 0.0 else d / dt
    val sd = (oldest.hAcc + newest.hAcc) * 0.35 / dt
    return Measurement(z, sd * sd + 0.3, SpeedSource.POSITION)
}

/** Adds distance and moving time up to [t] using the filtered speed; bridges long gaps with a straight line. */
private fun EngineState.integrate(t: Long, goodPos: PosSample?, accepted: Boolean, cfg: EngineSettings): EngineState {
    val out = filter.output
    val lm = lastMeasureNanos
    if (lm != null && t <= lm) return copy(lastGood = goodPos ?: lastGood)
    if (session.paused) return copy(lastMeasureNanos = t, lastMeasureOut = out, lastGood = goodPos ?: lastGood)
    var st = stats
    if (lm != null) {
        val dt = (t - lm) / 1e9
        if (dt <= GAP_S) {
            val vm = (lastMeasureOut + out) / 2
            st = st.copy(distanceM = st.distanceM + vm * dt, movingS = st.movingS + if (isMoving(vm, t, cfg)) dt else 0.0)
        } else if (goodPos != null && lastGood != null) {
            val d = Geo.distanceM(lastGood.lat, lastGood.lon, goodPos.lat, goodPos.lon)
            st = st.copy(
                distanceM = st.distanceM + d,
                movingS = st.movingS + if (d / dt > MOVING_DRIVE_MPS) dt else 0.0,
                gaps = st.gaps + 1,
            )
        }
    }
    if (accepted) st = st.copy(maxMps = max(st.maxMps, out))
    return copy(stats = st, lastMeasureNanos = t, lastMeasureOut = out, lastGood = goodPos ?: lastGood)
}

private fun EngineState.isMoving(vm: Double, t: Long, cfg: EngineSettings): Boolean = when (cfg.mode) {
    Mode.DRIVE -> vm > MOVING_DRIVE_MPS
    Mode.STEP -> vm > 1.0 / 3.6 || (steps.lastStepNanos?.let { (t - it) / 1e9 <= STEP_MOVING_WINDOW_S } ?: false)
}

private fun EngineState.learnStride(chipSpeed: Double, t: Long): EngineState {
    val cadence = cadenceSpm(t)
    if (cadence <= 0 || chipSpeed < 0.5) return this
    val observed = chipSpeed / (cadence / 60.0)
    val k = Tuning.STRIDE_LEARN_RATE
    val st = steps
    return copy(
        steps = if (cadence < Tuning.RUN_CADENCE_SPM) {
            st.copy(strideWalkM = (st.strideWalkM + k * (observed - st.strideWalkM)).coerceIn(0.3, 2.5))
        } else {
            st.copy(strideRunM = (st.strideRunM + k * (observed - st.strideRunM)).coerceIn(0.3, 2.5))
        },
    )
}

private fun EngineState.onStepCount(e: StepCountEvent): EngineState {
    val last = steps.lastCounter
    val rebooted = last != null && e.counterTotal < last
    val delta = if (last == null || rebooted) 0L else e.counterTotal - last
    val window = Tuning.CADENCE_WINDOW_S
    val sample = CounterSample(e.tNanos, e.counterTotal)
    val samples = if (rebooted) listOf(sample) else (steps.counterSamples + sample).filter { (e.tNanos - it.tNanos) / 1e9 <= window }
    return copy(
        steps = steps.copy(lastCounter = e.counterTotal, counterSamples = samples, lastStepNanos = if (delta > 0) e.tNanos else steps.lastStepNanos),
        stats = if (session.paused || delta == 0L) stats else stats.copy(steps = stats.steps + delta),
    )
}

private fun EngineState.onStepDetected(e: StepDetectedEvent): EngineState {
    val window = Tuning.CADENCE_WINDOW_S
    val times = (steps.detectorTimes + e.tNanos).filter { (e.tNanos - it) / 1e9 <= window }
    // Without a step counter, the detector is the only step source.
    val countIt = steps.lastCounter == null && !session.paused
    return copy(
        steps = steps.copy(detectorTimes = times, lastStepNanos = e.tNanos),
        stats = if (countIt) stats.copy(steps = stats.steps + 1) else stats,
    )
}

/** In step mode, cadence × stride is a speed measurement; standing still with weak GPS reads as 0. */
private fun EngineState.onTick(e: TickEvent, cfg: EngineSettings): EngineState {
    if (cfg.mode != Mode.STEP) return this
    val t = e.tNanos
    val cadence = cadenceSpm(t)
    val gpsGood = quality(t) == GpsQuality.GOOD
    val sinceStep = steps.lastStepNanos?.let { (t - it) / 1e9 } ?: Double.MAX_VALUE
    val z = when {
        cadence > 0 -> cadence / 60.0 * strideFor(cadence)
        sinceStep >= STILL_AFTER_S && !gpsGood -> 0.0
        else -> return this
    }
    val u = filter.update(z, if (gpsGood) 0.36 else 0.09, t)
    return copy(filter = u.filter, lastSource = if (u.accepted) SpeedSource.STEPS else lastSource)
        .integrate(t, null, u.accepted, cfg)
}

private fun EngineState.onCommand(e: CommandEvent): EngineState = when (e.command) {
    Command.Reset -> freshSession(session.kind, e.utcMillis)
    Command.StartTrip -> freshSession(SessionKind.TRIP, e.utcMillis)
    Command.StopTrip -> freshSession(SessionKind.LIVE, e.utcMillis)
    Command.Pause -> copy(session = session.copy(paused = true))
    Command.Resume -> if (!session.paused) {
        this
    } else {
        // The pause is not a stop, so the arrival trend restarts rather than averaging it in.
        copy(session = session.copy(paused = false, segment = session.segment + 1), lastMeasureNanos = null, lastGood = null, trend = emptyList())
    }
    is Command.SetTarget -> copy(target = Target(e.command.distanceM, stats.distanceM, e.command.arriveByUtc))
    Command.ClearTarget -> copy(target = null)
}

/** A new session zeroes distance; a set target carries over and counts from the new zero. */
private fun EngineState.freshSession(kind: SessionKind, utc: Long) = copy(
    session = Session(kind = kind, startedUtc = utc), stats = Stats(), lastMeasureNanos = null, lastGood = null,
    trend = emptyList(), target = target?.copy(startDistanceM = 0.0),
)
