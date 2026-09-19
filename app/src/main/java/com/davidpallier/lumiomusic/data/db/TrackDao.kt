package com.davidpallier.lumiomusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davidpallier.lumiomusic.data.model.AlbumSummary
import com.davidpallier.lumiomusic.data.model.ArtistSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE uriString = :uriString LIMIT 1")
    suspend fun getByUri(uriString: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY artist, album, trackNumber")
    suspend fun getAllOnce(): List<TrackEntity>

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

    @Query(
        """
        SELECT album, MIN(albumArtist) AS albumArtist, COUNT(*) AS trackCount,
               MIN(coverArtPath) AS coverArtPath, MIN(year) AS year
        FROM tracks
        GROUP BY album
        ORDER BY album
        """
    )
    fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query(
        """
        SELECT artist, COUNT(DISTINCT album) AS albumCount, COUNT(*) AS trackCount
        FROM tracks
        GROUP BY artist
        ORDER BY artist
        """
    )
    fun observeArtists(): Flow<List<ArtistSummary>>

    @Query(
        """
        SELECT album, MIN(albumArtist) AS albumArtist, COUNT(*) AS trackCount,
               MIN(coverArtPath) AS coverArtPath, MIN(year) AS year
        FROM tracks
        WHERE artist = :artist
        GROUP BY album
        ORDER BY year, album
        """
    )
    fun observeAlbumsForArtist(artist: String): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber")
    fun observeTracksForAlbum(album: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY artist, album, trackNumber
        """
    )
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT album, MIN(albumArtist) AS albumArtist, COUNT(*) AS trackCount,
               MIN(coverArtPath) AS coverArtPath, MIN(year) AS year
        FROM tracks
        GROUP BY album
        ORDER BY album
        """
    )
    suspend fun getAlbumsOnce(): List<AlbumSummary>

    @Query(
        """
        SELECT artist, COUNT(DISTINCT album) AS albumCount, COUNT(*) AS trackCount
        FROM tracks
        GROUP BY artist
        ORDER BY artist
        """
    )
    suspend fun getArtistsOnce(): List<ArtistSummary>

    @Query(
        """
        SELECT album, MIN(albumArtist) AS albumArtist, COUNT(*) AS trackCount,
               MIN(coverArtPath) AS coverArtPath, MIN(year) AS year
        FROM tracks
        WHERE artist = :artist
        GROUP BY album
        ORDER BY year, album
        """
    )
    suspend fun getAlbumsForArtistOnce(artist: String): List<AlbumSummary>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber")
    suspend fun getTracksForAlbumOnce(album: String): List<TrackEntity>
}
