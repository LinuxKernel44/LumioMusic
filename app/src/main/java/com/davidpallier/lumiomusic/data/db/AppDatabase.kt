package com.davidpallier.lumiomusic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
