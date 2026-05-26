package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String, // Can be content URI (content://...) or asset path
    val isDemo: Boolean = false,
    val category: String = "Por defecto",
    val lyrics: String? = null,
    val aiAnalysis: String? = null,
    val aiVibe: String? = null,
    val aiInfluences: String? = null,
    val coverArtUrl: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long
)
