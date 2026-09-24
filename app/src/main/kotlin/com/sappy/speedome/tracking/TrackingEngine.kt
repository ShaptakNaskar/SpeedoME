package com.sappy.speedome.tracking

import android.os.SystemClock
import com.sappy.speedome.engine.Command
import com.sappy.speedome.engine.CommandEvent
import com.sappy.speedome.engine.Engine
import com.sappy.speedome.engine.EngineEvent
import com.sappy.speedome.engine.EngineState
import com.sappy.speedome.engine.TickEvent
import com.sappy.speedome.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the pure engine on one thread. Every source (real GPS, sensors, simulator, commands)
 * submits events here; screens observe [state]. Nothing ever blocks the UI thread.
 */
class TrackingEngine(scope: CoroutineScope, private val settings: StateFlow<AppSettings>) {
    private val engineThread = Dispatchers.Default.limitedParallelism(1)
    private sealed interface Msg {
        class Event(val event: EngineEvent) : Msg

        class Restore(val state: EngineState) : Msg
    }

    private val inbox = Channel<Msg>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    init {
        scope.launch(engineThread) {
            for (m in inbox) {
                _state.value = when (m) {
                    is Msg.Event -> Engine.reduce(_state.value, m.event, settings.value.engine)
                    is Msg.Restore -> m.state
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                submit(TickEvent(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis()))
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

    fun command(command: Command) =
        submit(CommandEvent(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), command))
}
