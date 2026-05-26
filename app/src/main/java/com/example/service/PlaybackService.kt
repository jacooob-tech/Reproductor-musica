package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class PlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isServiceForeground = false

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "music_playback_channel_v2"
        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        const val ACTION_PLAY_PAUSE = "com.example.service.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.service.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Listen to playback state changes and update notification in real-time
        serviceScope.launch {
            combine(PlaybackStateHelper.currentTrack, PlaybackStateHelper.isPlaying) { track, playing ->
                track to playing
            }.collect { (track, playing) ->
                if (track != null) {
                    showNotification(track, playing)
                } else {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> PlaybackStateHelper.onPlayPauseAction?.invoke()
            ACTION_NEXT -> PlaybackStateHelper.onNextAction?.invoke()
            ACTION_PREVIOUS -> PlaybackStateHelper.onPrevAction?.invoke()
            ACTION_STOP -> {
                PlaybackStateHelper.isPlaying.value = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproductor de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción de música en segundo plano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(track: Track, isPlaying: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)

        // Intent to launch MainActivity when clicking notification
        val contextIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contextIntent, pendingIntentFlags)

        // Playback Action Intents
        val prevPending = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS }, pendingIntentFlags)
        val playPending = PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }, pendingIntentFlags)
        val nextPending = PendingIntent.getService(this, 3, Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT }, pendingIntentFlags)
        val stopPending = PendingIntent.getService(this, 4, Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }, pendingIntentFlags)

        val playIconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", prevPending)
            .addAction(playIconRes, if (isPlaying) "Pausar" else "Reproducir", playPending)
            .addAction(android.R.drawable.ic_media_next, "Siguiente", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending)

        val notification = notificationBuilder.build()

        // Always promote to foreground first if not already in foreground or if playing,
        // to satisfy the strict Android platform OS limit of calling startForeground within 5 seconds.
        if (isPlaying || !isServiceForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isServiceForeground = true
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to start service as foreground", e)
                try {
                    // Fallback to non-type startForeground to prevent immediate crash if background limits hit
                    startForeground(NOTIFICATION_ID, notification)
                    isServiceForeground = true
                } catch (ne: Exception) {
                    android.util.Log.e("PlaybackService", "Failed to fallback notify", ne)
                }
            }
        }

        // Now, if not playing, remove from foreground status to allow swipe-to-dismiss,
        // but keep the notification itself active.
        if (!isPlaying && isServiceForeground) {
            try {
                @Suppress("DEPRECATION")
                stopForeground(false)
                isServiceForeground = false
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to stopForeground", e)
            }
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to notify non-playing update", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
