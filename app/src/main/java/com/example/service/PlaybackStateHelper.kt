package com.example.service

import com.example.data.Track
import kotlinx.coroutines.flow.MutableStateFlow

object PlaybackStateHelper {
    val currentTrack = MutableStateFlow<Track?>(null)
    val isPlaying = MutableStateFlow(false)

    // Callbacks to invoke standard playback simulation methods in MusicViewModel
    var onPlayPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPrevAction: (() -> Unit)? = null
}
