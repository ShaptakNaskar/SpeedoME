package com.sappy.speedome.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class, PointEntity::class], version = 1, exportSchema = true)
abstract class SpeedoDatabase : RoomDatabase() {
    abstract fun trips(): TripDao

    companion object {
        fun create(context: Context): SpeedoDatabase =
            Room.databaseBuilder(context.applicationContext, SpeedoDatabase::class.java, "speedome.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
