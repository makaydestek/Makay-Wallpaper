package com.makay.wallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.WallpaperSettingsRepository

class ScreenEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val triggers = WallpaperSettingsRepository.getInstance(context).triggers.value
        val shouldChange = when (action) {
            Intent.ACTION_SCREEN_ON -> triggers.onScreenOn
            Intent.ACTION_SCREEN_OFF -> triggers.onScreenOff
            Intent.ACTION_USER_PRESENT -> triggers.onUnlock
            else -> false
        }
        if (shouldChange) {
            WallpaperController.next(context.applicationContext)
        }
    }
}
