package com.makay.wallpaper.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.util.WallpaperControlNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Settings shortcut for advancing the active wallpaper album. */
class NextWallpaperTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(com.makay.wallpaper.R.string.tile_next_wallpaper)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply { state = Tile.STATE_UNAVAILABLE; updateTile() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val changed = WallpaperController.next(applicationContext)
            if (changed) WallpaperControlNotification.refreshIfEnabled(applicationContext)
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    state = if (changed) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
                    updateTile()
                }
            }
        }
    }
}
