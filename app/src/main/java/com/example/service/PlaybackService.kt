package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isServiceForeground = false

    companion object {
        const val CHANNEL_ID = "playback_control_channel"
        const val NOTIFICATION_ID = 404

        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        const val ACTION_PLAY_PAUSE = "com.example.service.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.service.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Observe the global playback changes to dynamically update the notification automatically.
        serviceScope.launch {
            combine(PlaybackStateHelper.currentTrack, PlaybackStateHelper.isPlaying) { track, isPlaying ->
                Pair(track, isPlaying)
            }.distinctUntilChanged().collect { (track, isPlaying) ->
                // Sync status with our custom launcher App Widget
                com.example.widget.MusicWidgetProvider.updateAllWidgets(this@PlaybackService, track, isPlaying)

                if (track != null) {
                    showOrUpdateNotification(track, isPlaying)
                } else {
                    try {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackService", "Error stopping foreground", e)
                    }
                    isServiceForeground = false
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    PlaybackStateHelper.onPlayPauseAction?.invoke()
                }
                ACTION_NEXT -> {
                    PlaybackStateHelper.onNextAction?.invoke()
                }
                ACTION_PREVIOUS -> {
                    PlaybackStateHelper.onPrevAction?.invoke()
                }
                ACTION_STOP -> {
                    try {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackService", "Error stopping foreground on action stop", e)
                    }
                    isServiceForeground = false
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        // Avoid updating notification redundantly. The distinctUntilChanged flow collector in onCreate
        // will automatically handle notification updates when state fields are mutated.
        val currentTrackLocal = PlaybackStateHelper.currentTrack.value
        if (currentTrackLocal == null) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showOrUpdateNotification(track: com.example.data.Track, isPlaying: Boolean) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val prevIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPending = PendingIntent.getService(
            this, 11, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(
            this, 12, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(
            this, 13, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 14, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) "Pausar" else "Reproducir"

        // Track custom brand Accent Color for beautiful themed UI (aesthetic integrity)
        val brandColor = try {
            android.graphics.Color.parseColor(track.accentColorHex)
        } catch (e: Exception) {
            android.graphics.Color.BLUE
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play) // Elegant fallback play vector
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopPending) // Clean swipe-to-dismiss behavior
            .setOngoing(isPlaying)
            .setColorized(true)
            .setColor(brandColor)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", prevPending)
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Siguiente", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar", stopPending)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        val notification = notificationBuilder.build()

        val manager = getSystemService(NotificationManager::class.java)

        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isServiceForeground = true
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to startForeground", e)
                try {
                    manager.notify(NOTIFICATION_ID, notification)
                } catch (ne: Exception) {
                    android.util.Log.e("PlaybackService", "Failed to fallback notify", ne)
                }
            }
        } else {
            if (isServiceForeground) {
                try {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Failed to stopForeground", e)
                }
                isServiceForeground = false
            }
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to update notification", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproductor de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles persistentes para el reproductor de música"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            com.example.widget.MusicWidgetProvider.updateAllWidgets(this, null, false)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Error clearing widget on destroy", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
