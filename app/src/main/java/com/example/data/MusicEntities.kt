package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val category: String,
    val lyrics: String,
    val accentColorHex: String, // Used to dynamically colorize the full-screen player background!
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val coverUrl: String = ""
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("trackId")
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId")]
)
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long = System.currentTimeMillis()
)
