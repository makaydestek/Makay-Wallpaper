package com.makay.wallpaper.util

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.makay.wallpaper.service.MakayLiveWallpaperService

object LiveWallpaperHelper {

    fun isOurLiveWallpaperActive(context: Context): Boolean {
        return runCatching {
            val info = WallpaperManager.getInstance(context).wallpaperInfo
            info != null &&
                info.packageName == context.packageName &&
                info.serviceName == MakayLiveWallpaperService::class.java.name
        }.getOrDefault(false)
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
}
