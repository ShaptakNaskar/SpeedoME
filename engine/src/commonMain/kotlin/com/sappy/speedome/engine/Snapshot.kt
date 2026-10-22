package com.sappy.speedome.engine

import kotlinx.serialization.json.Json

/** JSON encoding of [EngineState] for the per-second resume snapshot. */
object Snapshot {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(state: EngineState): String = json.encodeToString(EngineState.serializer(), state)

    /** Returns null when the text isn't a readable snapshot (e.g. written by an incompatible version). */
    fun decodeOrNull(text: String): EngineState? =
        runCatching { json.decodeFromString(EngineState.serializer(), text) }.getOrNull()
}
