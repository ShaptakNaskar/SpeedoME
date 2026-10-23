package com.sappy.speedome.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** Trips and their points (docs/plan.md §7). Each platform builds it in its own place ([Platform]). */
@Database(entities = [SessionEntity::class, PointEntity::class], version = 1, exportSchema = true)
@ConstructedBy(SpeedoDatabaseConstructor::class)
abstract class SpeedoDatabase : RoomDatabase() {
    abstract fun trips(): TripDao

    companion object {
        const val FILE = "speedome.db"
    }
}

// Room generates the actual object for each platform.
@Suppress("KotlinNoActualForExpect")
expect object SpeedoDatabaseConstructor : RoomDatabaseConstructor<SpeedoDatabase> {
    override fun initialize(): SpeedoDatabase
}
