package com.davidpallier.lumiomusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE uriString NOT IN (:seenUris)")
    suspend fun deleteMissing(seenUris: List<String>)

    @Query("SELECT * FROM tracks ORDER BY artist, album, trackNumber")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeCount(): Flow<Int>

    @Query("SELECT DISTINCT album FROM tracks ORDER BY album")
    suspend fun getAlbumNames(): List<String>

    @Query("SELECT DISTINCT artist FROM tracks ORDER BY artist")
    suspend fun getArtistNames(): List<String>
}
