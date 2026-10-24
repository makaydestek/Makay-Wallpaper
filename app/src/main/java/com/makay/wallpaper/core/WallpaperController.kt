package com.makay.wallpaper.core

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.GestureAction
import com.makay.wallpaper.domain.model.WallpaperImage
import com.makay.wallpaper.domain.model.WallpaperTarget
import com.makay.wallpaper.util.LiveWallpaperHelper
import com.makay.wallpaper.widget.WallpaperWidgetProvider
import java.util.Calendar
import kotlin.random.Random

/**
 * Merkezi duvar kağıdı kontrolü — UI, worker, widget, receiver ve live wallpaper kullanır.
 */
object WallpaperController {

    fun isLandscapePaused(context: Context): Boolean {
        val behavior = WallpaperSettingsRepository.getInstance(context).behavior.value
        if (!behavior.pauseInLandscape) return false
        val orient = context.resources.configuration.orientation
        return orient == Configuration.ORIENTATION_LANDSCAPE
    }

    fun currentImage(context: Context, forLock: Boolean = false): WallpaperImage? {
        val settings = WallpaperSettingsRepository.getInstance(context)
        val albums = AlbumRepository.getInstance(context)
        val albumId = if (forLock) {
            settings.lockAlbumId.value ?: settings.activeAlbumId.value
        } else {
            settings.activeAlbumId.value
        } ?: return null
        val album = albums.getById(albumId) ?: return null
        if (album.images.isEmpty()) return null
        val index = if (forLock && settings.lockAlbumId.value != null) {
            settings.lockIndex.value
        } else {
            settings.activeIndex.value
        }.coerceIn(0, album.images.lastIndex)
        return album.images[index]
    }

    /**
     * @param allowDayPart true yalnızca zamanlayıcı/worker — manuel geçişte albüm değişmesin
     */
    fun applyCurrent(context: Context, allowDayPart: Boolean = false): Boolean {
        if (isLandscapePaused(context)) return false
        if (allowDayPart) applyDayPartIfNeeded(context)
        val settings = WallpaperSettingsRepository.getInstance(context)
        val home = currentImage(context, forLock = false) ?: return false
        val target = settings.target.value
        val lockSeparate = settings.lockAlbumId.value != null
        val live = LiveWallpaperHelper.isOurLiveWallpaperActive(context)

        // Canlı WP aktifse setBitmap ÇALIŞTIRMA — jestleri ve canlı motoru öldürür
        if (live) {
            var ok = true
            // Kilit ayrıysa (veya yalnız kilit hedefi) statik kilit uygula
            when {
                target == WallpaperTarget.LOCK -> {
                    val lock = currentImage(context, forLock = true) ?: home
                    ok = WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                    if (ok) settings.setLastLockUri(lock.uri)
                }
                lockSeparate && target != WallpaperTarget.HOME -> {
                    val lock = currentImage(context, forLock = true) ?: home
                    ok = WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                    if (ok) settings.setLastLockUri(lock.uri)
                }
                lockSeparate && target == WallpaperTarget.HOME -> {
                    // Ana canlı; kilit albümünü ayrı tut (OEM sync önleme)
                    val lock = currentImage(context, forLock = true)
                    if (lock != null) {
                        WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                        settings.setLastLockUri(lock.uri)
                    }
                }
                target == WallpaperTarget.BOTH && !lockSeparate -> {
                    // Canlı genelde yalnız ana ekranda; kilit için aynı görseli kilide yaz
                    WallpaperApplier.setWallpaper(context, home, WallpaperTarget.LOCK)
                    settings.setLastLockUri(home.uri)
                }
            }
            settings.markChangedNow()
            notifyChanged(context)
            return ok
        }

        // Statik duvar kağıdı yolu
        val ok = when {
            lockSeparate -> applyWithSeparateLock(context, home, target)
            else -> applyWithSharedAlbum(context, home, target)
        }
        if (ok) {
            settings.markChangedNow()
            notifyChanged(context)
        }
        return ok
    }

    private fun applyWithSeparateLock(
        context: Context,
        home: WallpaperImage,
        target: WallpaperTarget
    ): Boolean {
        val settings = WallpaperSettingsRepository.getInstance(context)
        val lock = currentImage(context, forLock = true) ?: home
        return when (target) {
            WallpaperTarget.HOME -> {
                val homeOk = WallpaperApplier.setWallpaper(context, home, WallpaperTarget.HOME)
                // OEM ana+kilit senkronunu kır: kilidi ayrı albüm görseliyle geri yaz
                val lockOk = WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                if (lockOk) settings.setLastLockUri(lock.uri)
                homeOk || lockOk
            }
            WallpaperTarget.LOCK -> {
                val lockOk = WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                if (lockOk) settings.setLastLockUri(lock.uri)
                lockOk
            }
            WallpaperTarget.BOTH -> {
                val homeOk = WallpaperApplier.setWallpaper(context, home, WallpaperTarget.HOME)
                val lockOk = WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                if (lockOk) settings.setLastLockUri(lock.uri)
                homeOk || lockOk
            }
        }
    }

    private fun applyWithSharedAlbum(
        context: Context,
        home: WallpaperImage,
        target: WallpaperTarget
    ): Boolean {
        val settings = WallpaperSettingsRepository.getInstance(context)
        return when (target) {
            WallpaperTarget.HOME -> {
                val homeOk = WallpaperApplier.setWallpaper(context, home, WallpaperTarget.HOME)
                // MIUI vb.: yalnız HOME istenince kilidi eski kilit görseliyle geri yükle
                reapplySavedLock(context)
                homeOk
            }
            WallpaperTarget.LOCK -> {
                val ok = WallpaperApplier.setWallpaper(context, home, WallpaperTarget.LOCK)
                if (ok) settings.setLastLockUri(home.uri)
                ok
            }
            WallpaperTarget.BOTH -> {
                val ok = WallpaperApplier.setWallpaper(context, home, WallpaperTarget.BOTH)
                if (ok) settings.setLastLockUri(home.uri)
                ok
            }
        }
    }

    private fun reapplySavedLock(context: Context) {
        val settings = WallpaperSettingsRepository.getInstance(context)
        val uri = settings.lastLockUri() ?: return
        // Ana ile aynıysa tekrar yazmaya gerek yok
        val home = currentImage(context, forLock = false)
        if (home?.uri == uri) return
        runCatching {
            WallpaperApplier.setWallpaper(
                context,
                Uri.parse(uri),
                target = WallpaperTarget.LOCK
            )
        }
    }

    /**
     * @param sequential true ise (UI / jest) rastgele ayarı yok sayılır.
     */
    fun next(
        context: Context,
        sequential: Boolean = false,
        allowDayPart: Boolean = false
    ): Boolean {
        if (isLandscapePaused(context)) return false
        if (allowDayPart) applyDayPartIfNeeded(context)
        val settings = WallpaperSettingsRepository.getInstance(context)
        // Rastgele yalnız AKTİF albüm içinde — başka klasöre geçmez
        if (!sequential && (settings.random.value || settings.timer.value.random)) {
            return random(context, allowDayPart = false)
        }
        return advance(context, +1)
    }

    fun prev(context: Context): Boolean {
        if (isLandscapePaused(context)) return false
        return advance(context, -1)
    }

    fun random(context: Context, allowDayPart: Boolean = false): Boolean {
        if (isLandscapePaused(context)) return false
        if (allowDayPart) applyDayPartIfNeeded(context)
        val settings = WallpaperSettingsRepository.getInstance(context)
        val albums = AlbumRepository.getInstance(context)
        val albumId = settings.activeAlbumId.value ?: return false
        val album = albums.getById(albumId) ?: return false
        if (album.images.isEmpty()) return false
        settings.setActiveIndex(Random.nextInt(album.images.size))
        if (settings.lockAlbumId.value != null) {
            val lockAlbum = albums.getById(settings.lockAlbumId.value!!)
            if (lockAlbum != null && lockAlbum.images.isNotEmpty()) {
                settings.setLockIndex(Random.nextInt(lockAlbum.images.size))
            }
        }
        return applyCurrent(context, allowDayPart = false)
    }

    fun setAlbum(context: Context, albumId: String, startIndex: Int = 0): Boolean {
        val albums = AlbumRepository.getInstance(context)
        val album = albums.getById(albumId) ?: return false
        val settings = WallpaperSettingsRepository.getInstance(context)
        settings.setActiveAlbumId(albumId)
        if (album.images.isEmpty()) {
            settings.setActiveIndex(0)
            return false
        }
        settings.setActiveIndex(startIndex.coerceIn(0, album.images.lastIndex))
        return applyCurrent(context)
    }

    fun setLockAlbum(context: Context, albumId: String?): Boolean {
        WallpaperSettingsRepository.getInstance(context).setLockAlbumId(albumId)
        return applyCurrent(context)
    }

    fun nextAlbum(context: Context): Boolean = switchAlbum(context, +1)

    fun prevAlbum(context: Context): Boolean = switchAlbum(context, -1)

    fun performGesture(context: Context, action: GestureAction): Boolean {
        return when (action) {
            GestureAction.NEXT -> next(context, sequential = true)
            GestureAction.PREV -> prev(context)
            GestureAction.NEXT_ALBUM -> nextAlbum(context)
            GestureAction.PREV_ALBUM -> prevAlbum(context)
            GestureAction.NONE -> false
        }
    }

    fun nextChangeEtaMillis(context: Context): Long? {
        val timer = WallpaperSettingsRepository.getInstance(context).timer.value
        if (!timer.enabled) return null
        val last = WallpaperSettingsRepository.getInstance(context).lastChangeAt.value
        if (last <= 0L) return timer.intervalMillis()
        val elapsed = System.currentTimeMillis() - last
        return (timer.intervalMillis() - elapsed).coerceAtLeast(0L)
    }

    private fun applyDayPartIfNeeded(context: Context) {
        val settings = WallpaperSettingsRepository.getInstance(context)
        val dayPart = settings.dayPart.value
        if (!dayPart.enabled) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val id = dayPart.albumIdForHour(hour) ?: return
        if (id == settings.activeAlbumId.value) return
        val album = AlbumRepository.getInstance(context).getById(id) ?: return
        if (album.images.isEmpty()) return
        settings.setActiveAlbumId(id)
        settings.setActiveIndex(0)
    }

    private fun advance(context: Context, delta: Int): Boolean {
        val settings = WallpaperSettingsRepository.getInstance(context)
        val albums = AlbumRepository.getInstance(context)
        val albumId = settings.activeAlbumId.value ?: return false
        val album = albums.getById(albumId) ?: return false
        if (album.images.isEmpty()) return false
        val size = album.images.size
        val nextIndex = ((settings.activeIndex.value + delta) % size + size) % size
        settings.setActiveIndex(nextIndex)
        if (settings.lockAlbumId.value != null) {
            val lockAlbum = albums.getById(settings.lockAlbumId.value!!)
            if (lockAlbum != null && lockAlbum.images.isNotEmpty()) {
                val ls = lockAlbum.images.size
                val li = ((settings.lockIndex.value + delta) % ls + ls) % ls
                settings.setLockIndex(li)
            }
        }
        return applyCurrent(context)
    }

    private fun switchAlbum(context: Context, delta: Int): Boolean {
        val albums = AlbumRepository.getInstance(context).getAll()
        if (albums.isEmpty()) return false
        val settings = WallpaperSettingsRepository.getInstance(context)
        val currentId = settings.activeAlbumId.value
        val currentIdx = albums.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val size = albums.size
        val nextIdx = ((currentIdx + delta) % size + size) % size
        return setAlbum(context, albums[nextIdx].id, 0)
    }

    private fun notifyChanged(context: Context) {
        runCatching {
            context.sendBroadcast(
                Intent(WallpaperWidgetProvider.ACTION_REFRESH).setPackage(context.packageName)
            )
        }
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_WALLPAPER_CHANGED).setPackage(context.packageName)
            )
        }
    }

    const val ACTION_WALLPAPER_CHANGED = "com.makay.wallpaper.WALLPAPER_CHANGED"
}
