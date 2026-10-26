package com.makay.wallpaper.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.makay.wallpaper.MainActivity
import com.makay.wallpaper.R
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.widget.WallpaperWidgetProvider

/**
 * Kalıcı bildirim: Sonraki / Önceki / Sonraki albüm hızlı kontrolleri.
 */
object WallpaperControlNotification {

    private const val PREFS = "control_notification_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL_ID = "wallpaper_controls"
    private const val NOTIF_ID = 4201

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) show(context) else hide(context)
    }

    fun refreshIfEnabled(context: Context) {
        if (isEnabled(context)) show(context)
    }

    fun show(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService<NotificationManager>() ?: return
        ensureChannel(nm)

        val settings = WallpaperSettingsRepository.getInstance(app)
        val album = AlbumRepository.getInstance(app).getById(settings.activeAlbumId.value.orEmpty())
        val title = album?.name ?: app.getString(R.string.app_name)
        val index = if (album != null && album.images.isNotEmpty()) {
            "${settings.activeIndex.value + 1}/${album.images.size}"
        } else {
            "—"
        }
        val eta = WallpaperController.nextChangeEtaMillis(app)
        val etaText = eta?.let { formatEta(it) }
        val subtitle = buildString {
            append(index)
            if (etaText != null) {
                append(" · ")
                append(etaText)
            }
        }

        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun action(action: String, req: Int): PendingIntent {
            val i = Intent(app, WallpaperWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                app,
                req,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_wallpaper)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                LanguageManager.t("Önceki", "Prev", app),
                action(WallpaperWidgetProvider.ACTION_PREV, 51_001)
            )
            .addAction(
                0,
                LanguageManager.t("Sonraki", "Next", app),
                action(WallpaperWidgetProvider.ACTION_NEXT, 51_002)
            )
            .addAction(
                0,
                LanguageManager.t("Albüm", "Album", app),
                action(WallpaperWidgetProvider.ACTION_NEXT_ALBUM, 51_003)
            )
            .build()

        nm.notify(NOTIF_ID, notification)
    }

    fun hide(context: Context) {
        context.applicationContext.getSystemService<NotificationManager>()
            ?.cancel(NOTIF_ID)
    }

    fun formatEta(etaMs: Long): String {
        val totalSec = (etaMs / 1000).toInt().coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(h, m, s)
            else -> "%02d:%02d".format(m, s)
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Wallpaper controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick next / previous wallpaper controls"
                setShowBadge(false)
            }
        )
    }
}
