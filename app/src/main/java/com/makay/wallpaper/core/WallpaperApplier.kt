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
     * Kırp + seçilen moda göre tam hedef çözünürlüğe ölçekle.
     * Çıktı her zaman tam ekran boyutunda olur; boşluk kalmaz (FIT hariç şerit eklenir).
     */
    fun prepareForDisplay(
        context: Context,
        uri: Uri,
        crop: RectF,
        fit: DisplayFitSettings = WallpaperSettingsRepository.getInstance(context).displayFit.value
    ): Bitmap? {
        val cropped = decodeCropped(context, uri, crop, fit) ?: return null
        val (tw, th) = resolveTargetSize(context, fit, cropped.width, cropped.height)
        val scaled = scaleToExact(cropped, tw, th, fit.fitMode, fit.letterboxColor)
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
            2048 to 2048
        }
        // Hedefin ~2 katına kadar oku; kalite için
        val sample = calculateInSampleSize(
            bounds.outWidth,
            bounds.outHeight,
            tw * 2,
            th * 2
        )
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
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
        var (w, h) = when (fit.resolutionSource) {
            ResolutionSource.CUSTOM -> fit.portraitSize()
            ResolutionSource.DEVICE -> deviceScreenSize(context)
        }
        if (fit.autoRotateDimsForLandscape && sourceW > 0 && sourceH > 0 && sourceW > sourceH && w < h) {
            val t = w
            w = h
            h = t
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

    /**
     * Kaynağı tam [outW]x[outH] bitmap'e dönüştürür.
     */
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
                // Center-crop: ekranı tamamen kapla, fazla kenarları at
                val scale = max(outW.toFloat() / source.width, outH.toFloat() / source.height)
                val dw = source.width * scale
                val dh = source.height * scale
                val left = (outW - dw) / 2f
                val top = (outH - dh) / 2f
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(left, top)
                }
                canvas.drawBitmap(source, matrix, paint)
            }
        }
        return out
    }

    /**
     * Görsel üzerinde hedef en-boy oranına göre ortalanmış kırpma dikdörtgeni (0–1).
     */
    fun centeredCropForAspect(imageW: Int, imageH: Int, targetW: Int, targetH: Int): RectF {
        if (imageW <= 0 || imageH <= 0 || targetW <= 0 || targetH <= 0) {
            return RectF(0f, 0f, 1f, 1f)
        }
        val imageAspect = imageW.toFloat() / imageH
        val targetAspect = targetW.toFloat() / targetH
        return if (imageAspect > targetAspect) {
            // Görsel daha geniş → yanları kırp
            val cropW = targetAspect / imageAspect
            val left = (1f - cropW) / 2f
            RectF(left, 0f, left + cropW, 1f)
        } else {
            // Görsel daha uzun → üst/alt kırp
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
        val (tw, th) = bitmap.width to bitmap.height
        runCatching { wm.suggestDesiredDimensions(tw, th) }

        val which = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // visibleCropHint = tüm bitmap; sistem ekstra kırpmaz
            val hint = Rect(0, 0, bitmap.width, bitmap.height)
            wm.setBitmap(bitmap, hint, true, which)
        } else {
            @Suppress("DEPRECATION")
            wm.setBitmap(bitmap)
        }
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.let { return it }
        // file:// veya düz dosya yolu (uygulama içi kopyalar)
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
