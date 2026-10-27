package com.makay.wallpaper.core

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.GestureAction
import com.makay.wallpaper.domain.model.PlayOrder
import com.makay.wallpaper.domain.model.WallpaperImage
import com.makay.wallpaper.domain.model.WallpaperTarget
import com.makay.wallpaper.util.LiveWallpaperHelper
import com.makay.wallpaper.widget.WallpaperWidgetProvider
import java.util.Calendar

/**
 * Merkezi duvar kağıdı kontrolü — UI, worker, widget, receiver ve live wallpaper kullanır.
 */
object WallpaperController {

    private val gestureLock = Any()
    private val indexLock = Any()
    @Volatile private var lastGestureAt = 0L
    private const val GESTURE_COOLDOWN_MS = 700L

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
        val gesturesOn = settings.gestures.value.enabled

        // Jestler açıkken canlı WP varsa ASLA setBitmap (HOME) — jestleri öldürür
        // Jestler açık + canlı yok: yine de statik basma; kullanıcı canlıyı yeni ayarladıysa ezilmesin
        if (gesturesOn && live) {
            settings.markChangedNow()
            notifyChanged(context)
            // Kilit ayrı albüm ise yalnız kilidi statik güncelle (ana canlı kalır)
            if (lockSeparate || target == WallpaperTarget.LOCK) {
                val lock = currentImage(context, forLock = true) ?: home
                if (target != WallpaperTarget.HOME) {
                    WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                    settings.setLastLockUri(lock.uri)
                } else if (lockSeparate) {
                    WallpaperApplier.setWallpaper(context, lock, WallpaperTarget.LOCK)
                    settings.setLastLockUri(lock.uri)
                }
            }
            return true
        }

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
     * @param sequential true ise (UI / jest) playOrder yok sayılır; albüm sırasıyla ilerler.
     * Zamanlayıcı / tetikleyicilerde sıra yalnızca timer.playOrder ile belirlenir.
     */
    fun next(
        context: Context,
        sequential: Boolean = false,
        allowDayPart: Boolean = false
    ): Boolean {
        if (isLandscapePaused(context)) return false
        if (allowDayPart) applyDayPartIfNeeded(context)
        val settings = WallpaperSettingsRepository.getInstance(context)
        val order = if (sequential) {
            PlayOrder.SEQUENTIAL
        } else {
            settings.resolvedPlayOrder()
        }
        return when (order) {
            PlayOrder.RANDOM -> random(context, allowDayPart = false)
            PlayOrder.NEWEST_FIRST, PlayOrder.OLDEST_FIRST ->
                advanceOrdered(context, +1, order)
            PlayOrder.SEQUENTIAL -> advance(context, +1)
        }
    }

    fun prev(context: Context): Boolean {
        if (isLandscapePaused(context)) return false
        val settings = WallpaperSettingsRepository.getInstance(context)
        return when (val order = settings.resolvedPlayOrder()) {
            PlayOrder.NEWEST_FIRST, PlayOrder.OLDEST_FIRST ->
                advanceOrdered(context, -1, order)
            // Rastgele modda geri = albüm listesinde bir önceki (karışıklığı bozmaz)
            else -> advance(context, -1)
        }
    }

    fun random(context: Context, allowDayPart: Boolean = false): Boolean {
        if (isLandscapePaused(context)) return false
        if (allowDayPart) applyDayPartIfNeeded(context)
        val settings = WallpaperSettingsRepository.getInstance(context)
        val albums = AlbumRepository.getInstance(context)
        val albumId = settings.activeAlbumId.value ?: return false
        val album = albums.getById(albumId) ?: return false
        if (album.images.isEmpty()) return false
        val next = settings.nextShuffledIndex(
            albumId,
            album.images.size,
            settings.activeIndex.value
        )
        settings.setActiveIndex(next)
        if (settings.lockAlbumId.value != null) {
            val lockAlbum = albums.getById(settings.lockAlbumId.value!!)
            if (lockAlbum != null && lockAlbum.images.isNotEmpty()) {
                val li = settings.nextShuffledIndex(
                    "lock:${settings.lockAlbumId.value}",
                    lockAlbum.images.size,
                    settings.lockIndex.value
                )
                settings.setLockIndex(li)
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
        if (action == GestureAction.NONE) return false
        // Canlı WP + erişilebilirlik aynı dokunuşu iki kez işleyebilir; tek jest uygula
        synchronized(gestureLock) {
            val now = System.currentTimeMillis()
            if (now - lastGestureAt < GESTURE_COOLDOWN_MS) return false
            lastGestureAt = now
        }
        return when (action) {
            GestureAction.NEXT -> next(context, sequential = true)
            GestureAction.PREV -> prev(context)
            GestureAction.NEXT_ALBUM -> nextAlbum(context)
            GestureAction.PREV_ALBUM -> prevAlbum(context)
            GestureAction.NONE -> false
        }
    }

    /** Görünürlük tetikleyicileri jest ile çakışmasın */
    fun isGestureCooldownActive(): Boolean {
        return System.currentTimeMillis() - lastGestureAt < GESTURE_COOLDOWN_MS
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
        synchronized(indexLock) {
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
        }
        return applyCurrent(context)
    }

    /** Tarih / eklenme sırasına göre ilerle. */
    private fun advanceOrdered(context: Context, delta: Int, order: PlayOrder): Boolean {
        synchronized(indexLock) {
            val settings = WallpaperSettingsRepository.getInstance(context)
            val albums = AlbumRepository.getInstance(context)
            val albumId = settings.activeAlbumId.value ?: return false
            val album = albums.getById(albumId) ?: return false
            if (album.images.isEmpty()) return false
            val ordered = orderedIndices(context, album.images, order)
            val current = settings.activeIndex.value.coerceIn(0, album.images.lastIndex)
            val pos = ordered.indexOf(current).let { if (it < 0) 0 else it }
            val size = ordered.size
            val nextPos = ((pos + delta) % size + size) % size
            settings.setActiveIndex(ordered[nextPos])
            if (settings.lockAlbumId.value != null) {
                val lockAlbum = albums.getById(settings.lockAlbumId.value!!)
                if (lockAlbum != null && lockAlbum.images.isNotEmpty()) {
                    val lockOrdered = orderedIndices(context, lockAlbum.images, order)
                    val lc = settings.lockIndex.value.coerceIn(0, lockAlbum.images.lastIndex)
                    val lp = lockOrdered.indexOf(lc).let { if (it < 0) 0 else it }
                    val ls = lockOrdered.size
                    val nlp = ((lp + delta) % ls + ls) % ls
                    settings.setLockIndex(lockOrdered[nlp])
                }
            }
        }
        return applyCurrent(context)
    }

    private fun orderedIndices(
        context: Context,
        images: List<WallpaperImage>,
        order: PlayOrder
    ): List<Int> {
        if (order != PlayOrder.NEWEST_FIRST && order != PlayOrder.OLDEST_FIRST) {
            return images.indices.toList()
        }
        val indexed = images.mapIndexed { i, img -> i to imageSortKey(context, img, i) }
        return when (order) {
            PlayOrder.NEWEST_FIRST -> indexed.sortedByDescending { it.second }.map { it.first }
            PlayOrder.OLDEST_FIRST -> indexed.sortedBy { it.second }.map { it.first }
            else -> images.indices.toList()
        }
    }

    /** addedAt yoksa dosya / belge zamanına düş (eski albümler için). */
    private fun imageSortKey(context: Context, img: WallpaperImage, index: Int): Long {
        if (img.addedAt > 0L) return img.addedAt
        val uri = runCatching { Uri.parse(img.uri) }.getOrNull() ?: return index.toLong()
        val fromDoc = runCatching {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                ?.lastModified()
                ?.takeIf { it > 0L }
        }.getOrNull()
        if (fromDoc != null) return fromDoc
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val modified = java.io.File(path).takeIf { it.isFile }?.lastModified() ?: 0L
                if (modified > 0L) return modified
            }
        }
        return index.toLong()
    }

    private fun switchAlbum(context: Context, delta: Int): Boolean {
        val albums = AlbumRepository.getInstance(context).getAll()
        if (albums.isEmpty()) return false
        // Tek albümde «sonraki albüm» 1. fotoğrafa sıçramasın — sıradaki görsel
        if (albums.size == 1) return advance(context, if (delta >= 0) 1 else -1)
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
        runCatching {
            com.makay.wallpaper.util.WallpaperControlNotification.refreshIfEnabled(context)
        }
    }

    const val ACTION_WALLPAPER_CHANGED = "com.makay.wallpaper.WALLPAPER_CHANGED"
}
