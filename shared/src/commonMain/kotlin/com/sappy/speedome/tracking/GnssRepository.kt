package com.sappy.speedome.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Constellation ids: the same numbers as Android's GnssStatus.CONSTELLATION_* values. */
object Constellation {
    const val GPS = 1
    const val SBAS = 2
    const val GLONASS = 3
    const val QZSS = 4
    const val BEIDOU = 5
    const val GALILEO = 6
    const val IRNSS = 7
}

/** One satellite as reported by the receiver (Android only: iOS doesn't expose satellites). */
data class Satellite(
    val constellation: Int,
    val svid: Int,
    val cn0DbHz: Float,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val usedInFix: Boolean,
    val carrierHz: Float?,
)

data class GnssSnapshot(
    val satellites: List<Satellite> = emptyList(),
    val timeToFirstFixMs: Int? = null,
    val hardwareModel: String? = null,
) {
    val usedCount: Int get() = satellites.count { it.usedInFix }

    /** True when any used satellite is on a second frequency (L5/E5a/B2a ≈ 1176 MHz). */
    val dualFrequency: Boolean get() = satellites.any { it.usedInFix && (it.carrierHz ?: 0f) in 1.1e9f..1.2e9f }
}

/** Nerd-page data that stays out of the deterministic engine (it never affects stats). */
class GnssRepository {
    private val _snapshot = MutableStateFlow(GnssSnapshot())
    val snapshot: StateFlow<GnssSnapshot> = _snapshot.asStateFlow()

    private val _nmea = MutableStateFlow<List<String>>(emptyList())
    val nmea: StateFlow<List<String>> = _nmea.asStateFlow()

    fun onSatellites(sats: List<Satellite>) = _snapshot.update { it.copy(satellites = sats) }

    fun onFirstFix(ttffMs: Int) = _snapshot.update { it.copy(timeToFirstFixMs = ttffMs) }

    fun onHardware(model: String?) = _snapshot.update { it.copy(hardwareModel = model) }

    fun onNmea(line: String) = _nmea.update { (it + line.trim()).takeLast(40) }

    fun clearSatellites() = _snapshot.update { it.copy(satellites = emptyList()) }
}
