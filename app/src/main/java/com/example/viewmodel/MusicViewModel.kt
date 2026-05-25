package com.example.viewmodel

import android.app.Application
import com.example.network.GeminiLyricsSearcher
import com.example.network.MusicAnalysis
import com.example.network.GeminiMusicAnalyzer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random

import com.example.ui.theme.ThemeColorOption

enum class ActiveTab {
    HOME, SEARCH, LIBRARY, SETTINGS
}

class MusicViewModel(application: Application, private val repository: MusicRepository) : AndroidViewModel(application) {

    // Theme Color Option for Dynamic Styling
    private val sharedPrefs by lazy {
        getApplication<Application>().getSharedPreferences("app_settings_prefs", android.content.Context.MODE_PRIVATE)
    }

    private val _themeColorOption = MutableStateFlow<ThemeColorOption>(ThemeColorOption.BLUE)
    val themeColorOption: StateFlow<ThemeColorOption> = _themeColorOption.asStateFlow()

    fun setThemeColorOption(option: ThemeColorOption) {
        _themeColorOption.value = option
        sharedPrefs.edit().putString("selected_theme_color", option.name).apply()
    }

    // === MUSIC FILE SYSTEM SCANNER & STORAGE STATES ===
    private val _musicFolders = MutableStateFlow<Set<String>>(emptySet())
    val musicFolders: StateFlow<Set<String>> = _musicFolders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanStatus = MutableStateFlow<String?>(null)
    val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

    fun addMusicFolder(path: String) {
        val current = _musicFolders.value.toMutableSet()
        current.add(path)
        _musicFolders.value = current
        sharedPrefs.edit().putStringSet("music_folder_paths", current).apply()
    }

    fun removeMusicFolder(path: String) {
        val current = _musicFolders.value.toMutableSet()
        current.remove(path)
        _musicFolders.value = current
        sharedPrefs.edit().putStringSet("music_folder_paths", current).apply()
    }

    fun scanFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanStatus.value = "Iniciando escaneo..."
            delay(1000)

            val tracksToAdd = mutableListOf<Track>()
            val foldersToScan = _musicFolders.value

            foldersToScan.forEach { folderPath ->
                val directory = java.io.File(folderPath)
                if (directory.exists() && directory.isDirectory) {
                    scanDirectoryRecursive(directory, tracksToAdd, maxDepth = 2)
                }
            }

            if (tracksToAdd.isNotEmpty()) {
                repository.insertTracks(tracksToAdd)
                _scanStatus.value = "Escaneo completado. Se agregaron ${tracksToAdd.size} canciones locales."
            } else {
                _scanStatus.value = "Escaneo completado. No se encontraron archivos nuevos."
            }
            _isScanning.value = false
        }
    }

    private fun scanDirectoryRecursive(file: java.io.File, list: MutableList<Track>, maxDepth: Int, currentDepth: Int = 0) {
        if (currentDepth > maxDepth) return
        val files = file.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                scanDirectoryRecursive(f, list, maxDepth, currentDepth + 1)
            } else if (f.isFile) {
                val name = f.name.lowercase()
                if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".ogg")) {
                    val track = extractTrackMetadata(f) ?: continue
                    list.add(track)
                }
            }
        }
    }

    private fun extractTrackMetadata(file: java.io.File): Track? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Artista Local"
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Almacenamiento Local"
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 180000L

            val uniqueId = file.absolutePath.hashCode().toLong().let { if (it < 0) -it else it }
            val accentColors = listOf("#0288D1", "#00897B", "#F57C00", "#D81B60", "#5E35B1")
            val accentColor = accentColors[Math.abs(uniqueId.toInt() % accentColors.size)]

            Track(
                id = uniqueId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                category = "Local",
                lyrics = "Archivo local escaneado en:\n${file.absolutePath}\n\nLetra no disponible.",
                accentColorHex = accentColor,
                isFavorite = false,
                playCount = 0
            )
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error leyendo metadatos del archivo ${file.name}", e)
            val uniqueId = file.absolutePath.hashCode().toLong().let { if (it < 0) -it else it }
            Track(
                id = uniqueId,
                title = file.nameWithoutExtension,
                artist = "Artista Local",
                album = "Carpeta Local",
                durationMs = 180000L,
                category = "Local",
                lyrics = "Archivo local escaneado en:\n${file.absolutePath}\n\nLetra no disponible.",
                accentColorHex = "#78909C",
                isFavorite = false,
                playCount = 0
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    fun createDemoTrackInFolder(folderPath: String, title: String, artist: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = java.io.File(folderPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val filename = "${title.replace(" ", "_")}.mp3"
                val targetFile = java.io.File(dir, filename)
                targetFile.writeText("Dummy audio metadata representation for $title by $artist")
                _scanStatus.value = "Canción demo '$title' creada con éxito."
                
                // Refresh scan
                scanFolders()
            } catch (e: Exception) {
                _scanStatus.value = "Error creando demo: ${e.message}"
            }
        }
    }

    fun resetScanStatus() {
        _scanStatus.value = null
    }

    // === Lists ===
    val allTracks: StateFlow<List<Track>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === TRACK FILTER STATES (DURATION & FOLDERS) ===
    private val _filterMinDurationMs = MutableStateFlow(0L) // 0 seconds
    val filterMinDurationMs: StateFlow<Long> = _filterMinDurationMs.asStateFlow()

    private val _filterMaxDurationMs = MutableStateFlow(600000L) // 10 minutes default max
    val filterMaxDurationMs: StateFlow<Long> = _filterMaxDurationMs.asStateFlow()

    private val _filterFolderPath = MutableStateFlow<String?>(null)
    val filterFolderPath: StateFlow<String?> = _filterFolderPath.asStateFlow()

    fun setMinDurationFilter(ms: Long) {
        _filterMinDurationMs.value = ms
    }

    fun setMaxDurationFilter(ms: Long) {
        _filterMaxDurationMs.value = ms
    }

    fun setFolderPathFilter(path: String?) {
        _filterFolderPath.value = path
    }

    fun resetFilters() {
        _filterMinDurationMs.value = 0L
        _filterMaxDurationMs.value = 600000L // 10 minutes (600,000 ms)
        _filterFolderPath.value = null
    }

    val filteredTracks: StateFlow<List<Track>> = combine(
        allTracks,
        _filterMinDurationMs,
        _filterMaxDurationMs,
        _filterFolderPath
    ) { tracks, minMs, maxMs, folder ->
        tracks.filter { track ->
            val durationOk = track.durationMs in minMs..maxMs
            val folderOk = if (folder == null) {
                true
            } else {
                if (track.category == "Local") {
                    val pathKeyword = "Archivo local escaneado en:\n"
                    val idx = track.lyrics.indexOf(pathKeyword)
                    if (idx >= 0) {
                        val pathStart = idx + pathKeyword.length
                        val endOfLine = track.lyrics.indexOf("\n", pathStart)
                        val fullPath = if (endOfLine >= 0) track.lyrics.substring(pathStart, endOfLine) else track.lyrics.substring(pathStart)
                        fullPath.startsWith(folder)
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            durationOk && folderOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === UI Navigation & Search Mode ===
    private val _activeTab = MutableStateFlow(ActiveTab.HOME)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<Track>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPlaylistTracks: StateFlow<List<Track>> = selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) repository.getTracksForPlaylist(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<Track>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            repository.searchTracks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === Player Engine ===
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentProgress = MutableStateFlow(0L)
    val currentProgress: StateFlow<Long> = _currentProgress.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeatTask = MutableStateFlow(false) // false = repeat off, true = repeat current track
    val isRepeatTask: StateFlow<Boolean> = _isRepeatTask.asStateFlow()

    private val _audioWaveAmplitudes = MutableStateFlow(List(16) { 0.15f })
    val audioWaveAmplitudes: StateFlow<List<Float>> = _audioWaveAmplitudes.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics: StateFlow<Boolean> = _isSearchingLyrics.asStateFlow()

    private val _lyricsSearchError = MutableStateFlow<String?>(null)
    val lyricsSearchError: StateFlow<String?> = _lyricsSearchError.asStateFlow()

    private val _isAnalyzingMusic = MutableStateFlow(false)
    val isAnalyzingMusic: StateFlow<Boolean> = _isAnalyzingMusic.asStateFlow()

    private val _musicAnalysisError = MutableStateFlow<String?>(null)
    val musicAnalysisError: StateFlow<String?> = _musicAnalysisError.asStateFlow()

    private val _currentTrackAnalysis = MutableStateFlow<MusicAnalysis?>(null)
    val currentTrackAnalysis: StateFlow<MusicAnalysis?> = _currentTrackAnalysis.asStateFlow()

    // Jobs
    private var playbackJob: Job? = null
    private var visualizerJob: Job? = null

    init {
        // Load persisted dynamic appearance theme
        val savedThemeName = sharedPrefs.getString("selected_theme_color", ThemeColorOption.BLUE.name)
        val initialOption = try {
            ThemeColorOption.valueOf(savedThemeName ?: ThemeColorOption.BLUE.name)
        } catch (e: Exception) {
            ThemeColorOption.BLUE
        }
        _themeColorOption.value = initialOption

        // Load persisted or initialized dynamic storage folders
        val savedFolders = sharedPrefs.getStringSet("music_folder_paths", null)
        if (savedFolders != null) {
            _musicFolders.value = savedFolders
        } else {
            val defaultMusicFolder = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC).absolutePath
            _musicFolders.value = setOf(defaultMusicFolder)
            sharedPrefs.edit().putStringSet("music_folder_paths", setOf(defaultMusicFolder)).apply()
        }

        // Reset analysis when current track ID changes (to avoid displaying stale information)
        viewModelScope.launch {
            _currentTrack.collect { track ->
                if (track == null || track.id != _currentTrackAnalysis.value?.trackId) {
                    _currentTrackAnalysis.value = null
                    _musicAnalysisError.value = null
                }
            }
        }

        // Link Actions to the Notification system
        com.example.service.PlaybackStateHelper.onPlayPauseAction = { togglePlayPause() }
        com.example.service.PlaybackStateHelper.onNextAction = { nextTrack() }
        com.example.service.PlaybackStateHelper.onPrevAction = { previousTrack() }

        // Keep track of whether music playback has started at least once to avoid starting service on launch
        var hasPlaybackStarted = false

        // Automatically sync state flow updates to Notification Service
        viewModelScope.launch {
            combine(_currentTrack, _isPlaying) { track, playing ->
                Pair(track, playing)
            }.collect { (track, playing) ->
                com.example.service.PlaybackStateHelper.currentTrack.value = track
                com.example.service.PlaybackStateHelper.isPlaying.value = playing
                
                if (playing) {
                    hasPlaybackStarted = true
                }

                if (track != null && hasPlaybackStarted) {
                    val intent = android.content.Intent(getApplication(), com.example.service.PlaybackService::class.java).apply {
                        action = com.example.service.PlaybackService.ACTION_UPDATE
                    }
                    try {
                        if (playing && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            getApplication<Application>().startForegroundService(intent)
                        } else {
                            getApplication<Application>().startService(intent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicViewModel", "Failed to start PlaybackService", e)
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.ensureDefaultTracksPreseeded()
            // Set first track as current track initially (for beautiful UI setup before playing)
            val tracks = repository.allTracks.first()
            if (tracks.isNotEmpty()) {
                _currentTrack.value = tracks.first()
            }
        }
    }

    fun selectTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    // === Playback Core Actions ===
    fun playTrackNow(track: Track, tracksContext: List<Track> = emptyList()) {
        viewModelScope.launch {
            repository.increasePlayCount(track.id)
            _currentTrack.value = track
            _currentProgress.value = 0L
            _isPlaying.value = true

            // Set up queue
            val context = if (tracksContext.isNotEmpty()) tracksContext else listOf(track)
            val index = context.indexOfFirst { it.id == track.id }
            _queue.value = context
            _queueIndex.value = if (index != -1) index else 0

            restartSimulationJobs()
        }
    }

    fun togglePlayPause() {
        if (_currentTrack.value == null) return
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) {
            restartSimulationJobs()
        } else {
            stopSimulationJobs()
        }
    }

    fun nextTrack() {
        val q = _queue.value
        val index = _queueIndex.value
        if (q.isEmpty()) return

        val nextIndex = when {
            _isShuffle.value -> Random.nextInt(q.size)
            index < q.size - 1 -> index + 1
            else -> 0 // Loop to first track
        }
        _queueIndex.value = nextIndex
        playTrackNow(q[nextIndex], q)
    }

    fun previousTrack() {
        val q = _queue.value
        val index = _queueIndex.value
        if (q.isEmpty()) return

        val prevIndex = when {
            index > 0 -> index - 1
            else -> q.size - 1 // Loop to last track
        }
        _queueIndex.value = prevIndex
        playTrackNow(q[prevIndex], q)
    }

    fun seekTo(progressMs: Long) {
        val maxDuration = _currentTrack.value?.durationMs ?: 0L
        _currentProgress.value = progressMs.coerceIn(0L, maxDuration)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _isRepeatTask.value = !_isRepeatTask.value
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(trackId)
            // Update current track if it's the one being favorited
            val cur = _currentTrack.value
            if (cur != null && cur.id == trackId) {
                _currentTrack.value = cur.copy(isFavorite = !cur.isFavorite)
            }
        }
    }

    // === Playlist Actions ===
    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            repository.createPlaylist(name, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
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

    // === Simulation Engine ===
    private fun restartSimulationJobs() {
        stopSimulationJobs()

        // 1. Progress updater
        playbackJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val curTrack = _currentTrack.value
                if (curTrack != null) {
                    val nextProgress = _currentProgress.value + 1000L
                    if (nextProgress >= curTrack.durationMs) {
                        if (_isRepeatTask.value) {
                            _currentProgress.value = 0L
                        } else {
                            nextTrack()
                        }
                    } else {
                        _currentProgress.value = nextProgress
                    }
                }
            }
        }

        // 2. Beautiful soundwave animation amplitudes updater
        visualizerJob = viewModelScope.launch {
            while (true) {
                delay(120)
                _audioWaveAmplitudes.value = List(16) {
                    // Random-ish realistic wave amplitudes
                    Random.nextFloat().coerceIn(0.12f, 0.98f)
                }
            }
        }
    }

    private fun stopSimulationJobs() {
        playbackJob?.cancel()
        playbackJob = null
        visualizerJob?.cancel()
        visualizerJob = null
        _audioWaveAmplitudes.value = List(16) { 0.15f } // Idle state waveform
    }

    override fun onCleared() {
        super.onCleared()
        stopSimulationJobs()
        try {
            val intent = android.content.Intent(getApplication(), com.example.service.PlaybackService::class.java)
            getApplication<Application>().stopService(intent)
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to stop service on cleared", e)
        }
    }

    fun updateTrackLyrics(trackId: Long, newLyrics: String) {
        viewModelScope.launch {
            repository.updateTrackLyrics(trackId, newLyrics)
            val cur = _currentTrack.value
            if (cur != null && cur.id == trackId) {
                _currentTrack.value = cur.copy(lyrics = newLyrics)
            }
        }
    }

    fun searchLyricsOnline(trackId: Long, title: String, artist: String) {
        _isSearchingLyrics.value = true
        _lyricsSearchError.value = null
        viewModelScope.launch {
            try {
                val lyrics = GeminiLyricsSearcher.searchLyricsOnline(title, artist)
                if (lyrics != null) {
                    updateTrackLyrics(trackId, lyrics)
                } else {
                    _lyricsSearchError.value = "No se pudieron obtener las letras automáticamente. Puedes editarlas manualmente o reintentarlo."
                }
            } catch (e: Exception) {
                _lyricsSearchError.value = "Error: ${e.message}"
            } finally {
                _isSearchingLyrics.value = false
            }
        }
    }

    fun analyzeCurrentMusic() {
        val track = _currentTrack.value ?: return
        if (_isAnalyzingMusic.value) return
        _isAnalyzingMusic.value = true
        _musicAnalysisError.value = null
        
        viewModelScope.launch {
            try {
                val analysis = GeminiMusicAnalyzer.analyzeSong(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    lyrics = track.lyrics
                )
                if (analysis != null) {
                    _currentTrackAnalysis.value = analysis
                } else {
                    _musicAnalysisError.value = "No se pudo obtener el análisis inteligente en este momento. Inténtalo de nuevo."
                }
            } catch (e: Exception) {
                _musicAnalysisError.value = "Error: ${e.message}"
            } finally {
                _isAnalyzingMusic.value = false
            }
        }
    }
}
