package com.makay.wallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.data.ScheduleRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.ScheduleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val action = intent?.action
        val scheduleId = intent?.getStringExtra(EXTRA_SCHEDULE_ID)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_TIMER_TICK -> {
                        val timer = WallpaperSettingsRepository.getInstance(app).timer.value
                        if (!timer.enabled) {
                            WallpaperScheduler.cancelTimer(app)
                            return@launch
                        }
                        WallpaperController.next(app, allowDayPart = true)
                        WallpaperScheduler.rescheduleTimerAfterChange(app)
                    }
                    ACTION_SCHEDULE -> {
                        val id = scheduleId ?: return@launch
                        val schedule = ScheduleRepository.getInstance(app).getById(id)
                            ?: return@launch
                        if (!schedule.enabled) return@launch
                        when (schedule.type) {
                            ScheduleType.WALLPAPER -> {
                                if (schedule.random) WallpaperController.random(app)
                                else WallpaperController.next(app)
                            }
                            ScheduleType.ALBUM -> {
                                val albumId = schedule.targetAlbumId
                                if (!albumId.isNullOrBlank()) {
                                    WallpaperController.setAlbum(app, albumId)
                                    if (schedule.random) WallpaperController.random(app)
                                }
                            }
                        }
                        WallpaperScheduler.scheduleAlarm(app, schedule)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled wallpaper action failed", e)
                runCatching {
                    when (action) {
                        ACTION_TIMER_TICK -> WallpaperScheduler.rescheduleTimerAfterChange(app)
                        ACTION_SCHEDULE -> scheduleId
                            ?.let { ScheduleRepository.getInstance(app).getById(it) }
                            ?.let { WallpaperScheduler.scheduleAlarm(app, it) }
                        else -> Unit
                    }
                }.onFailure { Log.e(TAG, "Failed to re-arm scheduled wallpaper action", it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleReceiver"
        const val ACTION_TIMER_TICK = "com.makay.wallpaper.ACTION_TIMER_TICK"
        const val ACTION_SCHEDULE = "com.makay.wallpaper.ACTION_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
