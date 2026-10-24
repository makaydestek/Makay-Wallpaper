package com.makay.wallpaper.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.FileObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.makay.wallpaper.data.AlbumRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * İzlenen albüm klasörleri için anlık takip:
 * - Dosya yolu: FileObserver
 * - SAF / genel: MediaStore ContentObserver + kısa gecikmeli tarama
 */
object FolderWatchManager {

    private const val TAG = "FolderWatch"
    private const val DEBOUNCE_MS = 1_200L

    private val observers = ConcurrentHashMap<String, FileObserver>()
    private var mediaObserver: ContentObserver? = null
    private var appContext: Context? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanRunnable = Runnable {
        val ctx = appContext ?: return@Runnable
        runCatching { scanWatchedAlbums(ctx) }
    }

    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        ensureWorker()
        refresh(context.applicationContext)
        registerMediaObserver(context.applicationContext)
    }

    @Synchronized
    fun stop() {
        mainHandler.removeCallbacks(scanRunnable)
        workerHandler?.removeCallbacksAndMessages(null)
        observers.values.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        appContext?.contentResolver?.let { cr ->
            mediaObserver?.let { runCatching { cr.unregisterContentObserver(it) } }
        }
        mediaObserver = null
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }

    /** Albüm listesi değişince FileObserver'ları yenile. */
    @Synchronized
    fun refresh(context: Context) {
        val app = context.applicationContext
        appContext = app
        ensureWorker()

        observers.values.forEach { runCatching { it.stopWatching() } }
        observers.clear()

        AlbumRepository.getInstance(app).getAll()
            .filter { it.watchFolder && !it.folderPath.isNullOrBlank() }
            .forEach { album ->
                val path = resolveWatchablePath(album.folderPath!!) ?: return@forEach
                startFileObserver(album.id, path)
            }
        Log.d(TAG, "Watching ${observers.size} folder path(s)")
    }

    fun requestScanSoon() {
        mainHandler.removeCallbacks(scanRunnable)
        mainHandler.postDelayed(scanRunnable, DEBOUNCE_MS)
    }

    fun scanNow(context: Context) {
        runCatching { scanWatchedAlbums(context.applicationContext) }
    }

    private fun ensureWorker() {
        if (workerThread?.isAlive == true) return
        val thread = HandlerThread("FolderWatch").also { it.start() }
        workerThread = thread
        workerHandler = Handler(thread.looper)
    }

    private fun registerMediaObserver(context: Context) {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(workerHandler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d(TAG, "MediaStore change: $uri")
                requestScanSoon()
            }
        }
        mediaObserver = observer
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
    }

    private fun startFileObserver(albumId: String, path: String) {
        val dir = File(path)
        if (!dir.isDirectory) return
        val mask = FileObserver.CREATE or FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or FileObserver.DELETE or FileObserver.MOVED_FROM
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(path, mask) {
            override fun onEvent(event: Int, path: String?) {
                val e = event and FileObserver.ALL_EVENTS
                if (e == 0) return
                Log.d(TAG, "FileObserver album=$albumId event=$e file=$path")
                requestScanSoon()
            }
        }
        runCatching {
            observer.startWatching()
            observers[albumId] = observer
        }
    }

    /**
     * content:// ağaç URI'leri için FileObserver kullanılamaz; MediaStore yeter.
     * file:// veya düz yol için mutlak dizin yolu döner.
     */
    private fun resolveWatchablePath(treeUriOrPath: String): String? {
        return when {
            treeUriOrPath.startsWith("content:", ignoreCase = true) -> null
            treeUriOrPath.startsWith("file:", ignoreCase = true) ->
                Uri.parse(treeUriOrPath).path?.takeIf { File(it).isDirectory }
            else -> treeUriOrPath.takeIf { File(it).isDirectory }
        }
    }

    private fun scanWatchedAlbums(context: Context) {
        val albums = AlbumRepository.getInstance(context)
        albums.getAll()
            .filter { it.watchFolder && !it.folderPath.isNullOrBlank() }
            .forEach { album ->
                val uris = FolderImageScanner.scan(context, album.folderPath!!)
                if (uris.isNotEmpty()) {
                    albums.addImages(album.id, uris)
                }
            }
    }
}
