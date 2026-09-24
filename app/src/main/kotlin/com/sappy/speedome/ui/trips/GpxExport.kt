package com.sappy.speedome.ui.trips

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sappy.speedome.data.PointEntity
import com.sappy.speedome.data.SessionEntity
import com.sappy.speedome.engine.replay.GpxPoint
import com.sappy.speedome.engine.replay.GpxWriter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** GPX 1.1 export via the share sheet or a save-to-folder picker (docs/plan.md §7). */
object GpxExport {
    const val MIME = "application/gpx+xml"

    fun fileName(t: SessionEntity): String =
        "SpeedoME-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(t.startedAt)) + ".gpx"

    fun build(t: SessionEntity, points: List<PointEntity>): String = GpxWriter.write(
        name = t.name ?: fileName(t).removeSuffix(".gpx"),
        points = points.map { GpxPoint(it.t, it.lat, it.lon, it.alt, it.speedMps.toDouble(), it.segment) },
    )

    /** Writes the file to the app cache (call off the main thread) and returns a share intent. */
    fun shareIntent(context: Context, t: SessionEntity, points: List<PointEntity>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(t)).apply { writeText(build(t, points)) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share trip as GPX")
    }

    /** Writes to a document the user picked (call off the main thread). */
    fun writeTo(context: Context, uri: Uri, t: SessionEntity, points: List<PointEntity>) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(build(t, points).toByteArray()) }
    }
}
