package com.sappy.speedome.engine.replay

import com.sappy.speedome.engine.FixEvent

/**
 * The raw-fix log format written by the developer raw logger and read back by the replay harness.
 * One fix per line; empty cells are fields the chip did not provide.
 */
object RawLog {
    const val HEADER = "tNanos,utcMillis,lat,lon,hAcc,altM,vAcc,speed,speedAcc,bearing,bearingAcc,mock"

    fun line(e: FixEvent): String = listOf(
        e.tNanos, e.utcMillis, e.lat, e.lon, e.hAcc, e.altM, e.vAcc, e.speed, e.speedAcc, e.bearing, e.bearingAcc,
        if (e.isMock) 1 else 0,
    ).joinToString(",") { it?.toString() ?: "" }

    fun write(fixes: List<FixEvent>): String = buildString {
        append(HEADER).append('\n')
        fixes.forEach { append(line(it)).append('\n') }
    }

    /** Parses a log; malformed lines are skipped rather than failing the whole file. */
    fun parse(text: String): List<FixEvent> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("tNanos") }
        .mapNotNull { parseLine(it) }
        .toList()

    private fun parseLine(line: String): FixEvent? {
        val c = line.split(',')
        if (c.size < 12) return null
        fun f(i: Int) = c[i].takeIf { it.isNotEmpty() }?.toFloatOrNull()
        fun d(i: Int) = c[i].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        return FixEvent(
            tNanos = c[0].toLongOrNull() ?: return null,
            utcMillis = c[1].toLongOrNull() ?: return null,
            lat = d(2) ?: return null,
            lon = d(3) ?: return null,
            hAcc = f(4),
            altM = d(5),
            vAcc = f(6),
            speed = f(7),
            speedAcc = f(8),
            bearing = f(9),
            bearingAcc = f(10),
            isMock = c[11] == "1",
        )
    }
}
