package com.makay.wallpaper.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.makay.wallpaper.domain.model.ImageEffects
import kotlin.math.max
import kotlin.math.roundToInt

object ImageEffectsProcessor {

    fun apply(source: Bitmap, effects: ImageEffects): Bitmap {
        if (effects.isIdentity()) return source
        var current = source
        var owned = false

        if (effects.blurRadius > 0.5f) {
            val blurred = approximateBlur(current, effects.blurRadius.coerceIn(0f, 25f))
            if (owned && current !== source && !current.isRecycled) current.recycle()
            current = blurred
            owned = true
        }

        if (effects.grayscale || effects.brightness != 0f || effects.vignette > 0.01f) {
            val out = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (effects.grayscale || effects.brightness != 0f) {
                val cm = ColorMatrix()
                if (effects.grayscale) cm.setSaturation(0f)
                if (effects.brightness != 0f) {
                    val b = effects.brightness.coerceIn(-0.5f, 0.5f) * 255f
                    cm.postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                1f, 0f, 0f, 0f, b,
                                0f, 1f, 0f, 0f, b,
                                0f, 0f, 1f, 0f, b,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(current, 0f, 0f, paint)
            if (effects.vignette > 0.01f) {
                val v = effects.vignette.coerceIn(0f, 1f)
                val cx = out.width / 2f
                val cy = out.height / 2f
                val radius = max(cx, cy) * 1.15f
                val paintV = Paint(Paint.ANTI_ALIAS_FLAG)
                paintV.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb((v * 180).roundToInt(), 0, 0, 0)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paintV)
            }
            if (owned && current !== source && !current.isRecycled) current.recycle()
            current = out
            owned = true
        }
        return current
    }

    /** Düşük maliyetli yaklaşık blur: küçült → büyüt */
    private fun approximateBlur(src: Bitmap, radius: Float): Bitmap {
        val factor = (1f + radius / 5f).coerceIn(1.5f, 8f)
        val w = max(2, (src.width / factor).roundToInt())
        val h = max(2, (src.height / factor).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (small !== out && !small.isRecycled) small.recycle()
        return out
    }
}
