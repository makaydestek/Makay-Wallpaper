package com.makay.wallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.core.WallpaperScheduler
import com.makay.wallpaper.data.ScheduleRepository
import com.makay.wallpaper.domain.model.ScheduleType
import kotlin.random.Random

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            ACTION_TIMER_TICK -> {
                WallpaperController.next(app)
                WallpaperScheduler.rescheduleTimerAfterChange(app)
            }
            ACTION_SCHEDULE, null -> {
                val id = intent?.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
                val schedule = ScheduleRepository.getInstance(app).getById(id) ?: return
                if (!schedule.enabled) return
                when (schedule.type) {
                    ScheduleType.WALLPAPER -> {
                        if (schedule.random) WallpaperController.random(app)
                        else WallpaperController.next(app)
                    }
                    ScheduleType.ALBUM -> {
                        val albumId = schedule.targetAlbumId
                        if (!albumId.isNullOrBlank()) {
                            WallpaperController.setAlbum(app, albumId, if (schedule.random) Random.nextInt(0, 1000) % 1000 else 0)
                            // setAlbum zaten indeksi clamp eder; rastgele için tekrar random
                            if (schedule.random) WallpaperController.random(app)
                        }
                    }
                }
                WallpaperScheduler.scheduleAlarm(app, schedule)
            }
        }
    }

    companion object {
        const val ACTION_TIMER_TICK = "com.makay.wallpaper.ACTION_TIMER_TICK"
        const val ACTION_SCHEDULE = "com.makay.wallpaper.ACTION_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
