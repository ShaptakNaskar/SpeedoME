package com.sappy.speedome.tracking

import android.os.SystemClock
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.CommandEvent
import com.sappy.speedome.engine.Engine
import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.TickEvent
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs the pure engine on one thread. Every source (real GPS, sensors, simulator, commands)
 * submits events here; screens observe [state]. Nothing ever blocks the UI thread.
 */
class TrackingEngine(scope: CoroutineScope, private val settings: StateFlow<AppSettings>, active: StateFlow<Boolean>) {
    private val engineThread = Dispatchers.Default.limitedParallelism(1)
    private sealed interface Msg {
        class Event(val event: EngineEvent) : Msg

        class Restore(val state: EngineState) : Msg

        class Barrier(val done: CompletableDeferred<Unit>) : Msg
    }

    private val inbox = Channel<Msg>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    init {
        scope.launch(engineThread) {
            for (m in inbox) {
                when (m) {
                    is Msg.Event -> _state.value = Engine.reduce(_state.value, m.event, settings.value.engine)
                    is Msg.Restore -> _state.value = m.state
                    is Msg.Barrier -> m.done.complete(Unit)
                }
            }
        }
        // ~1 Hz housekeeping, only while tracking is active: nothing wakes the CPU once the app is closed.
        scope.launch {
            active.collectLatest { on ->
                if (!on) return@collectLatest
                while (true) {
                    delay(1000)
                    submit(TickEvent(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis()))
                }
            }
        }
    }

    fun submit(event: EngineEvent) {
        inbox.trySend(Msg.Event(event))
    }

    /** Replaces the whole engine state (resuming a saved session), in order with other events. */
    fun restore(state: EngineState) {
        inbox.trySend(Msg.Restore(state))
    }

    /** Suspends until everything submitted before this call has been applied to [state]. */
    suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        inbox.send(Msg.Barrier(done))
        done.await()
    }

    fun command(command: Command) =
        submit(CommandEvent(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), command))
}
