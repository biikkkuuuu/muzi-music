package com.biikkkuuuu.muzi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.biikkkuuuu.muzi.playback.MusicService

class LyricsWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MusicService.isRunning) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_UPDATE_LYRICS_WIDGET
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Service might be restricted in background
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_LYRICS_PLAY_PAUSE, ACTION_LYRICS_NEXT, ACTION_LYRICS_PREVIOUS -> {
                val mappedAction = when (intent.action) {
                    ACTION_LYRICS_PLAY_PAUSE -> MusicWidgetReceiver.ACTION_PLAY_PAUSE
                    ACTION_LYRICS_NEXT -> MusicWidgetReceiver.ACTION_NEXT
                    ACTION_LYRICS_PREVIOUS -> MusicWidgetReceiver.ACTION_PREVIOUS
                    else -> intent.action
                }
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = mappedAction
                    putExtras(intent)
                }
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }

    companion object {
        const val ACTION_LYRICS_PLAY_PAUSE = "com.biikkkuuuu.muzi.widget.LYRICS_PLAY_PAUSE"
        const val ACTION_LYRICS_NEXT = "com.biikkkuuuu.muzi.widget.LYRICS_NEXT"
        const val ACTION_LYRICS_PREVIOUS = "com.biikkkuuuu.muzi.widget.LYRICS_PREVIOUS"
        const val ACTION_UPDATE_LYRICS_WIDGET = "com.biikkkuuuu.muzi.widget.UPDATE_LYRICS_WIDGET"
    }
}
