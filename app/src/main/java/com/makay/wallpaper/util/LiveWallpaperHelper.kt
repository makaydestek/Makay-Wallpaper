package com.makay.wallpaper.util

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.makay.wallpaper.service.MakayLiveWallpaperService

object LiveWallpaperHelper {

    fun isOurLiveWallpaperActive(context: Context): Boolean {
        return runCatching {
            val wm = WallpaperManager.getInstance(context)
            val info = wm.wallpaperInfo ?: return false
            if (info.packageName != context.packageName) return false
            val svc = info.serviceName.orEmpty()
            val expected = MakayLiveWallpaperService::class.java.name
            val expectedShort = MakayLiveWallpaperService::class.java.simpleName
            svc == expected ||
                svc.endsWith(".$expectedShort") ||
                svc.contains(expectedShort) ||
                ComponentName(context, MakayLiveWallpaperService::class.java)
                    .flattenToString().let { flat ->
                        svc.equals(flat, ignoreCase = true) ||
                            info.component?.flattenToString().equals(flat, ignoreCase = true)
                    }
        }.getOrDefault(false)
    }

    fun statusLabel(context: Context): String {
        val live = isOurLiveWallpaperActive(context)
        val a11y = try {
            com.makay.wallpaper.service.DoubleTapAccessibilityService.isEnabled(context)
        } catch (_: Exception) {
            false
        }
        return "canlı WP ${if (live) "açık" else "kapalı"} · erişilebilirlik ${if (a11y) "açık" else "kapalı"}"
    }

    /** Sistem canlı duvar kağıdı seçicisini doğrudan Makay servisiyle açar. */
    fun openLiveWallpaperSetup(context: Context): Boolean {
        val component = ComponentName(context, MakayLiveWallpaperService::class.java)
        val change = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(change)
            true
        }.recoverCatching {
            val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /**
     * Sideload APK'larda erişilebilirlik «Kısıtlanmış ayar» ile kilitlenir.
     * Uygulama bilgisi → ⋮ → Kısıtlanmış ayarlara izin ver.
     */
    fun openAppDetailsForRestrictedSettings(context: Context): Boolean {
        val uri = Uri.fromParts("package", context.packageName, null)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(details)
            true
        }.getOrDefault(false)
    }

    /** Android 13+ bazı cihazlarda doğrudan kısıtlı ayar ekranı */
    fun openRestrictedSettingsIfPossible(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return openAppDetailsForRestrictedSettings(context)
        }
        // Önce uygulama detayı (HyperOS: ⋮ menüsünde izin)
        return openAppDetailsForRestrictedSettings(context)
    }
}
