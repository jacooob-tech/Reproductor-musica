package com.example.repository

import com.example.data.MusicDao
import com.example.data.Playlist
import com.example.data.PlaylistTrack
import com.example.data.Track
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {
    val allTracks: Flow<List<Track>> = musicDao.getAllTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun getTrackById(id: Long): Track? = musicDao.getTrackById(id)

    suspend fun insertTrack(track: Track): Long = musicDao.insertTrack(track)

    suspend fun updateTrack(track: Track) = musicDao.updateTrack(track)

    suspend fun deleteTrack(trackId: Long) = musicDao.deleteTrackById(trackId)

    suspend fun insertPlaylist(playlist: Playlist): Long = musicDao.insertPlaylist(playlist)

    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.clearPlaylistTracks(playlistId)
        musicDao.deletePlaylistById(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        musicDao.insertPlaylistTrack(PlaylistTrack(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        musicDao.deletePlaylistTrack(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> = musicDao.getTracksForPlaylist(playlistId)
}
