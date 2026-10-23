package com.makay.wallpaper

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Makay Wallpaper - Fotoğraf import ve album yönetimi
 * Wallpaper Changer Pro'nun Free+Pro özelliklerini içeren uygulama özellikleri
 */
object WallpaperHelper {

    private const TAG = "WallpaperHelper"

    /** Android 13+ (SDK 33)'den önceki sürümler için dış depolama erişimi */
    @Suppress("UnnecessaryChecks")
    fun getExternalImageUris(context: Context): List<String> {
        val uris = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: MediaStore ile sınırlı erişim
            val contentResolver = context.contentResolver
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME
            )

            try {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor != null && cursor.count > 0) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID))
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME))
                            // DISPLAY_NAMEbased erişim - gerçek yolu için MediaStore API gerekir
                            Log.d(TAG, "Image ID: $id, Name: $name")
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Gallery access denied: ${e.message}")
            }
        } else {
            // Android 12 ve altı: Direct file system access
            try {
                val externalDir = Environment.getExternalStorageDirectory()
                scanDirectory(externalDir, uris)
            } catch (e: SecurityException) {
                Log.e(TAG, "Storage access denied: ${e.message}")
            }
        }
        return uris
    }

    /** Dizini ve alt dizinlerini recursive tarar */
    private fun scanDirectory(dir: File, uris: MutableList<String>) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    scanDirectory(file, uris)
                } else {
                    // Resim uzantılarını tanı
                    val loweredPath = file.path.lowercase(Locale.US)
                    if (lowedPath.endsWith(".jpg") || loweredPath.endsWith(".jpeg") ||
                        loweredPath.endsWith(".png") || loweredPath.endsWith(".webp") ||
                        loweredPath.endsWith(".gif")) {
                        uris.add(file.absolutePath)
                    }
                }
            }
        }
    }

    /** Belirtilen klasörden benzersiz albüm oluşturur */
    fun createAlbumFromFolder(folderPath: String, context: Context): String? {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            Log.w(TAG, "Klasör bulunamadı: $folderPath")
            return null
        }

        // Klasör adı üzerinden albüm ismi oluştur
        val albumName = folder.name.replace("_", " ").replace("-", " ").capitalize()
        Log.d(TAG, "Album oluşturuluyor: $albumName from $folderPath")

        // Burada veritabanı veya Room ile album kaydedilebilir
        // Geçici olarak klasör yolunu döndürüyoruz
        return albumName
    }

    /** Widget güncellemesi için duwallpaper değişimini tetikler */
    fun updateWallpaperNow(context: Context, targetPackage: String = "com.android.systemui") {
        val intent = android.content.Intent(android.action.WALLPAPER_CHANGED)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.sendBroadcast(intent)
        Log.d(TAG, "Wallpaper change broadcast sent")
    }

    /** Seçilen resmin URI'ni alır (Android 13+ uyumlu) */
    fun getContentUriForImage(context: Context, imagePath: String): Uri? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: MediaStore'a mapping yap
            val contentResolver = context.contentResolver
            return contentResolver.update(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply { put("_data", imagePath) },
                null, null
            )
        }
        return Uri.fromFile(File(imagePath))
    }
}

/**
 * Helper extension functions
 */
private fun String.capitalize(): String {
    if (this.isEmpty()) return this
    return this.substring(0, 1).toUpperCase() + this.substring(1).toLowerCase()
}