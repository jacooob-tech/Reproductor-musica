package com.example.repository

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class MusicRepository(private val musicDao: MusicDao) {

    val allTracks: Flow<List<Track>> = musicDao.getAllTracks()
    val favoriteTracks: Flow<List<Track>> = musicDao.getFavoriteTracks()
    val playlists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val history: Flow<List<Track>> = musicDao.getPlaybackHistory()

    fun searchTracks(query: String): Flow<List<Track>> {
        return if (query.isBlank()) {
            allTracks
        } else {
            musicDao.searchTracks("%$query%")
        }
    }

    suspend fun toggleFavorite(trackId: Long) {
        val track = musicDao.getTrackById(trackId)
        if (track != null) {
            val updated = track.copy(isFavorite = !track.isFavorite)
            musicDao.updateTrack(updated)
        }
    }

    suspend fun getTrackById(trackId: Long): Track? {
        return musicDao.getTrackById(trackId)
    }

    suspend fun deleteTrack(trackId: Long) {
        val track = musicDao.getTrackById(trackId)
        if (track != null) {
            musicDao.deleteTrack(track)
        }
    }

    suspend fun updateTrack(track: Track) {
        musicDao.updateTrack(track)
    }

    suspend fun increasePlayCount(trackId: Long) {
        val track = musicDao.getTrackById(trackId)
        if (track != null) {
            val updated = track.copy(playCount = track.playCount + 1)
            musicDao.updateTrack(updated)
            // Add to history
            musicDao.insertHistory(PlaybackHistory(trackId = trackId))
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        val playlist = Playlist(name = name, description = description)
        return musicDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        musicDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        musicDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<Track>> {
        return musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun clearHistory() {
        musicDao.clearHistory()
    }

    suspend fun insertTracks(tracks: List<Track>) {
        musicDao.insertTracks(tracks)
    }

    suspend fun updateTrackLyrics(trackId: Long, lyrics: String) {
        val track = musicDao.getTrackById(trackId)
        if (track != null) {
            val updated = track.copy(lyrics = lyrics)
            musicDao.updateTrack(updated)
        }
    }

    suspend fun ensureDefaultTracksPreseeded() {
        // Checking if the database contains any tracks. If not, seed default tracks.
        val existing = musicDao.getAllTracks().first()
        if (existing.isEmpty()) {
            val defaultTracks = listOf(
                Track(
                    id = 1L,
                    title = "Sunset Boulevard",
                    artist = "The Midnight Grid",
                    album = "Retro Future",
                    durationMs = 204000L,
                    category = "Synthwave",
                    accentColorHex = "#D81B60",
                    lyrics = "Teclados retro en el horizonte...\nLas luces parpadean como estrellas.\nUn viaje interminable hacia el ocaso neón.\nConduciendo sin rumbo bajo la luna retro.\n\nSiente el pulso del sintetizador...\nEl tiempo se congela en esta autopista."
                ),
                Track(
                    id = 2L,
                    title = "Raindrops & Coffee",
                    artist = "Lofi Dreamer",
                    album = "Cozy Afternoons",
                    durationMs = 156000L,
                    category = "Lofi",
                    accentColorHex = "#3949AB",
                    lyrics = "[Instrumental Melódico]\nTazas humeantes de café selecto.\nGotas de lluvia golpeando suavemente el cristal.\nUn susurro de piano relaja la mente.\nEstudiando o descansando en el rincón predilecto."
                ),
                Track(
                    id = 3L,
                    title = "Neon Horizon",
                    artist = "Future Wave",
                    album = "Digital Mirage",
                    durationMs = 188000L,
                    category = "Synthwave",
                    accentColorHex = "#00ACC1",
                    lyrics = "Caminando por calles de silicio y luz,\nSombra digital persiguiendo tu caminar.\nEsquivando hologramas en la gran ciudad,\nDonde el mañana es hoy, y el ayer no volverá.\n\nEl futuro brilla con luz fluorescente..."
                ),
                Track(
                    id = 4L,
                    title = "Starlight Chill",
                    artist = "Acoustic Breeze",
                    album = "Midnight Session",
                    durationMs = 232000L,
                    category = "Acoustic",
                    accentColorHex = "#00897B",
                    lyrics = "Solo una guitarra y la inmensidad...\nLas estrellas guían todo el cantar.\nUn acorde suave para recordar,\nQue la vida es bella al respirar.\n\nSiente la brisa fría del campo en flor...\nLa melodía alivia tu viejo dolor."
                ),
                Track(
                    id = 5L,
                    title = "Urban Coffee Shop",
                    artist = "Jazz Cafe Trio",
                    album = "Standard Pleasures",
                    durationMs = 275000L,
                    category = "Jazz",
                    accentColorHex = "#8D6E63",
                    lyrics = "[Solo de Contrabajo y Saxofón]\nEl ambiente cálido del club nocturno.\nConversaciones lejanas y risas de terciopelo.\nUn saxofón que deslumbra con elegancia.\nLa noche de jazz nunca quiere acabar."
                ),
                Track(
                    id = 6L,
                    title = "Solar Wind",
                    artist = "Atmospheric Ambient",
                    album = "Galactic Journey",
                    durationMs = 310000L,
                    category = "Ambient",
                    accentColorHex = "#5E35B1",
                    lyrics = "[Vuelo Cósmico Instrumental]\nFlotando en el vacío estellar.\nOndas de sonido cruzando la nebulosa.\nSintiendo la ingravidez de nuestra existencia.\nTranquilidad absoluta entre millares de planetas."
                ),
                Track(
                    id = 7L,
                    title = "Tokyo Driftwood",
                    artist = "Cyberpunk DJ",
                    album = "Neo Tokyo Lights",
                    durationMs = 212000L,
                    category = "Pop",
                    accentColorHex = "#F4511E",
                    lyrics = "Calles mojadas de Shinjuku,\nLuces de neón que guían la velocidad.\nVelocidad mental, pulso en su cenit.\nSintiendo el ritmo tecnológico de Oriente.\n\n¡Súbele al volumen, la noche es nuestra!"
                ),
                Track(
                    id = 8L,
                    title = "Summer Escape",
                    artist = "Tropical Vibes",
                    album = "Island Beats",
                    durationMs = 195000L,
                    category = "Pop",
                    accentColorHex = "#7CB342",
                    lyrics = "La arena blanca bajo tus pies,\nUn cóctel dulce para disfrutar.\nEl sol acaricia tu piel color miel,\nNo hay preocupaciones que puedan llegar.\n\n¡Baila en la playa con la marea!"
                )
            )
            musicDao.insertTracks(defaultTracks)

            // Let's also insert a couple of default playlists
            val p1 = Playlist(name = "Favoritas para Estudiar", description = "Canciones pausadas para concentrarse")
            val p2 = Playlist(name = "Clásicos del Mañana", description = "La mejor selección musical de la semana")
            
            val p1Id = musicDao.insertPlaylist(p1)
            val p2Id = musicDao.insertPlaylist(p2)
            
            // Link some tracks to these playlists
            musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(p1Id, 2L)) // Raindrops & Coffee
            musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(p1Id, 4L)) // Starlight Chill
            musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(p1Id, 6L)) // Solar Wind
            
            musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(p2Id, 1L)) // Sunset Boulevard
            musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(p2Id, 3L)) // Neon Horizon
            musicDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(p2Id, 7L)) // Tokyo Driftwood
        }
    }
}
