package com.makay.wallpaper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.data.WallpaperSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WallpaperChangeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val timer = WallpaperSettingsRepository.getInstance(applicationContext).timer.value
            if (!timer.enabled) {
                WallpaperScheduler.cancelTimer(applicationContext)
                return@withContext Result.success()
            }
            // Zamanlayıcı: yalnız aktif albüm; gün-saati albüm geçişi worker'da.
            // next → applyCurrent zaten aralığı sıfırlar; yedek olarak yeniden planla.
            // false can be an intentional no-op (for example, landscape pause).
            WallpaperController.next(applicationContext, allowDayPart = true)
            WallpaperScheduler.rescheduleTimerAfterChange(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
