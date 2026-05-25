package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import com.example.ui.theme.ThemeColorOption
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.Playlist
import com.example.data.Track
import com.example.viewmodel.ActiveTab
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.AiRetrievalState
import java.util.Locale
import kotlin.math.sin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import android.content.Intent
import android.net.Uri
import android.provider.Settings

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentProgress by viewModel.currentProgress.collectAsStateWithLifecycle()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val playLists by viewModel.playlists.collectAsStateWithLifecycle()

    // Request notification permissions for API 33+ devices
    val context = androidx.compose.ui.platform.LocalContext.current
    val postNotificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                postNotificationLauncher.launch(permission)
            }
        }
    }

    var showAddPlaylistDialogTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackBeingManaged by remember { mutableStateOf<Track?>(null) }
    var showEqualizerDialogTrack by remember { mutableStateOf<Track?>(null) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    val connectedDeviceName by viewModel.bluetoothConnectedDeviceName.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (!isPlayerExpanded) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.HOME,
                        onClick = { viewModel.selectTab(ActiveTab.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Inicio") },
                        label = { Text("Inicio") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.SEARCH,
                        onClick = { viewModel.selectTab(ActiveTab.SEARCH) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Buscar") },
                        label = { Text("Buscar") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_search")
                    )
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.LIBRARY,
                        onClick = { viewModel.selectTab(ActiveTab.LIBRARY) },
                        icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Biblioteca") },
                        label = { Text("Biblioteca") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_library")
                    )
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.SETTINGS,
                        onClick = { viewModel.selectTab(ActiveTab.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isPlayerExpanded) 0.dp else innerPadding.calculateBottomPadding())
        ) {
            // Main views based on Tab
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    ActiveTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        paddingValues = innerPadding,
                        onAddToPlaylist = { showAddPlaylistDialogTrack = it },
                        onManageTrack = { trackBeingManaged = it }
                    )
                    ActiveTab.SEARCH -> SearchScreen(
                        viewModel = viewModel,
                        paddingValues = innerPadding,
                        onAddToPlaylist = { showAddPlaylistDialogTrack = it },
                        onManageTrack = { trackBeingManaged = it }
                    )
                    ActiveTab.LIBRARY -> LibraryScreen(
                        viewModel = viewModel,
                        paddingValues = innerPadding,
                        onCreatePlaylistClick = { showCreatePlaylistDialog = true }
                    )
                    ActiveTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        paddingValues = innerPadding
                    )
                }
            }

            // Bottom Mini Player overlay - only visible if a song is loaded and not expanded
            if (currentTrack != null) {
                AnimatedVisibility(
                    visible = !isPlayerExpanded,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                ) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progress = currentProgress,
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onNext = { viewModel.nextTrack() },
                        onClick = { viewModel.setPlayerExpanded(true) }
                    )
                }
            }

            // Full Expanded Player Screen Overlay
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                currentTrack?.let { track ->
                    ExpandedPlayerScreen(
                        track = track,
                        isPlaying = isPlaying,
                        progress = currentProgress,
                        waveAmplitudes = viewModel.audioWaveAmplitudes.collectAsStateWithLifecycle().value,
                        isShuffle = viewModel.isShuffle.collectAsStateWithLifecycle().value,
                        isRepeat = viewModel.isRepeatTask.collectAsStateWithLifecycle().value,
                        queue = viewModel.queue.collectAsStateWithLifecycle().value,
                        isSearchingLyrics = viewModel.isSearchingLyrics.collectAsStateWithLifecycle().value,
                        lyricsSearchError = viewModel.lyricsSearchError.collectAsStateWithLifecycle().value,
                        connectedDeviceName = connectedDeviceName,
                        onSearchLyricsOnline = { trackId, title, artist -> viewModel.searchLyricsOnline(trackId, title, artist) },
                        onUpdateLyrics = { trackId, newLyrics -> viewModel.updateTrackLyrics(trackId, newLyrics) },
                        isAnalyzingMusic = viewModel.isAnalyzingMusic.collectAsStateWithLifecycle().value,
                        musicAnalysisError = viewModel.musicAnalysisError.collectAsStateWithLifecycle().value,
                        currentTrackAnalysis = viewModel.currentTrackAnalysis.collectAsStateWithLifecycle().value,
                        onAnalyzeCurrentMusic = { viewModel.analyzeCurrentMusic() },
                        onClose = { viewModel.setPlayerExpanded(false) },
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onNext = { viewModel.nextTrack() },
                        onPrevious = { viewModel.previousTrack() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onAddToPlaylist = { showAddPlaylistDialogTrack = track },
                        onEqualizerClick = { showEqualizerDialogTrack = track },
                        onBluetoothClick = { showBluetoothDialog = true }
                    )
                }
            }
        }
    }

    // Modal dialog for selecting/creating custom playlist for a loaded Track
    showAddPlaylistDialogTrack?.let { track ->
        PlaylistSelectionDialog(
            playlists = playLists,
            onDismiss = { showAddPlaylistDialogTrack = null },
            onPlaylistSelected = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, track.id)
                showAddPlaylistDialogTrack = null
            },
            onCreateNewPlaylist = {
                showCreatePlaylistDialog = true
            }
        )
    }

    // Modal dialog to create a playlist
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onSubmit = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showCreatePlaylistDialog = false
            }
        )
    }

    if (trackBeingManaged != null) {
        TrackManagementDialog(
            track = trackBeingManaged!!,
            viewModel = viewModel,
            onDismiss = { trackBeingManaged = null }
        )
    }

    showEqualizerDialogTrack?.let { eqTrack ->
        EqualizerDialog(
            track = eqTrack,
            viewModel = viewModel,
            onDismiss = { showEqualizerDialogTrack = null }
        )
    }

    if (showBluetoothDialog) {
        BluetoothDeviceSelectorDialog(
            viewModel = viewModel,
            onDismiss = { showBluetoothDialog = false }
        )
    }
}

// === HOME SCREEN ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    paddingValues: PaddingValues,
    onAddToPlaylist: (Track) -> Unit,
    onManageTrack: (Track) -> Unit
) {
    val allTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val historyTracks by viewModel.history.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val filteredTracks = remember(allTracks, selectedCategory) {
        if (selectedCategory == null) {
            allTracks
        } else {
            allTracks.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    val categories = listOf("Synthwave", "Lofi", "Acoustic", "Jazz", "Pop")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Welcome and Google branding header
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "reproductor",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¡Hola, amante de la música!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        // Horizontal Category Mood Filters (Google Pill styling)
        item {
            Column {
                Text(
                    text = "Tu estado de ánimo",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Todo") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory.equals(cat, ignoreCase = true),
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        // Recently Played list (simulated from History)
        if (historyTracks.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Escuchado recientemente",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(historyTracks) { track ->
                            RecentTrackItem(
                                track = track,
                                onClick = { viewModel.playTrackNow(track, historyTracks) }
                            )
                        }
                    }
                }
            }
        }

        // Popular Tracks (Main compilation)
        item {
            Text(
                text = "Recomendaciones para ti",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (filteredTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay canciones disponibles en esta categoría.")
                }
            }
        } else {
            items(filteredTracks) { track ->
                TrackListItem(
                    track = track,
                    currentTrack = viewModel.currentTrack.collectAsStateWithLifecycle().value,
                    isPlaying = viewModel.isPlaying.collectAsStateWithLifecycle().value,
                    waveAmplitudes = viewModel.audioWaveAmplitudes.collectAsStateWithLifecycle().value,
                    onClick = { viewModel.playTrackNow(track, filteredTracks) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                    onManageTrack = { onManageTrack(track) }
                )
            }
        }
    }
}

// === BUSCAR SCREEN ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    paddingValues: PaddingValues,
    onAddToPlaylist: (Track) -> Unit,
    onManageTrack: (Track) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val allTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    
    val minMs = viewModel.filterMinDurationMs.collectAsStateWithLifecycle().value
    val maxMs = viewModel.filterMaxDurationMs.collectAsStateWithLifecycle().value
    val folder = viewModel.filterFolderPath.collectAsStateWithLifecycle().value

    val filteredSearchResults = remember(searchResults, minMs, maxMs, folder) {
        searchResults.filter { track ->
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
    }

    val categoriesList = listOf(
        Pair("Para Estudiar", "#5C6BC0"),
        Pair("Glow de Noche", "#8E24AA"),
        Pair("Viaje en Moto", "#FF5722"),
        Pair("Silencio Cósmico", "#009688"),
        Pair("Concentración", "#3F51B5"),
        Pair("Energía Pop", "#E91E63")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_screen")
            .padding(top = 24.dp)
    ) {
        // Rounded search bar Google-style
        SearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.search(it) },
            onSearch = {},
            active = false,
            onActiveChange = {},
            placeholder = { Text("Buscar canciones, artistas...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.search("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {}

        Spacer(modifier = Modifier.height(12.dp))

        if (searchQuery.isBlank()) {
            // Recommendation Grids when search is blank
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 120.dp)
            ) {
                Text(
                    text = "Explorar todo",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Grid cards of categories
                val chunkedList = categoriesList.chunked(2)
                chunkedList.forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { (category, colorHex) ->
                            val color = remember(colorHex) { Color(android.graphics.Color.parseColor(colorHex)) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.5f))
                                        )
                                    )
                                    .clickable {
                                        // Pick a filter based on exploration
                                        val filter = when (category) {
                                            "Energía Pop" -> "Pop"
                                            "Glow de Noche" -> "Synthwave"
                                            "Concentración" -> "Lofi"
                                            else -> "Acoustic"
                                        }
                                        viewModel.search(filter)
                                    }
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.align(Alignment.BottomStart)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Search Results list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Resultados para \"$searchQuery\"",
                        style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                if (filteredSearchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SearchOff,
                                contentDescription = "No se encontraron canciones",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No se encontraron resultados de canciones.")
                        }
                    }
                } else {
                    items(filteredSearchResults) { track ->
                        TrackListItem(
                            track = track,
                            currentTrack = viewModel.currentTrack.collectAsStateWithLifecycle().value,
                            isPlaying = viewModel.isPlaying.collectAsStateWithLifecycle().value,
                            waveAmplitudes = viewModel.audioWaveAmplitudes.collectAsStateWithLifecycle().value,
                            onClick = { viewModel.playTrackNow(track, filteredSearchResults) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                            onManageTrack = { onManageTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

// === LIBRARY SCREEN ===
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    paddingValues: PaddingValues,
    onCreatePlaylistClick: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val playlistTracks by viewModel.currentPlaylistTracks.collectAsStateWithLifecycle()

    var activePlaylistForDetail by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(selectedPlaylistId, playlists) {
        if (selectedPlaylistId != null) {
            activePlaylistForDetail = playlists.find { it.id == selectedPlaylistId }
        } else {
            activePlaylistForDetail = null
        }
    }

    if (activePlaylistForDetail != null) {
        // Detailed Playlist view (Google style overlay)
        PlaylistDetailScreen(
            playlist = activePlaylistForDetail!!,
            tracks = playlistTracks,
            currentTrack = viewModel.currentTrack.collectAsStateWithLifecycle().value,
            isPlaying = viewModel.isPlaying.collectAsStateWithLifecycle().value,
            waveAmplitudes = viewModel.audioWaveAmplitudes.collectAsStateWithLifecycle().value,
            onBack = { viewModel.selectPlaylist(null) },
            onPlayAll = {
                if (playlistTracks.isNotEmpty()) {
                    viewModel.playTrackNow(playlistTracks.first(), playlistTracks)
                }
            },
            onDeletePlaylist = {
                viewModel.deletePlaylist(activePlaylistForDetail!!.id)
            },
            onTrackClick = { clicked -> viewModel.playTrackNow(clicked, playlistTracks) },
            onRemoveTrack = { trackId -> viewModel.removeTrackFromPlaylist(activePlaylistForDetail!!.id, trackId) }
        )
    } else {
        // Library Overview
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_screen")
                .padding(start = 16.dp, end = 16.dp, top = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tu Biblioteca",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Button(
                    onClick = onCreatePlaylistClick,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Añadir")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva set")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Liked Songs Quick Entry Row
                item {
                    LibraryCategoryItem(
                        title = "Canciones que te gustan",
                        count = favoriteTracks.size,
                        colorHex = "#FF1744", // Red vibrant
                        icon = Icons.Filled.Favorite,
                        onClick = {
                            if (favoriteTracks.isNotEmpty()) {
                                viewModel.playTrackNow(favoriteTracks.first(), favoriteTracks)
                            }
                        }
                    )
                }

                item {
                    Text(
                        text = "Mis Listas de Reproducción",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                if (playlists.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No has creado ninguna lista.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                } else {
                    items(playlists) { playlist ->
                        PlaylistLibraryRow(
                            playlist = playlist,
                            onPlaylistClick = { viewModel.selectPlaylist(playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

// === CONFIGURACIÓN (SETTINGS) SCREEN ===
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    paddingValues: PaddingValues
) {
    val currentThemeOption = viewModel.themeColorOption.collectAsStateWithLifecycle().value
    val musicFolders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    
    // Filters States
    val minDurationMs by viewModel.filterMinDurationMs.collectAsStateWithLifecycle()
    val maxDurationMs by viewModel.filterMaxDurationMs.collectAsStateWithLifecycle()
    val filterFolderPath by viewModel.filterFolderPath.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    
    var showFolderExplorer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Permission check
    var hasStoragePermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    android.Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
    ) {
        Text(
            text = "Configuración",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 1. AJUSTES DE ASPECTO =================
            item {
                Text(
                    text = "Ajustes de Aspecto",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_appearance_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Énfasis de Color",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Personaliza el color de realce de la interfaz",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // Theme Selection Row with individual 48.dp hit areas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThemeColorOption.values().forEach { option ->
                                val isSelected = option == currentThemeOption
                                val circleColor = if (isSystemInDarkTheme()) option.getDarkPrimary() else option.getLightPrimary()
                                val onCircleColor = if (isSystemInDarkTheme()) option.getDarkOnPrimary() else option.getLightOnPrimary()

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { viewModel.setThemeColorOption(option) }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(circleColor)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Seleccionado",
                                                tint = onCircleColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Color de realce activo: " + currentThemeOption.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // ================= 2. AUDIO & FOLDERS TRACK FILTERS =================
            item {
                Text(
                    text = "Filtro de Canciones por Duración y Carpetas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("song_filter_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Filtrar Canciones",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Filtra canciones visibles por longitud y su ruta local",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            if (minDurationMs > 0L || maxDurationMs < 600000L || filterFolderPath != null) {
                                TextButton(
                                    onClick = { viewModel.resetFilters() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.ClearAll, contentDescription = "Limpiar filtros", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Limpiar", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Duración Mínima Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Duración Mínima",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = formatMs(minDurationMs),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Slider(
                                value = minDurationMs.toFloat(),
                                onValueChange = { viewModel.setMinDurationFilter(it.toLong()) },
                                valueRange = 0f..400000f, // Up to 6:40 minutes
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("duration_min_slider")
                            )
                        }

                        // Duración Máxima Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Duración Máxima",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = if (maxDurationMs >= 600000L) "Sin límite (10+ min)" else formatMs(maxDurationMs),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Slider(
                                value = maxDurationMs.toFloat(),
                                onValueChange = { viewModel.setMaxDurationFilter(it.toLong()) },
                                valueRange = 30000f..600000f, // 30s to 10m
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("duration_max_slider")
                            )
                        }

                        // Select Folder Filter Row
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Filtrar por Carpeta Registrada:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // All folders chip
                                val isAllSelected = filterFolderPath == null
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { viewModel.setFolderPathFilter(null) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Todas",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                
                                if (musicFolders.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(musicFolders.toList()) { folder ->
                                            val isSelected = filterFolderPath == folder
                                            val folderName = folder.substringAfterLast("/")
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { viewModel.setFolderPathFilter(folder) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Folder,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = folderName,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (musicFolders.isEmpty() && filterFolderPath != null) {
                                Text(
                                    text = "No has registrado ninguna carpeta todavía.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                                )
                            } else if (filterFolderPath != null) {
                                Text(
                                    text = "Ruta actual: $filterFolderPath",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // Filtered tracks results summary & play control
                        if (filteredTracks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${filteredTracks.size} canciones encontradas",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                Button(
                                    onClick = { viewModel.playTrackNow(filteredTracks.first(), filteredTracks) },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir filtrados", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reproducir", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ninguna canción coincide con los filtros especificados.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }
            }

            // Real-time matching sub-list representation
            if (filteredTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Canciones filtradas (${filteredTracks.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                items(filteredTracks) { track ->
                    val isCurrent = viewModel.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                    val accentColor = remember(track.accentColorHex) { Color(android.graphics.Color.parseColor(track.accentColorHex)) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playTrackNow(track, filteredTracks) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (track.category == "Local") Icons.Filled.Folder else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = accentColor
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.category}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatMs(track.durationMs),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }

            // ================= 3. ESCÁNER DE ALMACENAMIENTO =================
            item {
                Text(
                    text = "Escáner de Almacenamiento",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("storage_scanner_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Gestión de Música Local",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Escanea carpetas del almacenamiento en busca de archivos .mp3",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        if (!hasStoragePermission) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Permiso de Almacenamiento ausente",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Algunas carpetas de música podrían no ser listadas sin permiso.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(
                                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                    android.Manifest.permission.READ_MEDIA_AUDIO
                                                } else {
                                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                                }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Activar", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // ALWAYS VISIBLE: List of Music Folders
                        Text(
                            text = "Carpetas de Música Registradas:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        
                        if (musicFolders.isEmpty()) {
                            Text(
                                text = "No hay carpetas añadidas aún.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.padding(start = 4.dp)
                             )
                        } else {
                            musicFolders.forEach { path ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                         .clip(RoundedCornerShape(8.dp))
                                         .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                         .padding(horizontal = 8.dp, vertical = 6.dp),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Row(
                                         modifier = Modifier.weight(1f),
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                     ) {
                                         Icon(
                                             imageVector = Icons.Filled.FolderOpen,
                                             contentDescription = null,
                                             tint = MaterialTheme.colorScheme.secondary,
                                             modifier = Modifier.size(16.dp)
                                         )
                                         Text(
                                             text = path,
                                             style = MaterialTheme.typography.bodySmall,
                                             maxLines = 1,
                                             overflow = TextOverflow.Ellipsis
                                         )
                                     }
                                     IconButton(
                                         onClick = {
                                             if (filterFolderPath == path) {
                                                 viewModel.setFolderPathFilter(null)
                                             }
                                             viewModel.removeMusicFolder(path)
                                         },
                                         modifier = Modifier.size(24.dp)
                                     ) {
                                         Icon(
                                             imageVector = Icons.Filled.Close,
                                             contentDescription = "Eliminar carpeta",
                                             tint = MaterialTheme.colorScheme.error,
                                             modifier = Modifier.size(16.dp)
                                         )
                                     }
                                 }
                             }
                         }

                         Row(
                             modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             OutlinedButton(
                                 onClick = { showFolderExplorer = true },
                                 modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                 shape = RoundedCornerShape(12.dp)
                             ) {
                                 Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                 Spacer(modifier = Modifier.width(6.dp))
                                 Text("Añadir Carpeta", maxLines = 1, overflow = TextOverflow.Ellipsis)
                             }

                             Button(
                                 onClick = { viewModel.scanFolders() },
                                 modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                 shape = RoundedCornerShape(12.dp),
                                 enabled = !isScanning && musicFolders.isNotEmpty()
                             ) {
                                 if (isScanning) {
                                     CircularProgressIndicator(
                                         modifier = Modifier.size(16.dp),
                                         strokeWidth = 2.dp,
                                         color = MaterialTheme.colorScheme.onPrimary
                                     )
                                 } else {
                                     Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                     Spacer(modifier = Modifier.width(6.dp))
                                     Text("Escanear", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                 }
                             }
                         }

                         // Scan Status Banner representation
                         scanStatus?.let { status ->
                             Row(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .clip(RoundedCornerShape(8.dp))
                                     .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                     .padding(horizontal = 8.dp, vertical = 6.dp),
                                 horizontalArrangement = Arrangement.SpaceBetween,
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Text(
                                     text = status,
                                     style = MaterialTheme.typography.bodySmall.copy(
                                         color = MaterialTheme.colorScheme.onPrimaryContainer,
                                         fontWeight = FontWeight.Medium
                                     ),
                                     modifier = Modifier.weight(1f)
                                 )
                                 IconButton(
                                     onClick = { viewModel.resetScanStatus() },
                                     modifier = Modifier.size(24.dp)
                                 ) {
                                     Icon(
                                         imageVector = Icons.Filled.Close,
                                         contentDescription = "Limpiar estado",
                                         tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                         modifier = Modifier.size(14.dp)
                                     )
                                 }
                             }
                         }
                    }
                }
            }
        }
    }

    if (showFolderExplorer) {
        FolderExplorerDialog(
            onDismiss = { showFolderExplorer = false },
            onFolderSelected = { path ->
                viewModel.addMusicFolder(path)
                showFolderExplorer = false
            },
            viewModel = viewModel
        )
    }
}

// === MINI PLAYER COMPONENT ===
@Composable
fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progress: Long,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    if (track == null) return
    val animatedColor = remember(track.accentColorHex) {
        Color(android.graphics.Color.parseColor(track.accentColorHex))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            .clickable(onClick = onClick)
    ) {
        // Decorative soft ambient bar on target mini player left
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .background(animatedColor)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Simulation (Mini)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(animatedColor.copy(alpha = 0.2f))
                    .drawBehind {
                        drawCircle(color = animatedColor, radius = size.minDimension / 4)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info (Title, Artist)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier.testTag("mini_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Horizontal progress indicator at the very bottom
        val durationRatio = if (track.durationMs > 0) progress.toFloat() / track.durationMs.toFloat() else 0f
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
        ) {
            drawLine(
                color = animatedColor.copy(alpha = 0.2f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = size.height
            )
            drawLine(
                color = animatedColor,
                start = Offset(0f, size.height),
                end = Offset(size.width * durationRatio.coerceIn(0f, 1f), size.height),
                strokeWidth = size.height
            )
        }
    }
}

// === FULL PLAYER COMPONENT SCREEN ===
@Composable
fun ExpandedPlayerScreen(
    track: Track,
    isPlaying: Boolean,
    progress: Long,
    waveAmplitudes: List<Float>,
    isShuffle: Boolean,
    isRepeat: Boolean,
    queue: List<Track>,
    isSearchingLyrics: Boolean,
    lyricsSearchError: String?,
    connectedDeviceName: String = "Altavoz del Teléfono",
    onSearchLyricsOnline: (Long, String, String) -> Unit,
    onUpdateLyrics: (Long, String) -> Unit,
    isAnalyzingMusic: Boolean,
    musicAnalysisError: String?,
    currentTrackAnalysis: com.example.network.MusicAnalysis?,
    onAnalyzeCurrentMusic: () -> Unit,
    onClose: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEqualizerClick: () -> Unit,
    onBluetoothClick: () -> Unit
) {
    var activeSubPage by remember { mutableStateOf(0) } // 0 = Player Controls, 1 = Lyrics, 2 = Queue list, 3 = AI Analysis
    val accentColor = remember(track.accentColorHex) {
        Color(android.graphics.Color.parseColor(track.accentColorHex))
    }

    val baseBackground = MaterialTheme.colorScheme.background
    val opaqueStartColor = remember(accentColor, baseBackground) {
        accentColor.copy(alpha = 0.35f).compositeOver(baseBackground)
    }

    // Dynamic ambient background brushing (Google YTM style blur glow) - completely opaque
    val ambientGradient = Brush.verticalGradient(
        colors = listOf(
            opaqueStartColor,
            baseBackground,
            baseBackground
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {} // Intercept and consume clicks to prevent pass-through to background elements
            )
            .background(ambientGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // App top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Tabs inside dynamic player: REPRODUCTOR, LETRAS, COLA, ANÁLISIS IA
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = "REPRODUCTOR",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (activeSubPage == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeSubPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { activeSubPage = 0 }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Text(
                    text = "LETRAS",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (activeSubPage == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeSubPage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { activeSubPage = 1 }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Text(
                    text = "COLA",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (activeSubPage == 2) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeSubPage == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { activeSubPage = 2 }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Text(
                    text = "ANÁLISIS IA",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (activeSubPage == 3) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeSubPage == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { activeSubPage = 3 }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.Filled.PlaylistAdd,
                    contentDescription = "Guardar en Playlist",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Body switcher based on active view in player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeSubPage) {
                0 -> {
                    // Standard visual player page
                    PlayerControlsPage(
                        track = track,
                        isPlaying = isPlaying,
                        progress = progress,
                        waveAmplitudes = waveAmplitudes,
                        isShuffle = isShuffle,
                        isRepeat = isRepeat,
                        accentColor = accentColor,
                        connectedDeviceName = connectedDeviceName,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onToggleFavorite = onToggleFavorite,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onEqualizerClick = onEqualizerClick,
                        onBluetoothClick = onBluetoothClick
                    )
                }
                1 -> {
                    // Creative scrollable lyrics page with real-time autoscrolling, online search & edit options
                    LyricsPage(
                        track = track,
                        progress = progress,
                        isSearchingLyrics = isSearchingLyrics,
                        lyricsSearchError = lyricsSearchError,
                        accentColor = accentColor,
                        onSearchLyricsOnline = { onSearchLyricsOnline(track.id, track.title, track.artist) },
                        onUpdateLyrics = { onUpdateLyrics(track.id, it) },
                        onSeekToProgress = onSeek
                    )
                }
                2 -> {
                    // Dynamic Queue playlist view
                    QueuePage(
                        queue = queue,
                        currentTrackId = track.id,
                        accentColor = accentColor
                    )
                }
                3 -> {
                    // Beautiful AI-powered analytical page
                    MusicAnalysisPage(
                        track = track,
                        isAnalyzing = isAnalyzingMusic,
                        analysisError = musicAnalysisError,
                        analysis = currentTrackAnalysis,
                        onAnalyze = onAnalyzeCurrentMusic,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerControlsPage(
    track: Track,
    isPlaying: Boolean,
    progress: Long,
    waveAmplitudes: List<Float>,
    isShuffle: Boolean,
    isRepeat: Boolean,
    accentColor: Color,
    connectedDeviceName: String = "Altavoz del Teléfono",
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onEqualizerClick: () -> Unit,
    onBluetoothClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Professional Polish: Aspect square cover arts box with 40dp rounded corners
        Box(
            modifier = Modifier
                .size(255.dp)
                .shadow(12.dp, RoundedCornerShape(40.dp), ambientColor = accentColor)
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.25f),
                            accentColor.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (track.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = "Portada de la canción",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                // Glossy gradient visual overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                )
                            )
                        )
                )
            } else {
                // Subtle rotating inner vinyl detail in background for a real native music feeling
                val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(15000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Rotation"
                )

                val finalRotation = if (isPlaying) rotationAngle else 0f

                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .graphicsLayer { rotationZ = finalRotation }
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.9f))
                        .drawBehind {
                            // Rich vinyl record micro grooves with gradient feel
                            val radiusStep = size.minDimension / 12
                            for (i in 1..5) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.08f),
                                    radius = radiusStep * i,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer vinyl rim shine
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawArc(
                                    color = Color.White.copy(alpha = 0.06f),
                                    startAngle = -45f,
                                    sweepAngle = 90f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                                )
                                drawArc(
                                    color = Color.White.copy(alpha = 0.06f),
                                    startAngle = 135f,
                                    sweepAngle = 90f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                                )
                            }
                    )
                    // Vinyl center core label with song title initials or beautiful note icon!
                    val initials = if (track.title.length >= 2) {
                        track.title.take(2).uppercase()
                    } else if (track.title.isNotEmpty()) {
                        track.title.take(1).uppercase()
                    } else {
                        "♫"
                    }
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Ambient Live Soundwave Canvas
        DynamicSoundWaveCanvas(
            waveAmplitudes = waveAmplitudes,
            accentColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        )

        // Title and Subtitle Info Labels
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = track.title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist}  •  ${track.album}",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Sleek Timeline Progress seeker
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progress.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(track.durationMs.toFloat()),
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thumbColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_progress_slider")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(progress),
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = formatMs(track.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        // Action Keys Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Orden aleatorio",
                    tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Professional Polish: Beautiful squricle Rounded[28px] primary Button with Scale micro animation
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .shadow(4.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onPlayPauseToggle)
                    .testTag("player_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Professional Polish: Immersive Cast/Accessory Output device selector footer mimicking pixel-buds layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audio output device badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBluetoothClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CastConnected,
                    contentDescription = "Dispositivo de salida",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = connectedDeviceName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Quick utility shortcut filters
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = "Bucle",
                        tint = if (isRepeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onEqualizerClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = "Ecualizador de audio",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsPage(
    track: Track,
    progress: Long,
    isSearchingLyrics: Boolean,
    lyricsSearchError: String?,
    accentColor: Color,
    onSearchLyricsOnline: () -> Unit,
    onUpdateLyrics: (String) -> Unit,
    onSeekToProgress: (Long) -> Unit
) {
    val lines = remember(track.lyrics) {
        track.lyrics.split("\n").map { it.trim() }
    }
    val lineCount = lines.size
    val duration = track.durationMs

    // Map playback progress to the estimated lyrics line
    val activeIndex = remember(progress, lineCount, duration) {
        if (lineCount <= 1 || duration <= 0L) {
            0
        } else {
            val progressRatio = progress.toDouble() / duration.toDouble()
            (progressRatio * lineCount).toInt().coerceIn(0, lineCount - 1)
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Smoothly scroll to keep active lyric centered
    LaunchedEffect(activeIndex) {
        if (lineCount > 0) {
            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Upper Controls bar (Search online & edit manually)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Letras en tiempo real",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "Toca un verso para saltar a ese momento",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Online button
                IconButton(
                    onClick = onSearchLyricsOnline,
                    enabled = !isSearchingLyrics,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    if (isSearchingLyrics) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = accentColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Buscar letras por internet",
                            tint = accentColor
                        )
                    }
                }

                // Edit button
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar letras",
                        tint = accentColor
                    )
                }
            }
        }

        // Online Search errors
        if (lyricsSearchError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = lyricsSearchError,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Infinite fluid list of live-scrolling aligned lyric items
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp)
        ) {
            itemsIndexed(lines) { index, lineText ->
                val isActive = index == activeIndex
                val displayLine = if (lineText.isBlank()) "•  •  •" else lineText

                Text(
                    text = displayLine,
                    textAlign = TextAlign.Center,
                    style = if (isActive) {
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 25.sp,
                            lineHeight = 34.sp,
                            color = accentColor
                        )
                    } else {
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (duration > 0L && lineCount > 0) {
                                val targetProgress = ((index.toFloat() / lineCount.toFloat()) * duration).toLong()
                                onSeekToProgress(targetProgress)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    // Modal Sheet or Dialog to Edit current tracks lyrics manual
    if (showEditDialog) {
        var editLyricsText by remember { mutableStateOf(track.lyrics) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "Editar Letras",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Modifica la letra de la canción (pulsa Enter para separar versos, esto ayuda al sincronizador en tiempo real):",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    OutlinedTextField(
                        value = editLyricsText,
                        onValueChange = { editLyricsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = { Text("Escribe la letra aquí...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateLyrics(editLyricsText)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Guardar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun QueuePage(
    queue: List<Track>,
    currentTrackId: Long,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Queue,
                contentDescription = null,
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cola de Reproducción",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("La cola está vacía.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(queue) { index, item ->
                    val isCurrent = item.id == currentTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrent) accentColor.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.width(28.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artist,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Reproduciendo",
                                tint = accentColor
                            )
                        } else {
                            Text(
                                text = formatMs(item.durationMs),
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }
            }
        }
    }
}

// === CANVAS COMPONENT FOR DYNAMIC SOUNDWAVE ===
@Composable
fun DynamicSoundWaveCanvas(
    waveAmplitudes: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val totalBars = waveAmplitudes.size
        val gap = 6.dp.toPx()
        val totalWidth = size.width
        val barWidth = (totalWidth - (gap * (totalBars - 1))) / totalBars
        val centerY = size.height / 2

        for (i in 0 until totalBars) {
            val amplitude = waveAmplitudes.getOrElse(i) { 0.15f }
            val barHeight = size.height * amplitude
            val startX = i * (barWidth + gap)

            drawLine(
                color = accentColor,
                start = Offset(startX, centerY - (barHeight / 2)),
                end = Offset(startX, centerY + (barHeight / 2)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// === LIST ITEM COMPONENT FOR SONGS ===
@Composable
fun TrackListItem(
    track: Track,
    currentTrack: Track?,
    isPlaying: Boolean,
    waveAmplitudes: List<Float>,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onManageTrack: () -> Unit
) {
    val isCurrent = currentTrack != null && currentTrack.id == track.id
    val accentColor = remember(track.accentColorHex) {
        Color(android.graphics.Color.parseColor(track.accentColorHex))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) accentColor.copy(alpha = 0.08f)
            else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Simulation
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.45f),
                                accentColor
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (track.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = "Portada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else if (isCurrent && isPlaying) {
                    // Small inline sound visualizer instead of static play icon
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        for (i in 0 until 4) {
                            val ampState = waveAmplitudes.getOrElse(i) { 0.3f }
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight(ampState.coerceIn(0.2f, 0.9f))
                                    .background(Color.White)
                            )
                        }
                    }
                } else {
                    val initials = if (track.title.isNotEmpty()) track.title.take(1).uppercase() else "♫"
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Metadata Detail
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist}  •  ${track.category}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Actions
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    Icons.Filled.PlaylistAdd,
                    contentDescription = "Añadir a Playlist",
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onManageTrack) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Gestionar Canción",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// === HORIZONTAL RECENT TRACK ITEM ===
@Composable
fun RecentTrackItem(
    track: Track,
    onClick: () -> Unit
) {
    val accentColor = remember(track.accentColorHex) {
        Color(android.graphics.Color.parseColor(track.accentColorHex))
    }

    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.2f))
                .drawBehind {
                    drawCircle(color = accentColor, radius = size.minDimension / 4)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// === PLAYLIST SELECTION ROW ===
@Composable
fun PlaylistLibraryRow(
    playlist: Playlist,
    onPlaylistClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlaylistClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated album artwork grid as custom dynamic icon holder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.description.isNotEmpty()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Abrir playlist",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// === QUICK CATEGORY ITEM FOR LIBRARY ===
@Composable
fun LibraryCategoryItem(
    title: String,
    count: Int,
    colorHex: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val color = remember(colorHex) { Color(android.graphics.Color.parseColor(colorHex)) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "$count canciones",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Reproducir todo",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// === PLAYLIST DETAIL PAGE SCREEN ===
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    waveAmplitudes: List<Float>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onRemoveTrack: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App top detail bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
            }
            IconButton(onClick = onDeletePlaylist) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar Playlist", tint = Color.Red)
            }
        }

        // Playlist header details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = playlist.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            if (playlist.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${tracks.size} canciones",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPlayAll,
                modifier = Modifier.fillMaxWidth(0.6f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("REPRODUCIR TODO")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tracks inside the playlist list
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Esta playlist no contiene canciones aún.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks) { item ->
                    val isCurrent = currentTrack != null && currentTrack.id == item.id
                    val itemAccent = remember(item.accentColorHex) { Color(android.graphics.Color.parseColor(item.accentColorHex)) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) itemAccent.copy(alpha = 0.08f)
                            else Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(itemAccent.copy(alpha = 0.15f))
                                    .drawBehind { drawCircle(itemAccent, radius = size.minDimension / 4) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrent && isPlaying) {
                                    // Animated equalizer
                                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = itemAccent)
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = itemAccent)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) itemAccent else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { onRemoveTrack(item.id) }) {
                                Icon(
                                    Icons.Filled.RemoveCircleOutline,
                                    contentDescription = "Quitar",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// === DIALOGS ===

@Composable
fun PlaylistSelectionDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Añadir a playlist",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no tienes listas.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPlaylistSelected(playlist.id) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                TextButton(
                    onClick = {
                        onDismiss()
                        onCreateNewPlaylist()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Crear nueva playlist")
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nueva playlist",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (name.isNotBlank()) errorMsg = ""
                    },
                    label = { Text("Nombre de la lista") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMsg.isNotEmpty()
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMsg = "Debes ingresar un nombre válido"
                            } else {
                                onSubmit(name, description)
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Crear")
                    }
                }
            }
        }
    }
}

@Composable
fun FolderExplorerDialog(
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit,
    viewModel: MusicViewModel
) {
    var currentDir by remember {
        mutableStateOf(
            java.io.File(
                android.os.Environment.getExternalStorageDirectory().absolutePath
            )
        )
    }

    val filesAndFolders = remember(currentDir) {
        try {
            currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header of Folder Explorer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Explorar Carpetas",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick storage jump location row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rootInternal = android.os.Environment.getExternalStorageDirectory()
                    val rootStorage = java.io.File("/storage")
                    
                    val isInternalSelected = currentDir.absolutePath == rootInternal.absolutePath
                    val isSDSelected = currentDir.absolutePath == "/storage" || (currentDir.absolutePath.startsWith("/storage/") && !currentDir.absolutePath.startsWith("/storage/emulated"))

                    Button(
                        onClick = { currentDir = rootInternal },
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isInternalSelected) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        } else {
                            ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isInternalSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Alm. Interno",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isInternalSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { currentDir = if (rootStorage.exists()) rootStorage else rootInternal },
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isSDSelected) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        } else {
                            ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (isSDSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tarjeta SD / Raíz",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isSDSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Current Route Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Ruta actual",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = currentDir.absolutePath,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action panel inside explorer for empty folders & demo tracks!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigate up button if a parent directory exists
                    val parent = currentDir.parentFile
                    if (parent != null && currentDir.absolutePath != "/") {
                        TextButton(
                            onClick = { currentDir = parent },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Subir de nivel", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Subir nivel", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Quick Creator for Demo audio files of testing in folder!
                    TextButton(
                        onClick = {
                            viewModel.createDemoTrackInFolder(
                                folderPath = currentDir.absolutePath,
                                title = "Local Beats " + (1..100).random(),
                                artist = "Dispositivo"
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Crear demo MP3", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crear demo MP3 aquí", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable File / Folder structure
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                ) {
                    if (filesAndFolders.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Carpeta vacía o sin permisos de lectura.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filesAndFolders) { file ->
                                val isDirectory = file.isDirectory
                                val isAudioFile = !isDirectory && (file.name.lowercase().endsWith(".mp3") ||
                                        file.name.lowercase().endsWith(".wav") ||
                                        file.name.lowercase().endsWith(".m4a") ||
                                        file.name.lowercase().endsWith(".flac") ||
                                        file.name.lowercase().endsWith(".ogg"))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isDirectory) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.04f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            if (isDirectory) {
                                                currentDir = file
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isDirectory -> Icons.Filled.Folder
                                            isAudioFile -> Icons.Filled.MusicNote
                                            else -> Icons.Filled.Info
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            isDirectory -> MaterialTheme.colorScheme.primary
                                            isAudioFile -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isDirectory) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!isDirectory) {
                                            Text(
                                                text = "${(file.length() / 1024)} KB",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom confirmation buttons block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = { onFolderSelected(currentDir.absolutePath) },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Elegir Carpeta")
                    }
                }
            }
        }
    }
}

// === UTILS ===
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun MusicAnalysisPage(
    track: Track,
    isAnalyzing: Boolean,
    analysisError: String?,
    analysis: com.example.network.MusicAnalysis?,
    onAnalyze: () -> Unit,
    accentColor: Color
) {
    val parsedColor = remember(analysis?.colorSuggestion) {
        if (analysis != null) {
            try {
                Color(android.graphics.Color.parseColor(analysis.colorSuggestion))
            } catch (e: Exception) {
                accentColor
            }
        } else {
            accentColor
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("music_analysis_page")
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        if (isAnalyzing) {
            item {
                MusicAnalysisLoadingView(accentColor = accentColor)
            }
        } else if (analysis != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = parsedColor.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = parsedColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Análisis IA Completado",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = parsedColor
                                )
                            )
                        }
                        Text(
                            text = "Gemini ha analizado esta pista de audio y ha organizado los metadatos según su significado, vibra e influencias musicales.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                AnalysisCard(
                    title = "Género y Estilo Musical",
                    content = analysis.genre,
                    icon = Icons.Filled.MusicNote,
                    iconColor = parsedColor
                )
            }

            item {
                AnalysisCard(
                    title = "Estado de Ánimo y Vibra",
                    content = analysis.mood,
                    icon = Icons.Filled.EmojiEmotions,
                    iconColor = parsedColor
                )
            }

            item {
                AnalysisCard(
                    title = "Significado de la Letra",
                    content = analysis.lyricsMeaning,
                    icon = Icons.Filled.Description,
                    iconColor = parsedColor
                )
            }

            item {
                AnalysisCard(
                    title = "Datos Curiosos e Historia",
                    content = analysis.funFacts,
                    icon = Icons.Filled.Lightbulb,
                    iconColor = parsedColor
                )
            }

            item {
                AnalysisCard(
                    title = "Actividad Recomendada",
                    content = analysis.recommendedActivity,
                    icon = Icons.Filled.DirectionsRun,
                    iconColor = parsedColor
                )
            }
        } else {
            if (analysisError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = analysisError,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "¿Deseas que la IA analice esta canción?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Permite que la Inteligencia Artificial analice la letra y composición de la pista. Se procesará:\n" +
                                "• El significado profundo de la letra\n" +
                                "• Sus géneros musicales reales e influencias de producción\n" +
                                "• El estado de ánimo predominante y su energía\n" +
                                "• Datos curiosos, historia del álbum y detalles del artista\n" +
                                "• Actividades recomendadas ideales para acompañar su escucha",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Button(
                        onClick = onAnalyze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Iniciar Análisis Inteligente", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun AnalysisCard(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun MusicAnalysisLoadingView(accentColor: Color) {
    var phraseIndex by remember { mutableStateOf(0) }
    val phrases = listOf(
        "Gemini está analizando la letra de la canción...",
        "Decodificando el género musical y estilo...",
        "Sintonizando el estado de ánimo emocional...",
        "Buscando anécdotas y datos curiosos del artista...",
        "Calculando el color de vibra y actividades recomendadas..."
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            phraseIndex = (phraseIndex + 1) % phrases.size
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = accentColor)
        Text(
            text = phrases[phraseIndex],
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackManagementDialog(
    track: Track,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editTitle by remember { mutableStateOf(track.title) }
    var editArtist by remember { mutableStateOf(track.artist) }
    var editAlbum by remember { mutableStateOf(track.album) }
    var editCategory by remember { mutableStateOf(track.category) }
    var editCoverUrl by remember { mutableStateOf(track.coverUrl) }
    var isFavorite by remember { mutableStateOf(track.isFavorite) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val cacheDir = context.cacheDir
                    val file = java.io.File(cacheDir, "custom_cover_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    editCoverUrl = "file://" + file.absolutePath
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackManagementDialog", "Error loading custom cover", e)
            }
        }
    }

    // AI research state
    val aiState by viewModel.aiRetrievalState.collectAsStateWithLifecycle()
    
    // Trimming options
    val totalDurationMs = track.durationMs
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(totalDurationMs) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var permissionDeniedAlert by remember { mutableStateOf(false) }
    var trimResultPath by remember { mutableStateOf<String?>(null) }
    var actionStatusMessage by remember { mutableStateOf<String?>(null) }

    val formatTime = remember {
        { ms: Long ->
            val secs = ms / 1000
            val m = secs / 60
            val s = secs % 60
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    LaunchedEffect(track) {
        viewModel.resetAiMetadataState()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Immersive Toolbar
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Gestor de Canción",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val updated = track.copy(
                                    title = editTitle,
                                    artist = editArtist,
                                    album = editAlbum,
                                    category = editCategory,
                                    coverUrl = editCoverUrl,
                                    isFavorite = isFavorite
                                )
                                viewModel.updateTrack(updated)
                                onDismiss()
                            }
                        ) {
                            Text(
                                "GUARDAR",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Quick Metadata Header card with cover preview
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (editCoverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = editCoverUrl,
                                        contentDescription = "Vista previa portada",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PhotoCamera,
                                            contentDescription = "Cambiar",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PhotoCamera,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Elegir",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = editTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = editArtist,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Favorita: ",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                                    )
                                    IconButton(
                                        onClick = { isFavorite = !isFavorite },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Favorito",
                                            tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // System / Action Notifications
                    if (actionStatusMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = actionStatusMessage!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { actionStatusMessage = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Metadata Fields
                    Text(
                        "Información de la pista",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Título") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = editArtist,
                            onValueChange = { editArtist = it },
                            label = { Text("Artista") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = editAlbum,
                            onValueChange = { editAlbum = it },
                            label = { Text("Álbum") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = { editCategory = it },
                            label = { Text("Género / Categoría") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = editCoverUrl,
                            onValueChange = { editCoverUrl = it },
                            label = { Text("URL de la Portada") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // GEMINI AI INVESTIGATION BLOCK
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Automación Inteligente",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "PRO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }

                            Text(
                                "Investiga de manera automática con la IA de Google Gemini para rellenar el título exacto, el artista oficial, álbum, género real y una portada adecuada.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )

                            Button(
                                onClick = {
                                    viewModel.researchMetadataWithAi(editTitle, editArtist)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Investigar con Gemini")
                            }

                            // AI States Responses
                            when (val state = aiState) {
                                is AiRetrievalState.Loading -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        Text("Revisando base de datos musical con Gemini...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                is AiRetrievalState.Error -> {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                is AiRetrievalState.Success -> {
                                    val meta = state.metadata
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                "Sugerencia encontrada por la IA:",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                if (meta.coverUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = meta.coverUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(64.dp)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                }
                                                Column {
                                                    Text("• Título: ${meta.title}", style = MaterialTheme.typography.bodyMedium)
                                                    Text("• Artista: ${meta.artist}", style = MaterialTheme.typography.bodyMedium)
                                                    Text("• Álbum: ${meta.album}", style = MaterialTheme.typography.bodyMedium)
                                                    Text("• Género: ${meta.genre}", style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    editTitle = meta.title
                                                    editArtist = meta.artist
                                                    editAlbum = meta.album
                                                    editCategory = meta.genre
                                                    if (meta.coverUrl.isNotBlank()) {
                                                        editCoverUrl = meta.coverUrl
                                                    }
                                                    viewModel.resetAiMetadataState()
                                                    actionStatusMessage = "¡Datos sugeridos cargados temporalmente! Clic en GUARDAR arriba para guardarlos de forma definitiva."
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Aplicar Cambios de IA")
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }

                    // TRIMMING AUDIO SECTION
                    Text(
                        "Recortar Audio (Tono de llamada)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "Elige el segmento de audio que quieres aislar para usar como tono de llamada.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )

                            // Start Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Inicio del recorte:", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatTime(startMs),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                Slider(
                                    value = startMs.toFloat(),
                                    onValueChange = { 
                                        startMs = it.toLong().coerceIn(0L, endMs - 1000L)
                                    },
                                    valueRange = 0f..totalDurationMs.toFloat()
                                )
                            }

                            // End Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fin del recorte:", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatTime(endMs),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                Slider(
                                    value = endMs.toFloat(),
                                    onValueChange = { 
                                        endMs = it.toLong().coerceIn(startMs + 1000L, totalDurationMs)
                                    },
                                    valueRange = 0f..totalDurationMs.toFloat()
                                )
                            }

                            // Result details
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Duración del recorte: ${formatTime(endMs - startMs)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.trimTrack(track, startMs, endMs) { path ->
                                        if (path != null) {
                                            trimResultPath = path
                                            actionStatusMessage = "¡Audio recortado con éxito! Se insertó una nueva pista '${track.title} (Recortado)' en la biblioteca."
                                        } else {
                                            actionStatusMessage = "Error al intentar recortar el audio de la pista."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.ContentCut, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Recortar y Guardar en Biblioteca")
                            }
                        }
                    }

                    // DEVICE RINGTONE SETUP
                    Text(
                        "Tono del Dispositivo",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Button(
                        onClick = {
                            viewModel.setAsRingtone(track, context) { success, msg ->
                                if (success) {
                                    actionStatusMessage = msg
                                } else if (msg == "PERMISSION_REQUIRED") {
                                    permissionDeniedAlert = true
                                } else {
                                    actionStatusMessage = msg
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Establecer como Tono de Llamada")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // DELETION BLOCK
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Text(
                        "Acciones peligrosas",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    )

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Borrar Canción de la Biblioteca")
                    }
                }
            }
        }
    }

    // Deletion confirmation window
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar Canción") },
            text = { Text("¿Estás absolutamente seguro de que prefieres eliminar '${track.title}' de tu biblioteca? El archivo físico intacto se preservará pero dejará de listarse aquí.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteTrack(track.id)
                        onDismiss()
                    }
                ) {
                    Text("ELIMINAR", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }

    // Permission denied notification system dialog
    if (permissionDeniedAlert) {
        AlertDialog(
            onDismissRequest = { permissionDeniedAlert = false },
            title = { Text("Permiso Requerido") },
            text = { Text("Para poder configurar tonos de llamada directamente, es indispensable otorgar permiso del sistema de escritura de configuración de Android.") },
            confirmButton = {
                Button(
                    onClick = {
                        permissionDeniedAlert = false
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            actionStatusMessage = "No se pudo abrir automáticamente. Por favor abre Ajustes y habilita 'Modificar ajustes del sistema'."
                        }
                    }
                ) {
                    Text("Autorizar Permiso")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDeniedAlert = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun EqualizerDialog(
    track: Track,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val bands by viewModel.equalizerBands.collectAsStateWithLifecycle()
    
    LaunchedEffect(track.id) {
        viewModel.refreshEqualizerBandsForTrack(track.id)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ecualizador de Audio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    bands.forEach { band ->
                        val currentDb = band.currentLevelMb / 100
                        val minDb = band.minLevelMb / 100
                        val maxDb = band.maxLevelMb / 100

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val freqText = if (band.centerFreqHz >= 1000) {
                                    "${band.centerFreqHz / 1000} kHz"
                                } else {
                                    "${band.centerFreqHz} Hz"
                                }
                                Text(
                                    text = freqText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${if (currentDb > 0) "+" else ""}$currentDb dB",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (currentDb != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            
                            Slider(
                                value = band.currentLevelMb.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateBandLevel(track.id, band.bandIndex, newValue.toInt())
                                },
                                valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    thumbColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "Plano" to listOf(0, 0, 0, 0, 0),
                        "Bajos" to listOf(600, 300, 0, 0, -200),
                        "Voz" to listOf(-200, 0, 400, 500, 100)
                    )
                    presets.forEach { (name, values) ->
                        Button(
                            onClick = {
                                values.forEachIndexed { idx, value ->
                                    if (idx < bands.size) {
                                        viewModel.updateBandLevel(track.id, bands[idx].bandIndex, value)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                }

                Text(
                    text = "Ajustes guardados automáticamente para esta canción.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun BluetoothDeviceSelectorDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val devices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.bluetoothConnectedDeviceName.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshBluetoothDevices()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dispositivo de Salida",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Selecciona dónde reproducir la música",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Dispositivo Activo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = connectedDeviceName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text(
                    text = "Dispositivos Vinculados:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isSpeakerActive = connectedDeviceName == "Altavoz del Teléfono"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSpeakerActive) MaterialTheme.colorScheme.secondaryContainer 
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.selectBluetoothDevice("Altavoz del Teléfono")
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (isSpeakerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Altavoz del Teléfono",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSpeakerActive) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSpeakerActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSpeakerActive) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Activo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    devices.forEach { device ->
                        val isCurrent = connectedDeviceName == device.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.secondaryContainer 
                                    else Color.Transparent
                                )
                                .clickable {
                                    viewModel.selectBluetoothDevice(device.name)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (device.isBonded) {
                                    Text(
                                        text = "Guardado • ${device.address}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Activo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("MusicPlayerScreen", "Failed to open bluetooth settings", e)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Vincular Nuevo")
                    }

                    Button(
                        onClick = {
                            viewModel.refreshBluetoothDevices()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Actualizar")
                    }
                }
            }
        }
    }
}

