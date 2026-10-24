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
            // Bazı launcher'lar dokunuşu onCommand ile iletir
            if (action != null && (
                    action.contains("tap", ignoreCase = true) ||
                        action.contains("click", ignoreCase = true)
                    )
            ) {
                val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
                if (gestures.enabled) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapAt > 550) tapCount = 0
                    lastTapAt = now
                    tapCount++
                    handler.removeCallbacks(tapReset)
                    handler.postDelayed(tapReset, 500)
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onDestroy() {
            handler.removeCallbacks(tapReset)
            handler.removeCallbacks(fadeTick)
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

        override fun onVisibilityChanged(visible: Boolean) {
            val prev = lastVisible
            lastVisible = visible
            if (visible) {
                reloadAndDraw(animate = false)
            }
            if (prev != null && prev != visible) {
                val triggers = WallpaperSettingsRepository.getInstance(applicationContext).triggers.value
                val shouldChange = if (visible) triggers.onHomeShown else triggers.onHomeHidden
                if (shouldChange) {
                    WallpaperController.next(applicationContext)
                    if (visible) reloadAndDraw(animate = true)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            // Bazı OEM'ler yalnız DOWN veya yalnız UP gönderir
            val action = event?.actionMasked ?: return
            if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) return
            if (action == MotionEvent.ACTION_DOWN) return // sayımı UP'ta yap; DOWN sadece izleme
            val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
            if (!gestures.enabled) return
            val now = System.currentTimeMillis()
            if (now - lastTapAt > 550) tapCount = 0
            lastTapAt = now
            tapCount++
            handler.removeCallbacks(tapReset)
            handler.postDelayed(tapReset, 500)
        }

        private fun handleTaps(count: Int) {
            val gestures = WallpaperSettingsRepository.getInstance(applicationContext).gestures.value
            if (!gestures.enabled) return
            val action: GestureAction = when (count) {
                2 -> gestures.doubleTapAction
                3 -> gestures.tripleTapAction
                else -> return
            }
            if (action == GestureAction.NONE) return
            val changed = WallpaperController.performGesture(applicationContext, action)
            if (changed || count >= 2) {
                reloadAndDraw(animate = true)
            }
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

            if (useFade && bitmap != null && next != null) {
                previousBitmap?.recycle()
                previousBitmap = bitmap
                bitmap = next
                fadeProgress = 0f
                handler.removeCallbacks(fadeTick)
                handler.post(fadeTick)
            } else {
                previousBitmap?.recycle()
                previousBitmap = null
                bitmap?.recycle()
                bitmap = next
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
                canvas.drawColor(Color.BLACK)
                val dst = Rect(0, 0, canvas.width, canvas.height)
                val prev = previousBitmap
                val cur = bitmap
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
