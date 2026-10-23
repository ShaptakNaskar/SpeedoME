package com.sappy.speedome.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A live meter or recorded trip (docs/plan.md §7). Times are UTC milliseconds. */
@Entity(tableName = "session", indices = [Index("state"), Index("kind", "startedAt")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LIVE or TRIP. */
    val kind: String,
    /** ACTIVE, PAUSED or FINISHED. */
    val state: String,
    /** DRIVE or STEP. */
    val mode: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Last time the recorder saved this row; drives gap detection on resume. */
    val updatedAt: Long,
    val name: String? = null,
    val distanceM: Double = 0.0,
    val movingS: Double = 0.0,
    val elapsedS: Double = 0.0,
    val maxMps: Double = 0.0,
    val steps: Long = 0,
    /** Serialized EngineState while the session is unfinished; null once finished. */
    val engineSnapshot: String? = null,
    /** Decimated route for list thumbnails: "lat,lon;lat,lon;…". */
    val thumbnail: String? = null,
)

/** One stored route point; at most one per second per session. */
@Entity(
    tableName = "point",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId", "t")],
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val t: Long,
    val segment: Int,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val hAcc: Float? = null,
    val vAcc: Float? = null,
    @ColumnInfo(name = "rawSpeed") val rawSpeedMps: Float? = null,
    @ColumnInfo(name = "speed") val speedMps: Float,
    val speedAcc: Float? = null,
    val source: String,
    val bearing: Float? = null,
    val isMock: Boolean = false,
)

object SessionStates {
    const val ACTIVE = "ACTIVE"
    const val PAUSED = "PAUSED"
    const val FINISHED = "FINISHED"
}
