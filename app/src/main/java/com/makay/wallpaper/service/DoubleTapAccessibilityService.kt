package com.makay.wallpaper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.GestureAction

/**
 * MIUI yedeği: boş alana çift dokunuş.
 *
 * ÖNEMLİ: TYPE_VIEW_CLICKED kullanılmaz — klasör/ikon her tıklamada
 * TOUCH + CLICK birlikte gelince tek tık «çift dokunuş» sayılıp
 * duvar kağıdı değişiyordu.
 */
class DoubleTapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private var lastTapAt = 0L
    private var suppressUntil = 0L
    private val reset = Runnable {
        val c = tapCount
        tapCount = 0
        handleTaps(c)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            // VIEW_CLICKED yok — ikon/klasör tıklaması jest olmasın
            eventTypes = AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 40
        }
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val settings = WallpaperSettingsRepository.getInstance(applicationContext)
        if (!settings.gestures.value.enabled) return

        val pkg = event.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return
        if (!isLauncherPackage(pkg)) return

        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Klasör aç/kapa, uygulama geçişi
                suppressUntil = now + SUPPRESS_AFTER_WINDOW_MS
                tapCount = 0
                handler.removeCallbacks(reset)
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (now < suppressUntil) return
                // Canlı WP dokunuş alabiliyorsa jesti o yapsın (daha az yanlış pozitif)
                if (com.makay.wallpaper.util.LiveWallpaperHelper
                        .isOurLiveWallpaperActive(applicationContext)
                ) {
                    return
                }
                if (now - lastTapAt > TAP_SESSION_MS) tapCount = 0
                lastTapAt = now
                tapCount++
                handler.removeCallbacks(reset)
                handler.postDelayed(reset, TAP_COMMIT_MS)
            }
        }
    }

    private fun handleTaps(count: Int) {
        if (System.currentTimeMillis() < suppressUntil) return
        val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
        if (!gestures.enabled) return
        val action: GestureAction = when (count) {
            2 -> gestures.doubleTapAction
            3 -> gestures.tripleTapAction
            else -> return
        }
        if (action == GestureAction.NONE) return
        Thread({
            WallpaperController.performGesture(applicationContext, action)
        }, "GestureApply").start()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(reset)
        super.onDestroy()
    }

    companion object {
        private const val TAP_SESSION_MS = 450L
        private const val TAP_COMMIT_MS = 420L
        private const val SUPPRESS_AFTER_WINDOW_MS = 800L

        @Volatile
        var instance: DoubleTapAccessibilityService? = null
            private set

        fun isEnabled(context: android.content.Context): Boolean {
            val expected = "${context.packageName}/${DoubleTapAccessibilityService::class.java.canonicalName}"
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any {
                it.equals(expected, ignoreCase = true) ||
                    it.endsWith(DoubleTapAccessibilityService::class.java.name, ignoreCase = true)
            }
        }

        fun openAccessibilitySettings(context: android.content.Context) {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }

        private fun isLauncherPackage(pkg: String): Boolean {
            val p = pkg.lowercase()
            return p.contains("launcher") ||
                p.contains("miui.home") ||
                p.contains("mi.android.globallauncher") ||
                p == "com.miui.home" ||
                p == "com.android.launcher3" ||
                p.contains("trebuchet") ||
                p.contains("nexuslauncher") ||
                p.contains("microsoftlauncher") ||
                p.contains("honeyboard") ||
                p.contains("oneplus.launcher") ||
                p.contains("oppo.launcher") ||
                p.contains("coloros") ||
                p.contains("samsung.android.app.launcher") ||
                p.contains("sec.android.app.launcher") ||
                p.contains("huawei.android.launcher") ||
                p.contains("hihonor.android.launcher") ||
                p.contains("realme.launcher") ||
                p.contains("transsion") ||
                p.contains("nothing.launcher") ||
                p.contains("pixel.launcher") ||
                p == "com.google.android.apps.nexuslauncher"
        }
    }
}
