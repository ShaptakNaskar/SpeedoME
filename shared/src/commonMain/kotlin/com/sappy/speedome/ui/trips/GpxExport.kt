package com.sappy.speedome.ui.trips

import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.engine.replay.GpxPoint
import com.sappy.speedome.engine.replay.GpxWriter
import com.sappy.speedome.localFileStamp

/** GPX 1.1 export through the share sheet or a save-as picker (docs/plan.md §7). */
object GpxExport {
    const val MIME = "application/gpx+xml"

    fun fileName(t: SessionEntity): String = "SpeedoME-${localFileStamp(t.startedAt)}.gpx"

    fun build(t: SessionEntity, points: List<PointEntity>): String = GpxWriter.write(
        name = t.name ?: fileName(t).removeSuffix(".gpx"),
        points = points.map { GpxPoint(it.t, it.lat, it.lon, it.alt, it.speedMps.toDouble(), it.segment) },
    )
}
