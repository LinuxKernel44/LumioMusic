package com.davidpallier.lumiomusic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, LyricsCacheEntity::class, PlaylistEntity::class, PlaylistTrackCrossRef::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun playlistDao(): PlaylistDao
}
