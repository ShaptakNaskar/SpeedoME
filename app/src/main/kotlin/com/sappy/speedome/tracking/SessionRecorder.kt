package com.sappy.speedome.tracking

import android.os.SystemClock
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.data.SessionStates
import com.sappy.speedome.data.TripDao
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.Geo
import com.sappy.speedome.engine.SessionKind
import com.sappy.speedome.engine.Snapshot
import com.sappy.speedome.engine.resumedAfter
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
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

    private val _offer = MutableStateFlow<ResumeOffer?>(null)
    val offer: StateFlow<ResumeOffer?> = _offer.asStateFlow()

    /** Id of a trip that just finished; the Speed screen shows its summary sheet. */
    private val _finished = MutableStateFlow<Long?>(null)
    val finished: StateFlow<Long?> = _finished.asStateFlow()

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
    private var lastSeenFix = Long.MIN_VALUE
    private var lastStoredFix: Long? = null
    private var lastStoredPos: Pair<Double, Double>? = null
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
                if (ageMinutes > 0) current?.let { dao.updateSession(it.copy(updatedAt = System.currentTimeMillis() - ageMinutes * 60_000L)) }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun dismissSummary() {
        _finished.value = null
    }

    fun discardTrip(id: Long) {
        if (_finished.value == id) _finished.value = null
        scope.launch(io) { dao.deleteSession(id) }
    }

    /** "Resume" on the ≥ 30 min offer. */
    fun acceptOffer() = handleOffer { o ->
        val snap = o.session.engineSnapshot?.let(Snapshot::decodeOrNull)
        if (snap == null) finishStale(o.session) else adoptAndRestore(o.session, snap, System.currentTimeMillis() - o.session.updatedAt)
    }

    /** "Save & finish" on a trip offer. */
    fun finishOffer() = handleOffer { o -> _finished.value = finishStale(o.session) }

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
        val gap = max(0L, System.currentTimeMillis() - row.updatedAt)
        if (gap < AUTO_RESUME_MS) {
            adoptAndRestore(row, snap, gap)
            if (gap >= 10_000) _resumed.value = gap
        } else {
            _offer.value = ResumeOffer(row, gap)
        }
    }

    private fun adoptAndRestore(row: SessionEntity, snap: EngineState, gapMillis: Long) {
        val resumed = snap.resumedAfter(gapMillis, SystemClock.elapsedRealtimeNanos())
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
            lastSeenFix = if (current != null) s.lastFix?.tNanos ?: Long.MIN_VALUE else Long.MIN_VALUE
            lastStoredFix = null
            lastStoredPos = null
        }
        lastState = s
        if (s.session.kind != SessionKind.TRIP) return // the live meter is never stored
        if (current == null) {
            val now = System.currentTimeMillis()
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
        val f = s.lastFix ?: return
        if (f.tNanos == lastSeenFix) return
        lastSeenFix = f.tNanos
        val sinceStored = lastStoredFix?.let { f.tNanos - it }
        if (s.session.paused || f.hAcc >= 20 || (sinceStored != null && sinceStored < 950_000_000L)) return
        // Parked: don't record GPS jitter as route (it would draw squiggles at every stop).
        val parkedHere = s.filter.output == 0.0 && lastStoredPos?.let { (lat, lon) ->
            Geo.distanceM(lat, lon, f.lat, f.lon) < max(f.hAcc, 8.0)
        } == true
        if (parkedHere) return
        lastStoredFix = f.tNanos
        lastStoredPos = f.lat to f.lon
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
        current = null
        recording.value = false
        if (pending.isNotEmpty()) dao.insertPoints(pending.toList())
        pending.clear()
        val now = System.currentTimeMillis()
        dao.updateSession(
            withStats(row, s).copy(
                state = SessionStates.FINISHED, endedAt = now, updatedAt = now,
                engineSnapshot = null, thumbnail = thumbnail(dao.points(row.id)),
            ),
        )
        _finished.value = row.id
        dirty = false
    }

    private suspend fun flush(force: Boolean = false) {
        val row = current ?: return
        val s = lastState ?: return
        if (!force && !dirty && pending.isEmpty() && System.currentTimeMillis() - row.updatedAt < 5_000) return
        val updated = withStats(row, s).copy(
            state = if (s.session.paused) SessionStates.PAUSED else SessionStates.ACTIVE,
            mode = settings.value.mode.name,
            updatedAt = System.currentTimeMillis(),
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
                .joinToString(";") { String.format(Locale.ROOT, "%.5f,%.5f", it.lat, it.lon) }
        }
    }
}
