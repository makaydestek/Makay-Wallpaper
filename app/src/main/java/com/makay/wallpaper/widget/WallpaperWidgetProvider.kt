package com.makay.wallpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.makay.wallpaper.MainActivity
import com.makay.wallpaper.R
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository

class WallpaperWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, it, appWidgetManager) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NEXT -> {
                WallpaperController.next(context.applicationContext)
                refreshAll(context)
                com.makay.wallpaper.util.WallpaperControlNotification.refreshIfEnabled(context)
            }
            ACTION_PREV -> {
                WallpaperController.prev(context.applicationContext)
                refreshAll(context)
                com.makay.wallpaper.util.WallpaperControlNotification.refreshIfEnabled(context)
            }
            ACTION_NEXT_ALBUM -> {
                WallpaperController.nextAlbum(context.applicationContext)
                refreshAll(context)
                com.makay.wallpaper.util.WallpaperControlNotification.refreshIfEnabled(context)
            }
            ACTION_REFRESH, WallpaperController.ACTION_WALLPAPER_CHANGED -> {
                refreshAll(context)
                com.makay.wallpaper.util.WallpaperControlNotification.refreshIfEnabled(context)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetId: Int, appWidgetManager: AppWidgetManager) {
        val views = RemoteViews(context.packageName, R.layout.widget_wallpaper)
        val settings = WallpaperSettingsRepository.getInstance(context)
        val albums = AlbumRepository.getInstance(context)
        val album = albums.getById(settings.activeAlbumId.value.orEmpty())
        val title = album?.name ?: context.getString(R.string.app_name)
        views.setTextViewText(R.id.widget_title, title)

        val eta = WallpaperController.nextChangeEtaMillis(context)
        val subtitle = buildString {
            if (album != null && album.images.isNotEmpty()) {
                append("${settings.activeIndex.value + 1}/${album.images.size}")
            }
            if (eta != null) {
                if (isNotEmpty()) append(" · ")
                append(com.makay.wallpaper.util.WallpaperControlNotification.formatEta(eta))
            }
            if (WallpaperController.isLandscapePaused(context)) {
                if (isNotEmpty()) append(" · ")
                append("⏸")
            }
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle.ifBlank { "Makay" })

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        fun broadcast(action: String, req: Int): PendingIntent {
            val i = Intent(context, WallpaperWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                appWidgetId + req,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        views.setOnClickPendingIntent(R.id.widget_next, broadcast(ACTION_NEXT, 10_000))
        views.setOnClickPendingIntent(R.id.widget_prev, broadcast(ACTION_PREV, 20_000))
        views.setOnClickPendingIntent(R.id.widget_next_album, broadcast(ACTION_NEXT_ALBUM, 30_000))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_NEXT = "com.makay.wallpaper.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.makay.wallpaper.widget.ACTION_PREV"
        const val ACTION_NEXT_ALBUM = "com.makay.wallpaper.widget.ACTION_NEXT_ALBUM"
        const val ACTION_REFRESH = "com.makay.wallpaper.widget.ACTION_REFRESH"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WallpaperWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val provider = WallpaperWidgetProvider()
            ids.forEach { provider.updateWidget(context, it, mgr) }
        }
    }
}
