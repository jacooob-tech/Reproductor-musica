package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // === Track Queries ===
    @Query("SELECT * FROM tracks")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1")
    fun getFavoriteTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE title LIKE :query OR artist LIKE :query OR category LIKE :query")
    fun searchTracks(query: String): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Update
    suspend fun updateTrack(track: Track)

    @Delete
    suspend fun deleteTrack(track: Track)

    // === Playlist Queries ===
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("""
        SELECT t.* FROM tracks t 
        INNER JOIN playlist_track_cross_ref ref ON t.id = ref.trackId 
        WHERE ref.playlistId = :playlistId
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>>

    // === History Queries ===
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history h ON t.id = h.trackId
        ORDER BY h.playedAt DESC LIMIT 20
    """)
    fun getPlaybackHistory(): Flow<List<Track>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
