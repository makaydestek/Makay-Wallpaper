package com.makay.wallpaper.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Galeri / Photo Picker URI'leri geçicidir.
 * Duvar kağıdı ve Önceki/Sonraki için uygulama depolamasına kopyalanır.
 */
object ImageImportHelper {

    private const val TAG = "ImageImport"
    private const val DIR = "wallpapers"

    fun importUris(context: Context, uris: List<Uri>): List<String> {
        if (uris.isEmpty()) return emptyList()
        val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
        return uris.mapNotNull { uri -> copyOne(context, uri, dir) }
    }

    private fun copyOne(context: Context, uri: Uri, dir: File): String? {
        return runCatching {
            // Zaten uygulama içi dosya ise olduğu gibi kullan
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null && path.startsWith(dir.absolutePath) && File(path).isFile) {
                    return@runCatching uri.toString()
                }
            }
            val ext = guessExt(context, uri)
            val out = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (!out.isFile || out.length() <= 0L) {
                out.delete()
                return@runCatching null
            }
            Uri.fromFile(out).toString()
        }.onFailure {
            Log.w(TAG, "Import failed: $uri", it)
        }.getOrNull()
    }

    private fun guessExt(context: Context, uri: Uri): String {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return when {
            type.contains("png") -> "png"
            type.contains("webp") -> "webp"
            type.contains("gif") -> "gif"
            type.contains("heic") || type.contains("heif") -> "heic"
            else -> "jpg"
        }
    }
}
