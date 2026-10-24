package com.makay.wallpaper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.makay.wallpaper.core.WallpaperApplier
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.GestureAction
import com.makay.wallpaper.domain.model.TransitionStyle

class MakayLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = MakayEngine()

    private inner class MakayEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var bitmap: Bitmap? = null
        private var previousBitmap: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var fadeProgress = 1f
        private var tapCount = 0
        private var lastTapAt = 0L
        private var lastVisible: Boolean? = null
        private val tapReset = Runnable {
            val count = tapCount
            tapCount = 0
            handleTaps(count)
        }
        private val fadeTick = object : Runnable {
            override fun run() {
                if (fadeProgress >= 1f) {
                    previousBitmap?.recycle()
                    previousBitmap = null
                    drawFrame()
                    return
                }
                fadeProgress = (fadeProgress + 0.08f).coerceAtMost(1f)
                drawFrame()
                handler.postDelayed(this, 16L)
            }
        }

        private val changeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                reloadAndDraw(animate = true)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter(WallpaperController.ACTION_WALLPAPER_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(changeReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(changeReceiver, filter)
            }
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(false)
            reloadAndDraw(animate = false)
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: android.os.Bundle?,
            resultRequested: Boolean
        ): android.os.Bundle? {
            // Bazı launcher'lar boş alan dokunuşunu onCommand ile iletir
            if (action != null && (
                    action.contains("tap", ignoreCase = true) ||
                        action.contains("click", ignoreCase = true)
                    )
            ) {
                val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
                if (gestures.enabled) {
                    registerTap()
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onDestroy() {
            handler.removeCallbacks(tapReset)
            handler.removeCallbacks(fadeTick)
            handler.removeCallbacks(confirmHiddenTrigger)
            runCatching { unregisterReceiver(changeReceiver) }
            bitmap?.recycle()
            previousBitmap?.recycle()
            bitmap = null
            previousBitmap = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        private var hiddenSince = 0L
        private val confirmHiddenTrigger = Runnable {
            if (lastVisible == false) {
                val triggers = WallpaperSettingsRepository.getInstance(applicationContext).triggers.value
                if (triggers.onHomeHidden && !WallpaperController.isGestureCooldownActive()) {
                    WallpaperController.next(applicationContext)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            val prev = lastVisible
            lastVisible = visible
            if (visible) {
                handler.removeCallbacks(confirmHiddenTrigger)
                reloadAndDraw(animate = false)
                // Klasör overlay kısa gizleme — tetikleme (en az 600ms gizli kalmış olmalı)
                if (prev == false &&
                    hiddenSince > 0L &&
                    System.currentTimeMillis() - hiddenSince >= 600L &&
                    !WallpaperController.isGestureCooldownActive()
                ) {
                    val triggers = WallpaperSettingsRepository.getInstance(applicationContext).triggers.value
                    if (triggers.onHomeShown) {
                        WallpaperController.next(applicationContext)
                        reloadAndDraw(animate = true)
                    }
                }
                hiddenSince = 0L
            } else if (prev != false) {
                // Kısa titreme (klasör açılışı) onHomeHidden çalıştırmasın
                hiddenSince = System.currentTimeMillis()
                handler.removeCallbacks(confirmHiddenTrigger)
                handler.postDelayed(confirmHiddenTrigger, 600L)
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            if (event == null) return
            val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
            if (!gestures.enabled) return
            // Boş duvar kağıdı alanı: launcher ikon/klasöre dokunuş buraya gelmez
            // Yalnız UP — tek fiziksel dokunuş = 1 sayım
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                registerTap()
            }
        }

        private fun registerTap() {
            val now = System.currentTimeMillis()
            if (now - lastTapAt < 90) return
            if (now - lastTapAt > 520) tapCount = 0
            lastTapAt = now
            tapCount++
            handler.removeCallbacks(tapReset)
            handler.postDelayed(tapReset, 480)
        }

        private fun handleTaps(count: Int) {
            val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
            if (!gestures.enabled) return
            // Net çift / üç; tek dokunuş (klasör/ikon) asla değiştirmez
            val use: GestureAction = when (count) {
                2 -> gestures.doubleTapAction
                3 -> gestures.tripleTapAction
                else -> return
            }
            if (use == GestureAction.NONE) return
            WallpaperController.performGesture(applicationContext, use)
            reloadAndDraw(animate = true)
        }

        private fun reloadAndDraw(animate: Boolean) {
            val image = WallpaperController.currentImage(applicationContext)
            val useFade = animate &&
                WallpaperSettingsRepository.getInstance(applicationContext)
                    .behavior.value.transition == TransitionStyle.FADE
            val next = if (image != null) {
                WallpaperApplier.prepareForDisplay(
                    applicationContext,
                    Uri.parse(image.uri),
                    image.cropRectNormalized()
                )
            } else null

            if (next == null) {
                // Decode başarısızsa mevcut kareyi silme (siyah flaş olmasın)
                drawFrame()
                return
            }

            if (useFade && bitmap != null) {
                previousBitmap?.recycle()
                previousBitmap = bitmap
                bitmap = next
                fadeProgress = 0f
                handler.removeCallbacks(fadeTick)
                handler.post(fadeTick)
            } else {
                previousBitmap?.recycle()
                previousBitmap = null
                val old = bitmap
                bitmap = next
                old?.recycle()
                fadeProgress = 1f
                drawFrame()
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return
                val dst = Rect(0, 0, canvas.width, canvas.height)
                val prev = previousBitmap
                val cur = bitmap
                val hasContent =
                    (prev != null && !prev.isRecycled && fadeProgress < 1f) ||
                        (cur != null && !cur.isRecycled)
                if (!hasContent) {
                    canvas.drawColor(Color.BLACK)
                } else {
                    // Önceki kareyi koru; siyah ara kare flaşını önle
                    canvas.drawColor(Color.BLACK)
                }
                if (prev != null && !prev.isRecycled && fadeProgress < 1f) {
                    paint.alpha = ((1f - fadeProgress) * 255).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(prev, null, dst, paint)
                }
                if (cur != null && !cur.isRecycled) {
                    paint.alpha = (fadeProgress * 255).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(cur, null, dst, paint)
                }
                paint.alpha = 255
            } finally {
                if (canvas != null) {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                }
            }
        }
    }
}
