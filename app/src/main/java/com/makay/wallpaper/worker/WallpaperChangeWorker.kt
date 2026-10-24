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
        runCatching {
            val timer = WallpaperSettingsRepository.getInstance(applicationContext).timer.value
            if (!timer.enabled) {
                WallpaperScheduler.cancelTimer(applicationContext)
                return@runCatching
            }
            WallpaperController.next(applicationContext)
            val interval = timer.intervalMillis()
            if (interval < 15L * 60_000L) {
                WallpaperScheduler.rescheduleTimerAfterChange(applicationContext)
            }
        }
        Result.success()
    }
}
