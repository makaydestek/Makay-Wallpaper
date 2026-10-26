package com.makay.wallpaper.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.DisplayFitSettings
import com.makay.wallpaper.domain.model.FitMode
import com.makay.wallpaper.domain.model.ResolutionSource
import com.makay.wallpaper.domain.model.WallpaperImage
import com.makay.wallpaper.domain.model.WallpaperTarget
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object WallpaperApplier {

    fun setWallpaper(
        context: Context,
        image: WallpaperImage,
        target: WallpaperTarget = WallpaperTarget.BOTH
    ): Boolean {
        return runCatching {
            val fit = WallpaperSettingsRepository.getInstance(context).displayFit.value
            val bitmap = prepareForDisplay(
                context,
                Uri.parse(image.uri),
                image.cropRectNormalized(),
                fit
            ) ?: return false
            try {
                applyBitmap(context, bitmap, target)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            true
        }.getOrDefault(false)
    }

    fun setWallpaper(
        context: Context,
        uri: Uri,
        cropRect: RectF = RectF(0f, 0f, 1f, 1f),
        target: WallpaperTarget = WallpaperTarget.BOTH
    ): Boolean {
        return runCatching {
            val fit = WallpaperSettingsRepository.getInstance(context).displayFit.value
            val bitmap = prepareForDisplay(context, uri, cropRect, fit) ?: return false
            try {
                applyBitmap(context, bitmap, target)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Seçilen [FitMode] ile hedef çözünürlüğe oturtulmuş bitmap üretir.
     * FILL kenarları kırpar; FIT letterbox kullanır; STRETCH en-boy oranını bozabilir.
     */
    fun prepareForDisplay(
        context: Context,
        uri: Uri,
        crop: RectF,
        fit: DisplayFitSettings = WallpaperSettingsRepository.getInstance(context).displayFit.value
    ): Bitmap? {
        val effective = fit.copy(autoRotateDimsForLandscape = false)
        val cropped = decodeCropped(context, uri, crop, effective) ?: return null
        val (tw, th) = resolveTargetSize(context, effective, cropped.width, cropped.height)
        val scaled = scaleToExact(
            cropped,
            tw,
            th,
            effective.fitMode,
            effective.letterboxColor
        )
        if (scaled !== cropped && !cropped.isRecycled) cropped.recycle()
        val effects = WallpaperSettingsRepository.getInstance(context).effects.value
        val withFx = ImageEffectsProcessor.apply(scaled, effects)
        if (withFx !== scaled && !scaled.isRecycled) scaled.recycle()
        return withFx
    }

    fun decodeCropped(
        context: Context,
        uri: Uri,
        crop: RectF,
        fit: DisplayFitSettings? = null
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val (tw, th) = if (fit != null) {
            resolveTargetSize(context, fit, bounds.outWidth, bounds.outHeight)
        } else {
            deviceScreenSize(context)
        }
        // Bellek: tam hedef boyuta örnekle (2x OOM / yeniden başlatma yapıyordu)
        val sample = calculateInSampleSize(
            bounds.outWidth,
            bounds.outHeight,
            tw,
            th
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val full = openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val left = (crop.left * full.width).toInt().coerceIn(0, full.width - 1)
        val top = (crop.top * full.height).toInt().coerceIn(0, full.height - 1)
        val right = (crop.right * full.width).toInt().coerceIn(left + 1, full.width)
        val bottom = (crop.bottom * full.height).toInt().coerceIn(top + 1, full.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) {
            full.recycle()
            return null
        }
        val cropped = Bitmap.createBitmap(full, left, top, w, h)
        if (cropped !== full) full.recycle()
        return cropped
    }

    fun resolveTargetSize(
        context: Context,
        fit: DisplayFitSettings,
        sourceW: Int = 0,
        sourceH: Int = 0
    ): Pair<Int, Int> {
        // MIUI: desiredMinimum bazen 2x genişlik → yarım ekran siyah. Statik WP için gerçek ekran.
        val (sw, sh) = deviceScreenSize(context)
        var (w, h) = when (fit.resolutionSource) {
            ResolutionSource.CUSTOM -> fit.portraitSize()
            ResolutionSource.DEVICE -> sw to sh
        }
        // Ekrandan küçük olmasın
        if (w < sw) w = sw
        if (h < sh) h = sh
        // Parallax 2x genişliği kullanma — siyah alt/yan üretiyor
        if (w > sw * 1.15f && h <= sh * 1.15f) {
            w = sw
            h = sh
        }
        if (fit.autoRotateDimsForLandscape && sourceW > 0 && sourceH > 0 && sourceW > sourceH && w < h) {
            // Kapalı tutulmalı; yine de güvenlik
        }
        return w.coerceAtLeast(240) to h.coerceAtLeast(240)
    }

    fun deviceScreenSize(context: Context): Pair<Int, Int> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = context.getSystemService(WindowManager::class.java)
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels to metrics.heightPixels
            }
        }.getOrElse {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    fun scaleToExact(
        source: Bitmap,
        outW: Int,
        outH: Int,
        mode: FitMode,
        letterboxColor: Int = Color.BLACK
    ): Bitmap {
        if (source.width == outW && source.height == outH && mode != FitMode.FIT) {
            return source
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (mode) {
            FitMode.STRETCH -> {
                canvas.drawBitmap(source, null, Rect(0, 0, outW, outH), paint)
            }
            FitMode.FIT -> {
                canvas.drawColor(letterboxColor)
                val scale = min(outW.toFloat() / source.width, outH.toFloat() / source.height)
                val dw = (source.width * scale).toInt()
                val dh = (source.height * scale).toInt()
                val left = (outW - dw) / 2
                val top = (outH - dh) / 2
                canvas.drawBitmap(
                    source,
                    null,
                    Rect(left, top, left + dw, top + dh),
                    paint
                )
            }
            FitMode.FILL -> {
                val scale = max(outW.toFloat() / source.width, outH.toFloat() / source.height)
                val left = (outW - source.width * scale) / 2f
                val top = (outH - source.height * scale) / 2f
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(left, top)
                }
                canvas.drawBitmap(source, matrix, paint)
            }
        }
        return out
    }

    fun centeredCropForAspect(imageW: Int, imageH: Int, targetW: Int, targetH: Int): RectF {
        if (imageW <= 0 || imageH <= 0 || targetW <= 0 || targetH <= 0) {
            return RectF(0f, 0f, 1f, 1f)
        }
        val imageAspect = imageW.toFloat() / imageH
        val targetAspect = targetW.toFloat() / targetH
        return if (imageAspect > targetAspect) {
            val cropW = targetAspect / imageAspect
            val left = (1f - cropW) / 2f
            RectF(left, 0f, left + cropW, 1f)
        } else {
            val cropH = imageAspect / targetAspect
            val top = (1f - cropH) / 2f
            RectF(0f, top, 1f, top + cropH)
        }
    }

    private fun applyBitmap(
        context: Context,
        bitmap: Bitmap,
        target: WallpaperTarget
    ) {
        val wm = WallpaperManager.getInstance(context)
        val (sw, sh) = deviceScreenSize(context)
        // İstenen boyutu ekranla hizala (MIUI yarım ekran bug'ı)
        runCatching { wm.suggestDesiredDimensions(sw, sh) }

        val which = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // null cropHint: sistem tam bitmap'i kullansın (Rect bazen siyah şerit bırakır)
            wm.setBitmap(bitmap, null, true, which)
        } else {
            @Suppress("DEPRECATION")
            wm.setBitmap(bitmap)
        }
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.let { return it }
        val path = when {
            uri.scheme.equals("file", ignoreCase = true) -> uri.path
            uri.scheme.isNullOrBlank() -> uri.toString()
            else -> null
        }
        if (!path.isNullOrBlank()) {
            return runCatching { java.io.FileInputStream(java.io.File(path)) }.getOrNull()
        }
        return null
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun visibleRect(crop: RectF, bitmapW: Int, bitmapH: Int): Rect {
        return Rect(
            (crop.left * bitmapW).toInt().coerceIn(0, bitmapW - 1),
            (crop.top * bitmapH).toInt().coerceIn(0, bitmapH - 1),
            (crop.right * bitmapW).toInt().coerceIn(1, bitmapW),
            (crop.bottom * bitmapH).toInt().coerceIn(1, bitmapH)
        )
    }
}
