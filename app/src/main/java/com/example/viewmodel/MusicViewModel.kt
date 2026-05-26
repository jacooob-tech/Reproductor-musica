package com.example.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Playlist
import com.example.data.Track
import com.example.network.AnalysisResult
import com.example.network.GeminiLyricsSearcher
import com.example.network.GeminiMusicAnalyzer
import com.example.repository.MusicRepository
import com.example.service.PlaybackStateHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class TrackSortOption {
    DATE_ADDED,
    TITLE,
    ARTIST,
    DURATION,
    AI_DESC
}

sealed interface AiRetrievalState {
    object Idle : AiRetrievalState
    object Loading : AiRetrievalState
    data class Success(val title: String, val artist: String, val album: String, val category: String, val desc: String) : AiRetrievalState
    data class Error(val message: String) : AiRetrievalState
}

class MusicViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    private val TAG = "MusicViewModel"

    // Backing states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterMinDurationMs = MutableStateFlow(0L)
    val filterMinDurationMs: StateFlow<Long> = _filterMinDurationMs.asStateFlow()

    private val _homeSortOption = MutableStateFlow(TrackSortOption.DATE_ADDED)
    val homeSortOption: StateFlow<TrackSortOption> = _homeSortOption.asStateFlow()

    private val _homeAiSortedIds = MutableStateFlow<List<Long>>(emptyList())
    val homeAiSortedIds: StateFlow<List<Long>> = _homeAiSortedIds.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    // 1. Get correct tracks based on play list or library
    private val playlistTracksFlow: Flow<List<Track>?> = _selectedPlaylistId.flatMapLatest { playlistId ->
        if (playlistId == null) flowOf(null)
        else repository.getTracksForPlaylist(playlistId)
    }

    private val baseTracksFlow: Flow<List<Track>> = combine(
        repository.allTracks,
        playlistTracksFlow
    ) { all, playlistTracks ->
        playlistTracks ?: all
    }

    // 2. Filter, search, and sort using exact 5-parameter type-safe combine
    val displayedTracks: StateFlow<List<Track>> = combine(
        baseTracksFlow,
        _searchQuery,
        _filterMinDurationMs,
        _homeSortOption,
        _homeAiSortedIds
    ) { sourceList, query, minDuration, sortOption, aiSortedIds ->
        var processed = sourceList.filter { track ->
            (track.title.contains(query, ignoreCase = true) || track.artist.contains(query, ignoreCase = true)) &&
            (track.duration >= minDuration)
        }
        processed = sortTracks(processed, sortOption, aiSortedIds)
        processed
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlistList: StateFlow<List<Playlist>> = repository.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Player Sync
    val currentTrack: StateFlow<Track?> = PlaybackStateHelper.currentTrack.asStateFlow()
    val isPlaying: StateFlow<Boolean> = PlaybackStateHelper.isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    // AI/Gemini specific states
    private val _aiRetrievalState = MutableStateFlow<AiRetrievalState>(AiRetrievalState.Idle)
    val aiRetrievalState: StateFlow<AiRetrievalState> = _aiRetrievalState.asStateFlow()

    private val _lyricsState = MutableStateFlow<String?>(null)
    val lyricsState: StateFlow<String?> = _lyricsState.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    private val _musicAnalysisState = MutableStateFlow<AnalysisResult?>(null)
    val musicAnalysisState: StateFlow<AnalysisResult?> = _musicAnalysisState.asStateFlow()

    private val _musicAnalysisLoading = MutableStateFlow(false)
    val musicAnalysisLoading: StateFlow<Boolean> = _musicAnalysisLoading.asStateFlow()

    private val _musicAnalysisError = MutableStateFlow<String?>(null)
    val musicAnalysisError: StateFlow<String?> = _musicAnalysisError.asStateFlow()

    private val _aiSortingLoading = MutableStateFlow(false)
    val aiSortingLoading: StateFlow<Boolean> = _aiSortingLoading.asStateFlow()

    private val _aiSortingError = MutableStateFlow<String?>(null)
    val aiSortingError: StateFlow<String?> = _aiSortingError.asStateFlow()

    // Player instances
    private var mediaPlayer: MediaPlayer? = null
    private var synthJob: Job? = null
    private var progressJob: Job? = null
    private var hasPlaybackStarted = false

    override fun onCleared() {
        super.onCleared()
        stopAllPlayback()
        stopService()
    }

    init {
        // Setup state bridge callbacks
        PlaybackStateHelper.onPlayPauseAction = { togglePlayPause() }
        PlaybackStateHelper.onNextAction = { nextTrack() }
        PlaybackStateHelper.onPrevAction = { previousTrack() }

        // Start progress tracking collector when isPlaying turns true
        viewModelScope.launch {
            isPlaying.collect { playing ->
                if (playing) {
                    startProgressTracker()
                } else {
                    progressJob?.cancel()
                }
            }
        }

        // Initialize demo tracks if database is empty on first launch
        viewModelScope.launch {
            repository.allTracks.first().let { current ->
                if (current.isEmpty()) {
                    insertDemoTracks()
                }
            }
        }
    }

    // --- SOUND ENGINE & PLAYBACK ---

    fun playTrack(track: Track) {
        stopAllPlayback()
        _playerError.value = null
        PlaybackStateHelper.currentTrack.value = track
        PlaybackStateHelper.isPlaying.value = true
        _playbackPosition.value = 0L
        hasPlaybackStarted = true

        if (track.isDemo) {
            playSynthesizedAudio(track)
        } else {
            playLocalMediaPlayer(track)
        }

        startForegroundService()
    }

    fun togglePlayPause() {
        val track = currentTrack.value ?: return
        val currentPlayingState = isPlaying.value

        if (currentPlayingState) {
            // Pause
            PlaybackStateHelper.isPlaying.value = false
            if (track.isDemo) {
                synthJob?.cancel()
            } else {
                mediaPlayer?.pause()
            }
        } else {
            // Resume
            PlaybackStateHelper.isPlaying.value = true
            if (track.isDemo) {
                playSynthesizedAudio(track)
            } else {
                try {
                    mediaPlayer?.start()
                } catch (e: Exception) {
                    // If the MediaPlayer was released or failed, restart clean
                    playLocalMediaPlayer(track)
                }
            }
        }
        startForegroundService()
    }

    fun nextTrack() {
        val tracks = displayedTracks.value
        val current = currentTrack.value ?: return
        val idx = tracks.indexOfFirst { it.id == current.id }
        if (idx != -1 && idx < tracks.size - 1) {
            playTrack(tracks[idx + 1])
        } else if (tracks.isNotEmpty()) {
            playTrack(tracks.first()) // Loop to start
        }
    }

    fun previousTrack() {
        val tracks = displayedTracks.value
        val current = currentTrack.value ?: return
        val idx = tracks.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            playTrack(tracks[idx - 1])
        } else if (tracks.isNotEmpty()) {
            playTrack(tracks.last()) // Loop to end
        }
    }

    fun seekTo(positionMs: Long) {
        val track = currentTrack.value ?: return
        _playbackPosition.value = positionMs
        if (!track.isDemo) {
            try {
                mediaPlayer?.seekTo(positionMs.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "Seek failed", e)
            }
        }
    }

    private fun playLocalMediaPlayer(track: Track) {
        try {
            val context = getApplication<Application>()
            val uri = Uri.parse(track.path)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setOnCompletionListener {
                    nextTrack()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer Error: what=$what, extra=$extra")
                    _playerError.value = "Error al reproducir el archivo. Puede que los permisos o el archivo estén dañados."
                    PlaybackStateHelper.isPlaying.value = false
                    true
                }
                prepare()
                start()
            }
            Log.d(TAG, "Successfully playing local track: ${track.title} via Uri: ${track.path}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play via MediaPlayer", e)
            _playerError.value = "No se pudo reproducir este archivo. Asegúrate de dar permisos de almacenamiento en los ajustes del teléfono: ${e.localizedMessage}"
            PlaybackStateHelper.isPlaying.value = false
        }
    }

    /**
     * Ambient Drone Synthesizer loops - generates lush soundscapes in real-time.
     * Keeps the progress bar moving forward manually since this is synthesized.
     */
    private fun playSynthesizedAudio(track: Track) {
        synthJob?.cancel()
        synthJob = viewModelScope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioTrack = try {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize,
                    AudioTrack.MODE_STREAM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init AudioTrack", e)
                return@launch
            }

            try {
                audioTrack.play()
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack play failed", e)
                audioTrack.release()
                return@launch
            }

            var t = 0.0
            val buffer = ShortArray(1024)

            // Dynamic synthesizer frequencies based on song name
            val baseFreq = when {
                track.title.contains("Zen") -> 130.81 // C3
                track.title.contains("Electro") -> 110.0 // A2
                track.title.contains("Space") -> 146.83 // D3
                else -> 220.0 // A3
            }

            try {
                while (isActive && PlaybackStateHelper.isPlaying.value) {
                    for (i in buffer.indices) {
                        // Math synthesis: lush major pentatonic/chords drone
                        val sine1 = Math.sin(2 * Math.PI * baseFreq * t)
                        val sine2 = 0.4 * Math.sin(2 * Math.PI * (baseFreq * 1.5) * t) // Fifth
                        val sine3 = 0.2 * Math.sin(2 * Math.PI * (baseFreq * 2.0) * t) // Octave
                        val sine4 = 0.15 * Math.sin(2 * Math.PI * (baseFreq * 1.2) * t) // Major Third
                        
                        // LFO (Low-Frequency Oscillator) for swelling amplitude
                        val lfo = 0.7 + 0.3 * Math.sin(2 * Math.PI * 0.15 * t)
                        
                        val combined = (sine1 + sine2 + sine3 + sine4) * lfo
                        buffer[i] = (combined * 6000).toInt().coerceIn(-32768, 32767).toShort()
                        t += 1.0 / sampleRate
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: CancellationException) {
                // Done
            } finally {
                try {
                    audioTrack.stop()
                } catch (e: Exception) {}
                audioTrack.release()
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val track = currentTrack.value ?: break
                if (track.isDemo) {
                    // Manual synthetic progression
                    val nextPos = _playbackPosition.value + 100
                    if (nextPos >= track.duration) {
                        withContext(Dispatchers.Main) {
                            nextTrack()
                        }
                    } else {
                        _playbackPosition.value = nextPos
                    }
                } else {
                    // Real MediaPlayer tracking
                    mediaPlayer?.let { player ->
                        try {
                            if (player.isPlaying) {
                                _playbackPosition.value = player.currentPosition.toLong()
                            }
                        } catch (e: Exception) {
                            // Suppress errors during transitions
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopAllPlayback() {
        progressJob?.cancel()
        synthJob?.cancel()
        synthJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        PlaybackStateHelper.isPlaying.value = false
    }

    private fun startForegroundService() {
        val context = getApplication<Application>()
        if (hasPlaybackStarted) {
            val intent = android.content.Intent(context, com.example.service.PlaybackService::class.java).apply {
                action = com.example.service.PlaybackService.ACTION_UPDATE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startForegroundService", e)
            }
        }
    }

    private fun stopService() {
        val context = getApplication<Application>()
        try {
            val intent = android.content.Intent(context, com.example.service.PlaybackService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stopService", e)
        }
    }

    // --- STATE MODIFIERS ---

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterMinDurationMs(ms: Long) {
        _filterMinDurationMs.value = ms
    }

    fun setHomeSortOption(option: TrackSortOption) {
        _homeSortOption.value = option
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    private fun sortTracks(tracks: List<Track>, sortOption: TrackSortOption, aiSortedIds: List<Long>): List<Track> {
        return when (sortOption) {
            TrackSortOption.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
            TrackSortOption.TITLE -> tracks.sortedBy { it.title.lowercase() }
            TrackSortOption.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            TrackSortOption.DURATION -> tracks.sortedByDescending { it.duration }
            TrackSortOption.AI_DESC -> {
                if (aiSortedIds.isEmpty()) tracks
                else {
                    val idMap = aiSortedIds.withIndex().associate { it.value to it.index }
                    tracks.sortedBy { idMap[it.id] ?: Int.MAX_VALUE }
                }
            }
        }
    }

    // --- MEDIASTORE SCAN (LOCAL SONGS) ---

    fun scanDeviceMusic(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _playerError.value = null
            val contentResolver = context.contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )

            try {
                val cursor = contentResolver.query(uri, projection, selection, null, null)
                var scannedCount = 0
                if (cursor != null) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: "Canción sin Título"
                        val artist = cursor.getString(artistCol) ?: "Artista Desconocido"
                        val album = cursor.getString(albumCol) ?: "Álbum Desconocido"
                        val duration = cursor.getLong(durationCol)

                        // Scoped-Storage-safe: Build Content URI
                        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()

                        // Insert or ignore
                        val track = Track(
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = songUri,
                            category = "Biblioteca",
                            isDemo = false
                        )
                        repository.insertTrack(track)
                        scannedCount++
                    }
                    cursor.close()
                    Log.d(TAG, "Scanned and saved $scannedCount library songs securely using ContentUris.")
                } else {
                    withContext(Dispatchers.Main) {
                        _playerError.value = "Biblioteca de música del teléfono inaccesible."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning MediaStore", e)
                withContext(Dispatchers.Main) {
                    _playerError.value = "Error al escanear la biblioteca musical: ${e.localizedMessage}"
                }
            }
        }
    }

    // --- SERVICE DATA MODIFIERS ---

    fun createPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            repository.insertPlaylist(Playlist(name = name, description = desc))
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            repository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch {
            val current = currentTrack.value
            if (current != null && current.id == trackId) {
                stopAllPlayback()
                PlaybackStateHelper.currentTrack.value = null
            }
            repository.deleteTrack(trackId)
        }
    }

    // --- GEMINI ACTIONS (INTEGRATIONS) ---

    /**
     * Researches official metadata online and updates the track database record
     */
    fun researchTrackMetadata(track: Track) {
        _aiRetrievalState.value = AiRetrievalState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            if (!com.example.network.GeminiApiClient.isApiKeyConfigured()) {
                _aiRetrievalState.value = AiRetrievalState.Error(
                    "API Key de Gemini no configurada.\n\nPor favor, ve al panel de 'Secrets' 🔑 (icono de llave en AI Studio), agrega la clave GEMINI_API_KEY con tu API Key y reinicia la app."
                )
                return@launch
            }

            try {
                val metadata = GeminiMusicAnalyzer.researchSongMetadata(track.title, track.artist)
                val updatedTrack = track.copy(
                    title = metadata.title,
                    artist = metadata.artist,
                    album = metadata.album,
                    category = metadata.category,
                    aiAnalysis = metadata.description
                )
                repository.updateTrack(updatedTrack)
                
                // Clear and sync current tracks if modified
                if (currentTrack.value?.id == track.id) {
                    PlaybackStateHelper.currentTrack.value = updatedTrack
                }

                _aiRetrievalState.value = AiRetrievalState.Success(
                    metadata.title, metadata.artist, metadata.album, metadata.category, metadata.description
                )
            } catch (e: Exception) {
                Log.e(TAG, "Metadata retrieve failed", e)
                _aiRetrievalState.value = AiRetrievalState.Error("Error al conectar con Gemini: ${e.localizedMessage}")
            }
        }
    }

    fun clearAiRetrievalState() {
        _aiRetrievalState.value = AiRetrievalState.Idle
    }

    /**
     * Retrieve lyrics for the given track using Gemini AI
     */
    fun fetchLyrics(track: Track) {
        _lyricsLoading.value = true
        _lyricsError.value = null
        _lyricsState.value = null
        viewModelScope.launch(Dispatchers.IO) {
            if (!com.example.network.GeminiApiClient.isApiKeyConfigured()) {
                _lyricsError.value = "API Key de Gemini no configurada."
                _lyricsLoading.value = false
                return@launch
            }

            try {
                val lyrics = GeminiLyricsSearcher.searchLyricsOnline(track.title, track.artist)
                val updatedTrack = track.copy(lyrics = lyrics)
                repository.updateTrack(updatedTrack)
                
                if (currentTrack.value?.id == track.id) {
                    PlaybackStateHelper.currentTrack.value = updatedTrack
                }
                
                _lyricsState.value = lyrics
            } catch (e: Exception) {
                Log.e(TAG, "Lyrics retrieval failed", e)
                _lyricsError.value = e.localizedMessage
            } finally {
                _lyricsLoading.value = false
            }
        }
    }

    fun loadLocalLyrics(track: Track) {
        _lyricsState.value = track.lyrics
        _lyricsLoading.value = false
        _lyricsError.value = null
    }

    /**
     * Analyzes song vibe, frequency/mood, and musical influences via Gemini AI
     */
    fun analyzeVibeAndInfluences(track: Track) {
        _musicAnalysisLoading.value = true
        _musicAnalysisError.value = null
        _musicAnalysisState.value = null
        viewModelScope.launch(Dispatchers.IO) {
            if (!com.example.network.GeminiApiClient.isApiKeyConfigured()) {
                _musicAnalysisError.value = "API Key no configurada."
                _musicAnalysisLoading.value = false
                return@launch
            }

            try {
                val result = GeminiMusicAnalyzer.analyzeSong(track.title, track.artist, track.lyrics)
                val updatedTrack = track.copy(
                    aiVibe = result.mood,
                    aiInfluences = result.influences,
                    aiAnalysis = result.summary
                )
                repository.updateTrack(updatedTrack)

                if (currentTrack.value?.id == track.id) {
                    PlaybackStateHelper.currentTrack.value = updatedTrack
                }

                _musicAnalysisState.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Vibe analysis failed", e)
                _musicAnalysisError.value = e.localizedMessage
            } finally {
                _musicAnalysisLoading.value = false
            }
        }
    }

    fun loadLocalAnalysis(track: Track) {
        if (track.aiVibe != null && track.aiInfluences != null && track.aiAnalysis != null) {
            _musicAnalysisState.value = AnalysisResult(track.aiVibe, track.aiInfluences, track.aiAnalysis)
        } else {
            _musicAnalysisState.value = null
        }
        _musicAnalysisLoading.value = false
        _musicAnalysisError.value = null
    }

    /**
     * Reorganize the current displayed tracks using Gemini AI based on custom user directions
     */
    fun reorganizeTracksWithAi(instruction: String) {
        _aiSortingLoading.value = true
        _aiSortingError.value = null
        val currentTracks = displayedTracks.value

        viewModelScope.launch(Dispatchers.IO) {
            if (!com.example.network.GeminiApiClient.isApiKeyConfigured()) {
                _aiSortingError.value = "La API Key de Gemini no está configurada o es inválida."
                _aiSortingLoading.value = false
                return@launch
            }

            try {
                val sortedIds = GeminiMusicAnalyzer.organizeSongsWithAi(currentTracks, instruction)
                withContext(Dispatchers.Main) {
                    _homeAiSortedIds.value = sortedIds
                    _homeSortOption.value = TrackSortOption.AI_DESC
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI list sorting failed", e)
                _aiSortingError.value = "Error al conectar con Gemini: ${e.localizedMessage}"
            } finally {
                _aiSortingLoading.value = false
            }
        }
    }

    fun clearAiSorting() {
        _homeAiSortedIds.value = emptyList()
        _homeSortOption.value = TrackSortOption.DATE_ADDED
    }

    // --- RE-INITIALIZATION BACKUPS (DEMO TRACKS) ---

    private suspend fun insertDemoTracks() {
        val demos = listOf(
            Track(
                title = "Ondas Alpha (Lullaby Zen)",
                artist = "Frecuencia Cósmica Synth",
                album = "Sonidos del Vacío",
                duration = 180000,
                path = "demo_waves_alpha",
                category = "Relajación",
                isDemo = true,
                coverArtUrl = "",
                dateAdded = System.currentTimeMillis() - 1000
            ),
            Track(
                title = "Estrella Errante (Electro Space)",
                artist = "Púlsar Cósmico",
                album = "Vía Láctea",
                duration = 240000,
                path = "demo_star_errant",
                category = "Synthwave",
                isDemo = true,
                coverArtUrl = "",
                dateAdded = System.currentTimeMillis() - 2000
            ),
            Track(
                title = "Sueño Lúcido (Lo-Fi Dream)",
                artist = "Sintetizador Orbital",
                album = "Frecuencias del Alma",
                duration = 300000,
                path = "demo_dream_lucid",
                category = "Lo-Fi",
                isDemo = true,
                coverArtUrl = "",
                dateAdded = System.currentTimeMillis() - 3000
            )
        )
        for (demo in demos) {
            repository.insertTrack(demo)
        }
    }
}
