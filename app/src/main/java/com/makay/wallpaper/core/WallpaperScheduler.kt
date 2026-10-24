package com.makay.wallpaper.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.makay.wallpaper.data.ScheduleRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.Schedule
import com.makay.wallpaper.receiver.ScheduleReceiver
import com.makay.wallpaper.util.FolderWatchManager
import com.makay.wallpaper.worker.FolderScanWorker
import com.makay.wallpaper.worker.WallpaperChangeWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WallpaperScheduler {

    private const val TIMER_UNIQUE = "wallpaper_timer"
    private const val TIMER_CHAIN_UNIQUE = "wallpaper_timer_chain"
    private const val FOLDER_SCAN_UNIQUE = "folder_scan"
    private const val TIMER_ALARM_REQ = 41001
    private const val WORK_MIN_PERIODIC_MS = 15L * 60_000L

    fun scheduleTimer(context: Context) {
        val app = context.applicationContext
        val timer = WallpaperSettingsRepository.getInstance(app).timer.value
        cancelTimer(app)
        if (!timer.enabled) return

        val interval = timer.intervalMillis().coerceAtLeast(5_000L)
        val wm = WorkManager.getInstance(app)

        if (interval >= WORK_MIN_PERIODIC_MS) {
            val periodic = PeriodicWorkRequestBuilder<WallpaperChangeWorker>(
                interval,
                TimeUnit.MILLISECONDS
            ).build()
            wm.enqueueUniquePeriodicWork(
                TIMER_UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )
        } else {
            // WorkManager periyodik min 15 dk; kısa aralık için AlarmManager veya zincirli OneTime
            scheduleExactTimerAlarm(app, interval)
            val one = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
                .setInitialDelay(interval, TimeUnit.MILLISECONDS)
                .addTag(TIMER_CHAIN_UNIQUE)
                .build()
            wm.enqueueUniqueWork(TIMER_CHAIN_UNIQUE, ExistingWorkPolicy.REPLACE, one)
        }
    }

    fun cancelTimer(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        wm.cancelUniqueWork(TIMER_UNIQUE)
        wm.cancelUniqueWork(TIMER_CHAIN_UNIQUE)
        wm.cancelAllWorkByTag(TIMER_CHAIN_UNIQUE)
        // Periyodik iş adı ile de iptal (bazı cihazlarda UPDATE sonrası kalıntı)
        runCatching { wm.cancelAllWorkByTag(TIMER_UNIQUE) }
        cancelExactTimerAlarm(app)
    }

    /** Worker zinciri: kısa aralıkta bir değişim sonrası yeniden planla. */
    fun rescheduleTimerAfterChange(context: Context) {
        val timer = WallpaperSettingsRepository.getInstance(context).timer.value
        if (!timer.enabled) return
        val interval = timer.intervalMillis().coerceAtLeast(5_000L)
        if (interval >= WORK_MIN_PERIODIC_MS) return
        scheduleExactTimerAlarm(context.applicationContext, interval)
        val one = OneTimeWorkRequestBuilder<WallpaperChangeWorker>()
            .setInitialDelay(interval, TimeUnit.MILLISECONDS)
            .addTag(TIMER_CHAIN_UNIQUE)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TIMER_CHAIN_UNIQUE,
            ExistingWorkPolicy.REPLACE,
            one
        )
    }

    fun scheduleAllAlarms(context: Context) {
        val app = context.applicationContext
        val schedules = ScheduleRepository.getInstance(app).getAll()
        schedules.forEach { cancelScheduleAlarm(app, it.id) }
        schedules.filter { it.enabled }.forEach { scheduleAlarm(app, it) }
    }

    fun scheduleAlarm(context: Context, schedule: Schedule) {
        if (!schedule.enabled) {
            cancelScheduleAlarm(context, schedule.id)
            return
        }
        val triggerAt = nextTriggerMillis(schedule) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = schedulePendingIntent(context, schedule.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelScheduleAlarm(context: Context, scheduleId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(schedulePendingIntent(context, scheduleId))
    }

    fun rescheduleOnBoot(context: Context) {
        scheduleTimer(context)
        scheduleAllAlarms(context)
        enqueueFolderScan(context)
    }

    fun enqueueFolderScan(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        // İzlenen klasörler: yeni fotoğraflar için periyodik tarama (Wallpaper Changer tarzı)
        val request = PeriodicWorkRequestBuilder<FolderScanWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FOLDER_SCAN_UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        scanFoldersNow(context)
    }

    /** Arka plan tarama — tek yol (çift tarama yarışı / takılma olmasın) */
    fun scanFoldersNow(context: Context) {
        FolderWatchManager.scanNow(context.applicationContext)
    }

    private fun scheduleExactTimerAlarm(context: Context, intervalMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + intervalMs
        val pi = timerPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun cancelExactTimerAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(timerPendingIntent(context))
    }

    private fun timerPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_TIMER_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            TIMER_ALARM_REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedulePendingIntent(context: Context, scheduleId: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_SCHEDULE
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val req = scheduleId.hashCode() and 0x7FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextTriggerMillis(schedule: Schedule): Long? {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, schedule.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, schedule.minute.coerceIn(0, 59))
        }

        if (schedule.dayOfYear in 1..366) {
            cal.set(Calendar.DAY_OF_YEAR, schedule.dayOfYear)
            if (cal.before(now) || cal.timeInMillis <= now.timeInMillis) {
                cal.add(Calendar.YEAR, 1)
            }
            return cal.timeInMillis
        }

        val days = schedule.daysOfWeek
        if (days.isEmpty()) {
            if (!cal.after(now)) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        // En yakın eşleşen günü ara (1..7)
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (offset == 0 && !candidate.after(now)) continue
            if (candidate.get(Calendar.DAY_OF_WEEK) in days) {
                return candidate.timeInMillis
            }
        }
        return null
    }
}
