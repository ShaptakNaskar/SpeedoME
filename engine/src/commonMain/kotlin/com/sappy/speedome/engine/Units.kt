package com.sappy.speedome.engine

/** Speed unit conversions. The engine works in SI units (m/s); screens convert at the edge. */
object Units {
    const val MPS_TO_KMH = 3.6
    const val MPS_TO_MPH = 2.2369362920544

    fun mpsToKmh(mps: Double): Double = mps * MPS_TO_KMH

    fun kmhToMps(kmh: Double): Double = kmh / MPS_TO_KMH

    fun mpsToMph(mps: Double): Double = mps * MPS_TO_MPH
}
