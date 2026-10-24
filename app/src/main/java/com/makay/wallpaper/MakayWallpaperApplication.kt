package com.makay.wallpaper

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.receiver.ScreenEventReceiver
import com.makay.wallpaper.util.CrashLogger
import com.makay.wallpaper.util.FolderWatchManager
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.PlatformManager

class MakayWallpaperApplication : Application() {

    private var screenReceiver: ScreenEventReceiver? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        LanguageManager.init(this)
        PlatformManager.init(this)
        registerScreenReceiver()
        FolderWatchManager.start(this)
        WallpaperScheduler.enqueueFolderScan(this)
        WallpaperScheduler.scheduleTimer(this)
        WallpaperScheduler.scheduleAllAlarms(this)
    }

    private fun registerScreenReceiver() {
        val receiver = ScreenEventReceiver()
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    companion object {
        lateinit var instance: MakayWallpaperApplication
            private set
    }
}
