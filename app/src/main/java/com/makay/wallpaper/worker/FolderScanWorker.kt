package com.makay.wallpaper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.util.FolderImageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val albums = AlbumRepository.getInstance(applicationContext)
            albums.getAll()
                .filter { it.watchFolder && !it.folderPath.isNullOrBlank() }
                .forEach { album ->
                    val uris = FolderImageScanner.scan(applicationContext, album.folderPath!!)
                    if (uris.isNotEmpty()) {
                        albums.addImages(album.id, uris)
                    }
                }
        }
        Result.success()
    }
}
