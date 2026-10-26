package com.makay.wallpaper.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.Album
import com.makay.wallpaper.domain.model.WallpaperImage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Yedekleme:
 * - .makaywp.zip → ayarlar + albüm metadata + görseller
 * - .json → eski yalnızca-metadata formatı (geriye uyumlu)
 */
object BackupRestore {

    private val gson = Gson()
    private const val MANIFEST = "manifest.json"
    private const val IMAGES_DIR = "images/"
    const val ZIP_MIME = "application/zip"
    const val ZIP_SUGGESTED_NAME = "MakayWallpaper-backup.makaywp.zip"

    fun exportToUri(context: Context, uri: Uri): Boolean {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        return if (name.endsWith(".json")) {
            exportJsonOnly(context, uri)
        } else {
            exportZip(context, uri)
        }
    }

    fun importFromUri(context: Context, uri: Uri): Boolean {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return if (
            name.endsWith(".zip") ||
            name.endsWith(".makaywp.zip") ||
            mime.contains("zip") ||
            mime.contains("octet-stream")
        ) {
            // Önce zip dene; başarısızsa JSON (eski)
            if (importZip(context, uri)) true else importJsonOnly(context, uri)
        } else {
            importJsonOnly(context, uri)
        }
    }

    private fun exportJsonOnly(context: Context, uri: Uri): Boolean {
        return runCatching {
            val albumsJson = AlbumRepository.getInstance(context).toJson()
            val json = WallpaperSettingsRepository.getInstance(context).exportSnapshotJson(albumsJson)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun importJsonOnly(context: Context, uri: Uri): Boolean {
        return runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return false
            applyManifestJson(context, text, imageRemap = emptyMap())
        }.getOrDefault(false)
    }

    private fun exportZip(context: Context, uri: Uri): Boolean {
        return runCatching {
            val albums = AlbumRepository.getInstance(context).getAll()
            val remappedAlbums = ArrayList<Album>()
            val filesToPack = ArrayList<Pair<String, File>>() // zipPath to local file

            albums.forEach { album ->
                val newImages = ArrayList<WallpaperImage>()
                album.images.forEachIndexed { index, image ->
                    val local = resolveLocalFile(context, image.uri)
                    if (local != null && local.isFile) {
                        val ext = local.extension.ifBlank { "jpg" }
                        val entryName = "${IMAGES_DIR}${album.id}/img_${index.toString().padStart(4, '0')}.$ext"
                        filesToPack.add(entryName to local)
                        newImages.add(image.copy(uri = entryName))
                    } else {
                        // Yerel kopya yoksa URI'yi olduğu gibi bırak (geri yüklemede atlanabilir)
                        newImages.add(image)
                    }
                }
                remappedAlbums.add(album.copy(images = newImages))
            }

            val albumsJson = gson.toJson(remappedAlbums)
            val manifest = WallpaperSettingsRepository.getInstance(context)
                .exportSnapshotJson(albumsJson)

            try {
                context.contentResolver.openOutputStream(uri)?.use { raw ->
                    ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                        zip.putNextEntry(ZipEntry(MANIFEST))
                        zip.write(manifest.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        filesToPack.forEach { (entryName, file) ->
                            zip.putNextEntry(ZipEntry(entryName))
                            FileInputStream(file).use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: return false
                true
            } finally {
                File(context.cacheDir, "backup_pack").deleteRecursively()
            }
        }.getOrDefault(false)
    }

    private fun importZip(context: Context, uri: Uri): Boolean {
        return runCatching {
            val tempRoot = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            try {
                var manifestText: String? = null
                val extracted = HashMap<String, File>()

                context.contentResolver.openInputStream(uri)?.use { raw ->
                    ZipInputStream(BufferedInputStream(raw)).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name.replace('\\', '/')
                            if (!entry.isDirectory && !name.contains("..")) {
                                if (name == MANIFEST) {
                                    manifestText = zip.readBytes().toString(Charsets.UTF_8)
                                } else if (name.startsWith(IMAGES_DIR)) {
                                    val out = File(tempRoot, name.substringAfter(IMAGES_DIR))
                                    out.parentFile?.mkdirs()
                                    out.outputStream().use { zip.copyTo(it) }
                                    extracted[name] = out
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: return false

                val text = manifestText ?: return false
                val wallpapersDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                val remap = HashMap<String, String>()
                extracted.forEach { (zipPath, tempFile) ->
                    val ext = tempFile.extension.ifBlank { "jpg" }
                    val dest = File(
                        wallpapersDir,
                        "restore_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
                    )
                    tempFile.copyTo(dest, overwrite = true)
                    remap[zipPath] = Uri.fromFile(dest).toString()
                }
                applyManifestJson(context, text, imageRemap = remap)
            } finally {
                tempRoot.deleteRecursively()
            }
        }.getOrDefault(false)
    }

    private fun applyManifestJson(
        context: Context,
        text: String,
        imageRemap: Map<String, String>
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val root = gson.fromJson(text, Map::class.java) as Map<String, Any?>
        val albumsNode = root["albums"] ?: return false
        val type = object : TypeToken<List<Album>>() {}.type
        var albums: List<Album> = gson.fromJson(gson.toJson(albumsNode), type)
        if (imageRemap.isNotEmpty()) {
            albums = albums.map { album ->
                album.copy(
                    images = album.images.mapNotNull { img ->
                        val mapped = imageRemap[img.uri]
                        when {
                            mapped != null -> img.copy(uri = mapped)
                            img.uri.startsWith(IMAGES_DIR) -> null // zip'te yoksa at
                            else -> img
                        }
                    }
                )
            }
        }
        AlbumRepository.getInstance(context).replaceAll(albums)
        WallpaperSettingsRepository.getInstance(context).importSnapshotJson(text)
        FolderWatchManager.refresh(context)
        return true
    }

    private fun resolveLocalFile(context: Context, uriString: String): File? {
        val uri = Uri.parse(uriString)
        return when {
            uri.scheme.equals("file", ignoreCase = true) ->
                uri.path?.let { File(it) }?.takeIf { it.isFile }
            uri.scheme.isNullOrBlank() ->
                File(uriString).takeIf { it.isFile }
            else -> {
                // content:// → geçici kopya dene
                runCatching {
                    val dir = File(context.cacheDir, "backup_pack").apply { mkdirs() }
                    val out = File(dir, "tmp_${UUID.randomUUID()}.bin")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    out.takeIf { it.isFile && it.length() > 0L }
                }.getOrNull()
            }
        }
    }
}
