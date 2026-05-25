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
import android.content.Context
import android.media.audiofx.Equalizer
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager

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

            // Extract automatic cover art from metadata
            var extractedCoverUrl = ""
            val embeddedPic = retriever.embeddedPicture
            if (embeddedPic != null) {
                try {
                    val cacheDir = getApplication<Application>().cacheDir
                    val coverFile = java.io.File(cacheDir, "cover_${uniqueId}.jpg")
                    coverFile.writeBytes(embeddedPic)
                    extractedCoverUrl = "file://" + coverFile.absolutePath
                } catch (ce: Exception) {
                    android.util.Log.e("MusicViewModel", "Error saving extracted album art to cache", ce)
                }
            }

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
                playCount = 0,
                coverUrl = extractedCoverUrl
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
    private var mediaPlayer: android.media.MediaPlayer? = null

    fun getTrackFilePath(track: Track): String? {
        if (track.category != "Local") return null
        val pathKeyword = "Archivo local escaneado en:\n"
        val idx = track.lyrics.indexOf(pathKeyword)
        if (idx >= 0) {
            val pathStart = idx + pathKeyword.length
            val endOfLine = track.lyrics.indexOf("\n", pathStart)
            return if (endOfLine >= 0) {
                track.lyrics.substring(pathStart, endOfLine).trim()
            } else {
                track.lyrics.substring(pathStart).trim()
            }
        }
        val pathKeywordAlt = "Archivo local escaneado en:\r\n"
        val idxAlt = track.lyrics.indexOf(pathKeywordAlt)
        if (idxAlt >= 0) {
            val pathStart = idxAlt + pathKeywordAlt.length
            val endOfLine = track.lyrics.indexOf("\r\n", pathStart)
            return if (endOfLine >= 0) {
                track.lyrics.substring(pathStart, endOfLine).trim()
            } else {
                track.lyrics.substring(pathStart).trim()
            }
        }
        return null
    }

    private fun playAudioWithMediaPlayer(track: Track) {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null

        val path = getTrackFilePath(track)
        if (path != null) {
            val file = java.io.File(path)
            if (file.exists()) {
                try {
                    val mp = android.media.MediaPlayer().apply {
                        setWakeMode(getApplication(), android.os.PowerManager.PARTIAL_WAKE_LOCK)
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        
                        try {
                            setupEqualizer(audioSessionId, track.id)
                        } catch (e: Exception) {
                            android.util.Log.e("MusicViewModel", "Error initializing Equalizer on session", e)
                        }

                        setOnCompletionListener {
                            viewModelScope.launch {
                                if (_isRepeatTask.value) {
                                    seekTo(0L)
                                    this@apply.start()
                                } else {
                                    nextTrack()
                                }
                            }
                        }
                    }
                    mediaPlayer = mp
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Error initialising real MediaPlayer for path: $path", e)
                }
            }
        }
    }

    // --- New Metadata and Device Ringtone management methods ---
    fun deleteTrack(trackId: Long) {
        viewModelScope.launch {
            repository.deleteTrack(trackId)
        }
    }

    fun updateTrack(track: Track) {
        viewModelScope.launch {
            repository.updateTrack(track)
        }
    }

    private val _aiRetrievalState = MutableStateFlow<AiRetrievalState>(AiRetrievalState.Idle)
    val aiRetrievalState: StateFlow<AiRetrievalState> = _aiRetrievalState.asStateFlow()

    fun resetAiMetadataState() {
        _aiRetrievalState.value = AiRetrievalState.Idle
    }

    fun researchMetadataWithAi(title: String, artist: String) {
        _aiRetrievalState.value = AiRetrievalState.Loading
        viewModelScope.launch {
            try {
                val result = GeminiMusicAnalyzer.researchSongMetadata(title, artist)
                if (result != null) {
                    _aiRetrievalState.value = AiRetrievalState.Success(result)
                } else {
                    _aiRetrievalState.value = AiRetrievalState.Error("No se pudo obtener información inteligente para esta canción.")
                }
            } catch (e: Exception) {
                _aiRetrievalState.value = AiRetrievalState.Error("Error: ${e.message}")
            }
        }
    }

    fun trimTrack(track: Track, startMs: Long, endMs: Long, completion: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = getTrackFilePath(track)
            if (path == null) {
                completion(null)
                return@launch
            }
            val originalFile = java.io.File(path)
            if (!originalFile.exists()) {
                completion(null)
                return@launch
            }
            
            try {
                val ext = originalFile.extension
                val nameWithoutExt = originalFile.nameWithoutExtension
                val outputName = "${nameWithoutExt}_trim_${startMs/1000}s_${endMs/1000}s.$ext"
                val outputFile = java.io.File(originalFile.parentFile, outputName)
                
                // Trim proportion calculation
                val totalDuration = track.durationMs
                val fileLength = originalFile.length()
                if (fileLength > 0 && totalDuration > 0) {
                    val startPct = startMs.toDouble() / totalDuration.toDouble()
                    val endPct = endMs.toDouble() / totalDuration.toDouble()
                    
                    val startByte = (fileLength * startPct).toLong().coerceIn(0L, fileLength)
                    val endByte = (fileLength * endPct).toLong().coerceIn(startByte, fileLength)
                    val bytesToRead = endByte - startByte
                    
                    originalFile.inputStream().use { input ->
                        input.skip(startByte)
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(4096)
                            var bytesLeft = bytesToRead
                            while (bytesLeft > 0) {
                                val toRead = Math.min(buffer.size.toLong(), bytesLeft).toInt()
                                val read = input.read(buffer, 0, toRead)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesLeft -= read
                            }
                        }
                    }
                } else {
                    originalFile.copyTo(outputFile, overwrite = true)
                }

                val uniqueId = outputFile.absolutePath.hashCode().toLong().let { if (it < 0) -it else it }
                val trimmedTrack = Track(
                    id = uniqueId,
                    title = "${track.title} (Recortado)",
                    artist = track.artist,
                    album = track.album,
                    durationMs = endMs - startMs,
                    category = "Local",
                    lyrics = "Archivo local recortado de ${startMs/1000}s a ${endMs/1000}s.\nArchivo escaneado en:\n${outputFile.absolutePath}",
                    accentColorHex = track.accentColorHex,
                    isFavorite = false,
                    playCount = 0,
                    coverUrl = track.coverUrl
                )
                repository.insertTracks(listOf(trimmedTrack))
                completion(outputFile.absolutePath)
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error recortando pista", e)
                completion(null)
            }
        }
    }

    fun setAsRingtone(track: Track, context: android.content.Context, completion: (Boolean, String) -> Unit) {
        val path = getTrackFilePath(track)
        if (path == null) {
            completion(false, "El archivo elegido no es local o no se puede encontrar su ruta.")
            return
        }
        val file = java.io.File(path)
        if (!file.exists()) {
            completion(false, "El archivo real no existe en el almacenamiento.")
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.System.canWrite(context)) {
                completion(false, "PERMISSION_REQUIRED")
                return
            }
        }

        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DATA, file.absolutePath)
                put(android.provider.MediaStore.MediaColumns.TITLE, track.title)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp3")
                put(android.provider.MediaStore.Audio.Media.IS_RINGTONE, true)
                put(android.provider.MediaStore.Audio.Media.IS_NOTIFICATION, false)
                put(android.provider.MediaStore.Audio.Media.IS_ALARM, false)
                put(android.provider.MediaStore.Audio.Media.IS_MUSIC, false)
            }

            val uri = android.provider.MediaStore.Audio.Media.getContentUriForPath(file.absolutePath)
            if (uri != null) {
                try {
                    context.contentResolver.delete(uri, "${android.provider.MediaStore.MediaColumns.DATA}=?", arrayOf(file.absolutePath))
                } catch (e: Exception) {}

                val newUri = context.contentResolver.insert(uri, contentValues)
                if (newUri != null) {
                    android.media.RingtoneManager.setActualDefaultRingtoneUri(
                        context,
                        android.media.RingtoneManager.TYPE_RINGTONE,
                        newUri
                    )
                    completion(true, "¡Tono de llamada establecido con éxito!")
                } else {
                    completion(false, "No se pudo insertar el tono de llamada en la base de datos de medios.")
                }
            } else {
                completion(false, "No se encontró el URI de contenido de almacenamiento.")
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error setting ringtone", e)
            completion(false, "Error al configurar: ${e.message}")
        }
    }

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
            }.distinctUntilChanged().collect { (track, playing) ->
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

            playAudioWithMediaPlayer(track)
            restartSimulationJobs()
        }
    }

    fun togglePlayPause() {
        if (_currentTrack.value == null) return
        _isPlaying.value = !_isPlaying.value
        
        mediaPlayer?.let { mp ->
            try {
                if (_isPlaying.value) {
                    mp.start()
                } else {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error toggling MediaPlayer play/pause", e)
            }
        }

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
            _isShuffle.value -> Random.nextInt(q.size)
            index > 0 -> index - 1
            else -> q.size - 1 // Loop to last track
        }
        _queueIndex.value = prevIndex
        playTrackNow(q[prevIndex], q)
    }

    fun seekTo(progressMs: Long) {
        val maxDuration = _currentTrack.value?.durationMs ?: 0L
        val targetProgress = progressMs.coerceIn(0L, maxDuration)
        _currentProgress.value = targetProgress
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(targetProgress.toInt())
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error seeking MediaPlayer", e)
            }
        }
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
                    val mp = mediaPlayer
                    if (mp != null) {
                        try {
                            _currentProgress.value = mp.currentPosition.toLong()
                        } catch (e: Exception) {
                            val nextProgress = _currentProgress.value + 1000L
                            _currentProgress.value = nextProgress.coerceAtMost(curTrack.durationMs)
                        }
                    } else {
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

        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                     it.pause()
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error pausing MediaPlayer inside stopSimulationJobs", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSimulationJobs()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {}
        }
        mediaPlayer = null
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null
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

    // === EQUALIZER STATES & CONTROLS ===
    private var equalizer: android.media.audiofx.Equalizer? = null
    private val eqPrefs by lazy {
        getApplication<Application>().getSharedPreferences("music_equalizer_prefs", Context.MODE_PRIVATE)
    }

    private val _equalizerBands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()

    private val _currentEqualizerTrackId = MutableStateFlow<Long?>(null)
    val currentEqualizerTrackId: StateFlow<Long?> = _currentEqualizerTrackId.asStateFlow()

    fun getSavedEqualizerBands(trackId: Long): List<Int>? {
        val savedStr = eqPrefs.getString("eq_$trackId", null) ?: return null
        return try {
            savedStr.split(",").map { it.toInt() }
        } catch (e: Exception) {
            null
        }
    }

    fun saveEqualizerBands(trackId: Long, bands: List<Int>) {
        val str = bands.joinToString(",")
        eqPrefs.edit().putString("eq_$trackId", str).apply()
    }

    fun setupEqualizer(sessionId: Int, trackId: Long) {
        try {
            equalizer?.release()
            val eq = android.media.audiofx.Equalizer(0, sessionId)
            eq.enabled = true
            equalizer = eq

            val savedBands = getSavedEqualizerBands(trackId)
            if (savedBands != null) {
                val numBands = eq.numberOfBands.toInt()
                for (i in 0 until numBands) {
                    if (i < savedBands.size) {
                        val mB = savedBands[i]
                        val range = eq.bandLevelRange
                        val minLevel = range[0]
                        val maxLevel = range[1]
                        val level = mB.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                        eq.setBandLevel(i.toShort(), level)
                    }
                }
            } else {
                val numBands = eq.numberOfBands.toInt()
                for (i in 0 until numBands) {
                    eq.setBandLevel(i.toShort(), 0)
                }
            }
            refreshEqualizerBandsForTrack(trackId)
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error setting up equalizer", e)
        }
    }

    fun refreshEqualizerBandsForTrack(trackId: Long) {
        _currentEqualizerTrackId.value = trackId
        val eq = equalizer
        if (eq != null) {
            try {
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()

                val bands = (0 until numBands).map { i ->
                    val freq = eq.getCenterFreq(i.toShort()) / 1000 // In Hz
                    val currentSavedList = getSavedEqualizerBands(trackId)
                    val level = if (currentSavedList != null && i < currentSavedList.size) {
                        currentSavedList[i]
                    } else {
                        eq.getBandLevel(i.toShort()).toInt()
                    }
                    EqualizerBand(
                        bandIndex = i,
                        centerFreqHz = freq,
                        minLevelMb = minLevel,
                        maxLevelMb = maxLevel,
                        currentLevelMb = level
                    )
                }
                _equalizerBands.value = bands
                return
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to query live equalizer bands", e)
            }
        }

        // Fallback: standard 5 bands
        val minLevel = -1500
        val maxLevel = 1500
        val standardFrequencies = listOf(60, 230, 910, 4000, 14000)
        val saved = getSavedEqualizerBands(trackId) ?: listOf(0, 0, 0, 0, 0)
        val bands = standardFrequencies.mapIndexed { i, freq ->
            val level = if (i < saved.size) saved[i] else 0
            EqualizerBand(
                bandIndex = i,
                centerFreqHz = freq,
                minLevelMb = minLevel,
                maxLevelMb = maxLevel,
                currentLevelMb = level
            )
        }
        _equalizerBands.value = bands
    }

    fun updateBandLevel(trackId: Long, bandIndex: Int, levelMb: Int) {
        val eq = equalizer
        if (eq != null) {
            try {
                val range = eq.bandLevelRange
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val clampedLevel = levelMb.coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(bandIndex.toShort(), clampedLevel)
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Error setting active eq band", e)
            }
        }

        val currentSaved = getSavedEqualizerBands(trackId) ?: listOf(0, 0, 0, 0, 0)
        val updatedBands = currentSaved.toMutableList()
        if (bandIndex in updatedBands.indices) {
            updatedBands[bandIndex] = levelMb
        } else {
            while (updatedBands.size <= bandIndex) {
                updatedBands.add(0)
            }
            updatedBands[bandIndex] = levelMb
        }
        saveEqualizerBands(trackId, updatedBands)
        refreshEqualizerBandsForTrack(trackId)
    }

    // === BLUETOOTH STATES & CONTROLS ===
    private val _bluetoothConnectedDeviceName = MutableStateFlow<String>("Altavoz del Teléfono")
    val bluetoothConnectedDeviceName: StateFlow<String> = _bluetoothConnectedDeviceName.asStateFlow()

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothDeviceRepresentation>>(emptyList())
    val bluetoothDevices: StateFlow<List<BluetoothDeviceRepresentation>> = _bluetoothDevices.asStateFlow()

    fun selectBluetoothDevice(name: String) {
        _bluetoothConnectedDeviceName.value = name
    }

    fun refreshBluetoothDevices() {
        _bluetoothDevices.value = getPairedBluetoothDevices(getApplication())
    }

    fun getPairedBluetoothDevices(context: Context): List<BluetoothDeviceRepresentation> {
        val devices = mutableListOf<BluetoothDeviceRepresentation>()
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                if (hasPermission) {
                    val bonded = adapter.bondedDevices
                    if (!bonded.isNullOrEmpty()) {
                        for (device in bonded) {
                            devices.add(
                                BluetoothDeviceRepresentation(
                                    address = device.address,
                                    name = device.name ?: "Dispositivo sin nombre",
                                    isConnected = false,
                                    isBonded = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error fetching Bluetooth devices", e)
        }

        val defaultDevices = listOf(
            BluetoothDeviceRepresentation("11:22:33:44:55:66", "Pixel Buds Pro", false, false),
            BluetoothDeviceRepresentation("00:11:22:33:44:55", "Sony WH-1000XM4", false, false),
            BluetoothDeviceRepresentation("AA:BB:CC:DD:EE:FF", "Bose QuietComfort 45", false, false),
            BluetoothDeviceRepresentation("AA:22:BB:33:CC:44", "Altavoz JBL Flip 6", false, false)
        )

        return (devices + defaultDevices).distinctBy { it.name }
    }
}

data class EqualizerBand(
    val bandIndex: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
)

data class BluetoothDeviceRepresentation(
    val address: String,
    val name: String,
    val isConnected: Boolean,
    val isBonded: Boolean
)

sealed class AiRetrievalState {
    object Idle : AiRetrievalState()
    object Loading : AiRetrievalState()
    data class Success(val metadata: com.example.network.ResearchMetadata) : AiRetrievalState()
    data class Error(val message: String) : AiRetrievalState()
}
