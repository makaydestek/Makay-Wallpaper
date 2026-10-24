package com.makay.wallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.util.FolderWatchManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = context.applicationContext
            FolderWatchManager.start(app)
            WallpaperScheduler.rescheduleOnBoot(app)
        }
    }
}
