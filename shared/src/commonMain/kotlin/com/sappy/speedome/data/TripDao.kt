package com.sappy.speedome.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert
    suspend fun insertPoints(points: List<PointEntity>)

    /** One write per second: new points plus the refreshed session row and snapshot. */
    @Transaction
    suspend fun save(session: SessionEntity, points: List<PointEntity>) {
        if (points.isNotEmpty()) insertPoints(points)
        updateSession(session)
    }

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    /** The unfinished session to resume, if the app stopped mid-way. */
    @Query("SELECT * FROM session WHERE state != 'FINISHED' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun unfinished(): SessionEntity?

    @Query("SELECT * FROM session WHERE state != 'FINISHED'")
    suspend fun allUnfinished(): List<SessionEntity>

    @Query("SELECT * FROM session WHERE kind = 'TRIP' AND state = 'FINISHED' ORDER BY startedAt DESC")
    fun trips(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM point WHERE sessionId = :sessionId ORDER BY t")
    suspend fun points(sessionId: Long): List<PointEntity>

    @Query("SELECT * FROM point WHERE sessionId = :sessionId ORDER BY t")
    fun observePoints(sessionId: Long): Flow<List<PointEntity>>

    @Query("SELECT MAX(segment) FROM point WHERE sessionId = :sessionId")
    suspend fun lastSegment(sessionId: Long): Int?

    @Query("SELECT * FROM point WHERE sessionId = :sessionId AND t BETWEEN :from AND :to ORDER BY t")
    suspend fun pointsBetween(sessionId: Long, from: Long, to: Long): List<PointEntity>

    /** Saves a finished section with a copy of its stretch of the trip's route; returns its id. */
    @Transaction
    suspend fun insertSection(section: SessionEntity, points: List<PointEntity>): Long {
        val id = insertSession(section)
        insertPoints(points.map { it.copy(id = 0, sessionId = id) })
        return id
    }

    @Query("SELECT * FROM session WHERE parentId = :tripId ORDER BY startedAt")
    fun sections(tripId: Long): Flow<List<SessionEntity>>

    @Query("UPDATE session SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("UPDATE session SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)
}
