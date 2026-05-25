package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.R
import com.example.data.Track
import com.example.service.PlaybackService
import com.example.service.PlaybackStateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val currentTrack = PlaybackStateHelper.currentTrack.value
        val isPlaying = PlaybackStateHelper.isPlaying.value

        appWidgetIds.forEach { appWidgetId ->
            updateSingleWidget(context, appWidgetManager, appWidgetId, currentTrack, isPlaying)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context, track: Track?, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            appWidgetIds.forEach { appWidgetId ->
                updateSingleWidget(context, appWidgetManager, appWidgetId, track, isPlaying)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            track: Track?,
            isPlaying: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.music_widget_layout)

            // Setup text values
            if (track != null) {
                views.setTextViewText(R.id.widget_track_title, track.title)
                views.setTextViewText(R.id.widget_track_artist, "${track.artist} • ${track.album}")
                val playPauseIcon = if (isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                }
                views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
            } else {
                views.setTextViewText(R.id.widget_track_title, "No hay reproducción")
                views.setTextViewText(R.id.widget_track_artist, "Selecciona una canción")
                views.setImageViewResource(R.id.widget_btn_play_pause, android.R.drawable.ic_media_play)
                views.setImageViewResource(R.id.widget_album_art, android.R.drawable.ic_media_play)
            }

            // Setup Intents
            // 1. Open app when clicking root layout or metadata
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // 2. Control intents
            val playPauseIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY_PAUSE
            }
            val playPausePending = PendingIntent.getService(
                context,
                101,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePending)

            val nextIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_NEXT
            }
            val nextPending = PendingIntent.getService(
                context,
                102,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

            val prevIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PREVIOUS
            }
            val prevPending = PendingIntent.getService(
                context,
                103,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)

            // Load remote album cover if available
            if (track != null && track.coverUrl.isNotBlank()) {
                val imageRequest = ImageRequest.Builder(context)
                    .data(track.coverUrl)
                    .target { drawable ->
                        val bitmap = (drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                        }
                    }
                    .build()
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        ImageLoader(context).execute(imageRequest)
                    } catch (e: Exception) {
                        android.util.Log.e("MusicWidgetProvider", "Failed to load album art for widget", e)
                        views.setImageViewResource(R.id.widget_album_art, android.R.drawable.ic_media_play)
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                    }
                }
            } else {
                views.setImageViewResource(R.id.widget_album_art, android.R.drawable.ic_media_play)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
