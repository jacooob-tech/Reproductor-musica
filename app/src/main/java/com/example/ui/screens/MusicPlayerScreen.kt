package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Playlist
import com.example.data.Track
import com.example.ui.theme.ThemeColorOption
import com.example.viewmodel.AiRetrievalState
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.TrackSortOption
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicViewModel,
    activeTheme: ThemeColorOption,
    onThemeChanged: (ThemeColorOption) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State bindings
    val tracks by viewModel.displayedTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistList.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val playerError by viewModel.playerError.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.homeSortOption.collectAsStateWithLifecycle()

    // Gemini network states
    val aiOrganizationLoading by viewModel.aiSortingLoading.collectAsStateWithLifecycle()
    val aiOrganizationError by viewModel.aiSortingError.collectAsStateWithLifecycle()

    // Screen dialog / overlay states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var showPlayerOverlay by remember { mutableStateOf(false) }
    var aiSortQuery by remember { mutableStateOf("") }

    // Floating notification error handling
    LaunchedEffect(playerError) {
        playerError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    // Storage permission launcher based on SDK level
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanDeviceMusic(context)
            Toast.makeText(context, "Escaneando música local...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Permiso denegado. No se puede escanear la biblioteca de música.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Mini embedded player bar
            currentTrack?.let { track ->
                MiniPlayerBar(
                    track = track,
                    isPlaying = isPlaying,
                    position = playbackPosition,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onClick = { showPlayerOverlay = true }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Reproductor AI",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Material 3 • Estilo elegante",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // Scan Folder Button
                IconButton(
                    onClick = {
                        permissionLauncher.launch(storagePermission)
                    },
                    modifier = Modifier
                        .testTag("scan_music_button")
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Escanear Música local",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Theme Selection Row
            Column {
                Text(
                    text = "Ajustar paleta de color:",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeColorOption.values().forEach { option ->
                        val isSelected = activeTheme == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { onThemeChanged(option) },
                            label = { Text(option.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Gemini Warning indicator (informational)
            if (!com.example.network.GeminiApiClient.isApiKeyConfigured()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF5C1E1E),
                        contentColor = Color(0xFFFFEAEA)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Llave faltante")
                        Text(
                            text = "API Key de Gemini no configurada. Las funciones de Inteligencia Artificial están inactivas. Configúrala en la pestaña de 'Secrets' 🔑 en AI Studio para activarlas.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // AI Smart Agent Sort
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Organización Inteligente por IA (Gemini)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ordena tu música usando lenguaje natural. Por ejemplo: 'Música relajada para dormir' o 'Canciones para entrenar'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = aiSortQuery,
                        onValueChange = { aiSortQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_sort_input"),
                        placeholder = { Text("Escribe instrucciones de ordenación...") },
                        trailingIcon = {
                            if (aiOrganizationLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        if (aiSortQuery.isNotBlank()) {
                                            viewModel.reorganizeTracksWithAi(aiSortQuery)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Ordenar con IA")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    if (!aiOrganizationError.isNullOrBlank()) {
                        Text(
                            text = aiOrganizationError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (sortOption == TrackSortOption.AI_DESC) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ordenado de forma inteligente por IA ✨",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { viewModel.clearAiSorting() }) {
                                Text("Restablecer", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Search Filter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_field"),
                    placeholder = { Text("Buscar canción, artista o álbum...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            // Active Folder/Playlists Panel
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Listas de Reproducción",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.testTag("new_playlist_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nueva Lista")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Todas" chip
                    FilterChip(
                        selected = selectedPlaylistId == null,
                        onClick = { viewModel.selectPlaylist(null) },
                        label = { Text("Todas") }
                    )

                    playlists.forEach { playlist ->
                        val isSelected = selectedPlaylistId == playlist.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPlaylist(playlist.id) },
                            label = { Text(playlist.name) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.deletePlaylist(playlist.id) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Borrar",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Track list title
            Text(
                text = if (selectedPlaylistId != null) "Canciones en esta Lista" else "Biblioteca de Canciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Tracks list representation (Inline Column)
            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay canciones disponibles.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Usa el icono 🔄 superior para escanear archivos del teléfono.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                tracks.forEach { track ->
                    val isActive = currentTrack?.id == track.id
                    TrackItemRow(
                        track = track,
                        isActive = isActive,
                        onPlayClick = { viewModel.playTrack(track) },
                        onDeleteClick = { viewModel.deleteTrack(track.id) },
                        onAddToPlaylist = { showAddToPlaylistDialog = track }
                    )
                }
            }
        }
    }

    // Modal Create Playlist
    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Crear Lista de Reproducción",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth().testTag("playlist_name_field")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Descripción (Opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.createPlaylist(name, desc)
                                    showCreatePlaylistDialog = false
                                }
                            },
                            modifier = Modifier.testTag("save_playlist_button")
                        ) {
                            Text("Crear")
                        }
                    }
                }
            }
        }
    }

    // Modal Add To Playlist
    showAddToPlaylistDialog?.let { track ->
        Dialog(onDismissRequest = { showAddToPlaylistDialog = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Añadir a lista de reproducción",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Selecciona dónde quieres añadir la canción: '${track.title}'",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addTrackToPlaylist(playlist.id, track.id)
                                        showAddToPlaylistDialog = null
                                        Toast.makeText(context, "Canción agregada!", Toast.LENGTH_SHORT).show()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(playlist.name, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddToPlaylistDialog = null }) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }

    // Expanded immersive full-screen overlay for active song
    if (showPlayerOverlay) {
        currentTrack?.let { track ->
            PlayerImmersiveScreen(
                track = track,
                isPlaying = isPlaying,
                position = playbackPosition,
                viewModel = viewModel,
                onDismiss = { showPlayerOverlay = false }
            )
        }
    }
}

// --- SUB LEVEL COMPOSABLES ---

@Composable
fun TrackItemRow(
    track: Track,
    isActive: Boolean,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("track_row_${track.id}")
            .clickable { onPlayClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left custom cover simulation
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (track.isDemo) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Metadata text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = track.artist,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${track.album} • ${track.category}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = formatDuration(track.duration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Folder association actions
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Default.Favorite, contentDescription = "Añadir a lista")
            }

            // Delete action
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar de biblioteca", tint = Color(0xFFE57373))
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    position: Long,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("mini_player_bar"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            // Linear progress slider track
            val progressFactor = if (track.duration > 0) position.toFloat() / track.duration else 0f
            LinearProgressIndicator(
                progress = progressFactor,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} (Clic para pantalla completa)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.testTag("mini_play_pause")
                ) {
                    if (isPlaying) {
                        PauseIcon(modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- IMMERSIVE PLAYER MODAL ---

@Composable
fun PlayerImmersiveScreen(
    track: Track,
    isPlaying: Boolean,
    position: Long,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Detalles, 1: Letra, 2: Análisis

    // Gemini states bindings
    val aiState by viewModel.aiRetrievalState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyricsState.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val analysis by viewModel.musicAnalysisState.collectAsStateWithLifecycle()
    val analysisLoading by viewModel.musicAnalysisLoading.collectAsStateWithLifecycle()
    val analysisError by viewModel.musicAnalysisError.collectAsStateWithLifecycle()

    // Load initial states
    LaunchedEffect(track.id) {
        viewModel.loadLocalLyrics(track)
        viewModel.loadLocalAnalysis(track)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("immersive_player_dialog"),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header back
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_overlay_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                    Text(
                        "Reproduciendo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Box(modifier = Modifier.width(48.dp))
                }

                // Waveform pulsating visualizer simulation
                val infiniteTransition = rememberInfiniteTransition()
                val animatedScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isPlaying) 1.2f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(animatedScale)
                        .drawBehind {
                            // Pulsating dynamic ripple rings
                            val strokeWidth = 3f
                            drawCircle(
                                color = if (isPlaying) Color(0xFF00ADB5).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f),
                                radius = size.minDimension / 2f + (animatedScale * 30),
                                style = Stroke(width = strokeWidth)
                            )
                            drawCircle(
                                color = if (isPlaying) Color(0xFF00ADB5).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
                                radius = size.minDimension / 2f + (animatedScale * 60),
                                style = Stroke(width = strokeWidth)
                            )
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Meta Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }

                // Slider Timeline
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = position.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..track.duration.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timeline_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(position))
                        Text(formatDuration(track.duration))
                    }
                }

                // Dynamic Controllers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.previousTrack() },
                        modifier = Modifier.size(48.dp).testTag("prev_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Anterior", modifier = Modifier.scale(-1f).size(36.dp))
                    }

                    Button(
                        onClick = { viewModel.togglePlayPause() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(64.dp).testTag("play_pause_button")
                    ) {
                        if (isPlaying) {
                            PauseIcon(modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Reproducir",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.nextTrack() },
                        modifier = Modifier.size(48.dp).testTag("next_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
                    }
                }

                // Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Detalles", "Letra IA", "Análisis IA").forEachIndexed { index, label ->
                        val isSelected = selectedTab == index
                        TextButton(
                            onClick = { selectedTab = index }
                        ) {
                            Text(
                                label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Tab Content Wrapper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> { // Detalles & Enrich metadata
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Categoría:", fontWeight = FontWeight.Bold)
                                    Text(track.category)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tipo de Track:", fontWeight = FontWeight.Bold)
                                    Text(if (track.isDemo) "Demo Synthesized" else "Local Library")
                                }
                                track.aiAnalysis?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } ?: Text(
                                    "No hay datos adicionales enriquecidos.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                when (aiState) {
                                    AiRetrievalState.Idle -> {
                                        Button(
                                            onClick = { viewModel.researchTrackMetadata(track) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("Enriquecer metatags con IA")
                                        }
                                    }
                                    AiRetrievalState.Loading -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Text("Consultando con Gemini...")
                                        }
                                    }
                                    is AiRetrievalState.Success -> {
                                        val success = aiState as AiRetrievalState.Success
                                        Text(
                                            "¡Enriquecido con éxito! Título: ${success.title} | Género: ${success.category}",
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                        TextButton(onClick = { viewModel.clearAiRetrievalState() }) {
                                            Text("Entendido")
                                        }
                                    }
                                    is AiRetrievalState.Error -> {
                                        val error = aiState as AiRetrievalState.Error
                                        Text(
                                            error.message,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = { viewModel.researchTrackMetadata(track) }
                                        ) {
                                            Text("Reintentar")
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // Lyrics view
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (lyricsLoading) {
                                    CircularProgressIndicator()
                                    Text("Buscando letra oficial en internet...")
                                } else {
                                    lyrics?.let { lyricsText ->
                                        Text(
                                            text = lyricsText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    } ?: run {
                                        Text(
                                            "Letra no disponible localmente.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Button(
                                            onClick = { viewModel.fetchLyrics(track) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("Buscar letra con AI (Gemini)")
                                        }
                                    }

                                    lyricsError?.let { err ->
                                        Text("Error: $err", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        2 -> { // Analysis view
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (analysisLoading) {
                                    CircularProgressIndicator()
                                    Text("Analizando composición musical...")
                                } else {
                                    analysis?.let { result ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Vibra / Canal:", fontWeight = FontWeight.Bold)
                                            Text(result.mood, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Corrientes / Influencias:", fontWeight = FontWeight.Bold)
                                            Text(result.influences)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Análisis de Composición:", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                                        Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                                    } ?: run {
                                        Text(
                                            "No se ha realizado un análisis de vibra.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Button(
                                            onClick = { viewModel.analyzeVibeAndInfluences(track) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("Analizar vibe e influencias con IA")
                                        }
                                    }

                                    analysisError?.let { err ->
                                        Text("Error: $err", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(tint, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(tint, RoundedCornerShape(1.dp))
            )
        }
    }
}

