package com.sappy.speedome.tracking

import com.sappy.speedome.utcMillis
import com.sappy.speedome.monotonicNanos
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.data.SessionStates
import com.sappy.speedome.data.TripDao
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.Snapshot
import com.sappy.speedome.engine.RouteSampler
import com.sappy.speedome.engine.resumedAfter
import com.sappy.speedome.engine.lapStats
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.sappy.speedome.killProcess
import com.sappy.speedome.gauges.fmt
import kotlinx.coroutines.IO
import kotlin.math.max

/**
 * Persists a recording trip (docs/plan.md §7): route points at most once a second and, every second,
 * one transaction with the new points plus the session totals and engine snapshot. On launch it
 * resumes an unfinished trip automatically (gap < 30 min) or offers a choice. The live meter is never
 * stored: it ends when the app closes.
 */
class SessionRecorder(
    private val scope: CoroutineScope,
    private val dao: TripDao,
    private val tracking: TrackingEngine,
    private val settings: StateFlow<AppSettings>,
) {
    data class ResumeOffer(val session: SessionEntity, val gapMillis: Long)

    /** A saved trip awaiting its summary sheet; [sectionOf] is set for a section of that trip. */
    data class Finished(val id: Long, val sectionOf: Long? = null)

    private val _offer = MutableStateFlow<ResumeOffer?>(null)
    val offer: StateFlow<ResumeOffer?> = _offer.asStateFlow()

    /** Trips and sections that just finished, oldest first; the Speed screen shows a summary sheet for the first. */
    private val _finished = MutableStateFlow<List<Finished>>(emptyList())
    val finished: StateFlow<List<Finished>> = _finished.asStateFlow()

    /** The gap (ms) after an automatic resume, until the UI has shown it. */
    private val _resumed = MutableStateFlow<Long?>(null)
    val resumed: StateFlow<Long?> = _resumed.asStateFlow()

    /** True once the launch-time restore is done and applied to the engine (a trip was resumed, offered or there was none). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** True while a trip row is open; the per-second save loop sleeps otherwise. */
    private val recording = MutableStateFlow(false)

    private data class Key(val kind: SessionKind, val startedUtc: Long)

    private val io = Dispatchers.IO.limitedParallelism(1)
    private val lock = Mutex()
    private var current: SessionEntity? = null
    private var currentKey: Key? = null
    private var adopt: Pair<Key, Long>? = null
    private var lastState: EngineState? = null
    private val sampler = RouteSampler()
    private val pending = ArrayList<PointEntity>()
    private var dirty = false

    fun start() {
        scope.launch(io) {
            lock.withLock { restoreOrOffer() }
            tracking.awaitIdle()
            _ready.value = true
            launch { tracking.state.collect { s -> lock.withLock { onState(s) } } }
            while (isActive) {
                recording.first { it }
                delay(1000)
                lock.withLock { flush() }
            }
        }
    }

    fun startTrip() = tracking.command(com.sappy.speedome.engine.Command.StartTrip)

    fun stopTrip() = tracking.command(com.sappy.speedome.engine.Command.StopTrip)

    fun reset() = tracking.command(com.sappy.speedome.engine.Command.Reset)

    /** Starts a section of the recording trip, or ends the open one (it is then saved and summarised). */
    fun toggleLap(open: Boolean) =
        tracking.command(if (open) com.sappy.speedome.engine.Command.EndLap else com.sappy.speedome.engine.Command.StartLap)

    /** Keeps a just-saved section linked to its trip, or makes it a trip of its own. */
    fun linkSection(id: Long, tripId: Long?) {
        scope.launch(io) { dao.setParent(id, tripId) }
    }

    fun clearResumedNotice() {
        _resumed.value = null
    }

    /**
     * Developer test: saves, then kills the process like Android would. With [ageMinutes] > 0 the saved
     * session looks that much older, so the next launch shows the resume offer instead of auto-resuming.
     */
    fun debugKill(ageMinutes: Int) {
        scope.launch(io) {
            lock.withLock {
                flush(force = true)
                if (ageMinutes > 0) current?.let { dao.updateSession(it.copy(updatedAt = utcMillis() - ageMinutes * 60_000L)) }
                killProcess()
            }
        }
    }

    fun dismissSummary(id: Long) {
        _finished.update { list -> list.filter { it.id != id } }
    }

    fun discardTrip(id: Long) {
        dismissSummary(id)
        scope.launch(io) { dao.deleteSession(id) }
    }

    /** "Resume" on the ≥ 30 min offer. */
    fun acceptOffer() = handleOffer { o ->
        val snap = o.session.engineSnapshot?.let(Snapshot::decodeOrNull)
        if (snap == null) finishStale(o.session) else adoptAndRestore(o.session, snap, utcMillis() - o.session.updatedAt)
    }

    /** "Save & finish" on a trip offer. */
    fun finishOffer() = handleOffer { o -> _finished.update { it + Finished(finishStale(o.session)) } }

    /** "Discard" on the offer. */
    fun discardOffer() = handleOffer { o -> dao.deleteSession(o.session.id) }

    private fun handleOffer(block: suspend (ResumeOffer) -> Unit) {
        val o = _offer.value ?: return
        _offer.value = null
        scope.launch(io) { lock.withLock { block(o) } }
    }

    private suspend fun restoreOrOffer() {
        val (trips, live) = dao.allUnfinished().partition { it.kind == SessionKind.TRIP.name }
        live.forEach { dao.deleteSession(it.id) } // live meters saved by versions before 0.10
        val row = trips.maxByOrNull { it.updatedAt } ?: return
        for (other in trips) if (other.id != row.id) finishStale(other)
        val snap = row.engineSnapshot?.let(Snapshot::decodeOrNull)
        if (snap == null) {
            finishStale(row)
            return
        }
        val gap = max(0L, utcMillis() - row.updatedAt)
        if (gap < AUTO_RESUME_MS) {
            adoptAndRestore(row, snap, gap)
            if (gap >= 10_000) _resumed.value = gap
        } else {
            _offer.value = ResumeOffer(row, gap)
        }
    }

    private fun adoptAndRestore(row: SessionEntity, snap: EngineState, gapMillis: Long) {
        val resumed = snap.resumedAfter(gapMillis, monotonicNanos())
        adopt = Key(resumed.session.kind, resumed.session.startedUtc) to row.id
        tracking.restore(resumed)
    }

    private suspend fun onState(s: EngineState) {
        val key = Key(s.session.kind, s.session.startedUtc)
        if (key != currentKey) {
            closeCurrent()
            currentKey = key
            // The restored session may arrive after the engine's initial blank state, so keep the
            // adoption pending until its own key shows up.
            val adopted = adopt?.takeIf { it.first == key }
            if (adopted != null) adopt = null
            current = adopted?.let { dao.session(it.second) }
            recording.value = current != null
            sampler.reset(seenFix = if (current != null) s.lastFix?.tNanos else null)
        } else if (s.lap == null && lastState?.lap != null) {
            current?.let { saveLap(it, lastState!!) } // the section just ended
        }
        lastState = s
        if (s.session.kind != SessionKind.TRIP) return // the live meter is never stored
        if (current == null) {
            val now = utcMillis()
            val row = SessionEntity(
                kind = s.session.kind.name,
                state = SessionStates.ACTIVE,
                mode = settings.value.mode.name,
                startedAt = s.session.startedUtc.takeIf { it > 0 } ?: now,
                updatedAt = now,
            )
            current = row.copy(id = dao.insertSession(row))
            recording.value = true
        }
        val f = sampler.take(s) ?: return
        pending += PointEntity(
            sessionId = current!!.id, t = f.utcMillis, segment = s.session.segment, lat = f.lat, lon = f.lon,
            alt = f.altM, hAcc = f.hAcc.toFloat(), vAcc = f.vAcc?.toFloat(), rawSpeedMps = f.rawSpeed?.toFloat(),
            speedMps = s.filter.output.toFloat(), speedAcc = f.speedAcc?.toFloat(), source = f.source.name,
            bearing = f.bearing?.toFloat(), isMock = f.isMock,
        )
        dirty = true
    }

    /** The recorded trip ended: finalise it. */
    private suspend fun closeCurrent() {
        val row = current ?: return
        val s = lastState
        if (s?.lap != null) saveLap(row, s) // stopping the trip ends its open section too
        current = null
        recording.value = false
        if (pending.isNotEmpty()) dao.insertPoints(pending.toList())
        pending.clear()
        val now = utcMillis()
        dao.updateSession(
            withStats(row, s).copy(
                state = SessionStates.FINISHED, endedAt = now, updatedAt = now,
                engineSnapshot = null, thumbnail = thumbnail(dao.points(row.id)),
            ),
        )
        _finished.update { it + Finished(row.id) }
        dirty = false
    }

    /** Saves the section open in [s] as a finished trip linked to [trip], with its stretch of the route. */
    private suspend fun saveLap(trip: SessionEntity, s: EngineState) {
        val lap = s.lap ?: return
        val st = s.lapStats() ?: return
        if (pending.isNotEmpty()) dao.insertPoints(pending.toList()) // the trip row catches up on the next flush
        pending.clear()
        val end = utcMillis()
        val points = dao.pointsBetween(trip.id, lap.startUtc, end)
        val id = dao.insertSection(
            SessionEntity(
                kind = SessionKind.TRIP.name, state = SessionStates.FINISHED, mode = trip.mode,
                startedAt = lap.startUtc, endedAt = end, updatedAt = end,
                distanceM = st.distanceM, movingS = st.movingS, elapsedS = st.elapsedS, maxMps = st.maxMps, steps = st.steps,
                thumbnail = thumbnail(points), parentId = trip.id,
            ),
            points,
        )
        _finished.update { it + Finished(id, sectionOf = trip.id) }
    }

    private suspend fun flush(force: Boolean = false) {
        val row = current ?: return
        val s = lastState ?: return
        if (!force && !dirty && pending.isEmpty() && utcMillis() - row.updatedAt < 5_000) return
        val updated = withStats(row, s).copy(
            state = if (s.session.paused) SessionStates.PAUSED else SessionStates.ACTIVE,
            mode = settings.value.mode.name,
            updatedAt = utcMillis(),
            engineSnapshot = Snapshot.encode(s),
        )
        dao.save(updated, pending.toList())
        pending.clear()
        current = updated
        dirty = false
    }

    /** Finishes a trip that was abandoned (or "Save & finish") with its last saved totals. */
    private suspend fun finishStale(row: SessionEntity): Long {
        dao.updateSession(
            row.copy(state = SessionStates.FINISHED, endedAt = row.updatedAt, engineSnapshot = null, thumbnail = thumbnail(dao.points(row.id))),
        )
        return row.id
    }

    private fun withStats(row: SessionEntity, s: EngineState?): SessionEntity = if (s == null) {
        row
    } else {
        row.copy(
            distanceM = s.stats.distanceM, movingS = s.stats.movingS, elapsedS = s.stats.elapsedS,
            maxMps = s.stats.maxMps, steps = s.stats.steps,
        )
    }

    companion object {
        const val AUTO_RESUME_MS = 30 * 60 * 1000L

        /** Decimates a route to at most 64 points for list thumbnails. */
        fun thumbnail(points: List<PointEntity>): String? {
            if (points.size < 2) return null
            val step = max(1, points.size / 64)
            return points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
                .joinToString(";") { "${fmt(it.lat, 5)},${fmt(it.lon, 5)}" }
        }
    }
}
