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
        try {
            val albums = AlbumRepository.getInstance(applicationContext)
            albums.getAll()
                .filter { it.watchFolder && !it.folderPath.isNullOrBlank() }
                .forEach { album ->
                    val scan = FolderImageScanner.scanWithStatus(
                        applicationContext,
                        album.folderPath!!
                    )
                    if (!scan.completed) throw IllegalStateException("Folder scan failed")
                    // Başarılı boş tarama, klasördeki son görsellerin silindiği anlamına gelebilir.
                    albums.syncFolderImages(album.id, scan.images)
                }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
