package com.davidpallier.lumiomusic.data.playlist

import com.davidpallier.lumiomusic.data.db.PlaylistDao
import com.davidpallier.lumiomusic.data.db.PlaylistEntity
import com.davidpallier.lumiomusic.data.db.PlaylistTrackCrossRef
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    val playlists: Flow<List<PlaylistSummary>> = playlistDao.observePlaylistSummaries()

    fun tracksFor(playlistId: Long): Flow<List<TrackEntity>> = playlistDao.observeTracksForPlaylist(playlistId)

    suspend fun getPlaylist(playlistId: Long): PlaylistEntity? = playlistDao.getPlaylist(playlistId)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun renamePlaylist(playlistId: Long, name: String) = playlistDao.renamePlaylist(playlistId, name)

    suspend fun deletePlaylist(playlistId: Long) = playlistDao.deletePlaylist(playlistId)

    suspend fun addTrack(playlistId: Long, trackId: Long) {
        val nextPosition = playlistDao.getMaxPosition(playlistId) + 1
        playlistDao.insertCrossRef(PlaylistTrackCrossRef(playlistId, trackId, nextPosition))
    }

    suspend fun createPlaylistWithTrack(name: String, trackId: Long): Long {
        val playlistId = createPlaylist(name)
        addTrack(playlistId, trackId)
        return playlistId
    }

    suspend fun removeTrack(playlistId: Long, trackId: Long) = playlistDao.removeTrack(playlistId, trackId)
}
