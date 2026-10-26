package com.makay.wallpaper.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Diğer Makay uygulamalarına güvenli derin bağlantı (yalnızca yüklüyse).
 */
object MakayEcosystemHelper {

    data class MakayApp(
        val packageName: String,
        val labelTr: String,
        val labelEn: String
    )

    val knownApps: List<MakayApp> = listOf(
        MakayApp("com.makay.cleaner", "Makay Cleaner", "Makay Cleaner"),
        MakayApp("com.makay.filemanager", "Makay File Manager", "Makay File Manager"),
        MakayApp("com.makay.videoplayer", "Makay Video Player", "Makay Video Player"),
        MakayApp("com.makay.launcher", "Makay Launcher", "Makay Launcher"),
        MakayApp("com.makay.remote", "Makay Uzaktan Bağlantı", "Makay Remote")
    )

    fun isInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun installedApps(context: Context): List<MakayApp> =
        knownApps.filter { isInstalled(context, it.packageName) }

    fun openApp(context: Context, packageName: String): Boolean {
        return runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }
}
