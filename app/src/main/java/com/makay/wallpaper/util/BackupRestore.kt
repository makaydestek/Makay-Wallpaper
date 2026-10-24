package com.makay.wallpaper.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.Album
import java.io.BufferedReader
import java.io.InputStreamReader

object BackupRestore {

    private val gson = Gson()

    fun exportToUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val albums = AlbumRepository.getInstance(context).getAll()
            val albumsJson = gson.toJson(albums)
            val json = WallpaperSettingsRepository.getInstance(context).exportSnapshotJson(albumsJson)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrDefault(false)
    }

    fun importFromUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return false
            @Suppress("UNCHECKED_CAST")
            val root = gson.fromJson(text, Map::class.java) as Map<String, Any?>
            val albumsNode = root["albums"] ?: return false
            val type = object : TypeToken<List<Album>>() {}.type
            val albums: List<Album> = gson.fromJson(gson.toJson(albumsNode), type)
            AlbumRepository.getInstance(context).replaceAll(albums)
            WallpaperSettingsRepository.getInstance(context).importSnapshotJson(text)
            FolderWatchManager.refresh(context)
            true
        }.getOrDefault(false)
    }
}
