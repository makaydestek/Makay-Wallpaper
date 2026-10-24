package com.makay.wallpaper.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makay.wallpaper.domain.model.Album
import com.makay.wallpaper.domain.model.WallpaperImage
import com.makay.wallpaper.util.FolderWatchManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AlbumRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _albums = MutableStateFlow(loadAlbums())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    fun getAll(): List<Album> = _albums.value

    fun getById(id: String): Album? = _albums.value.find { it.id == id }

    fun create(name: String, folderPath: String? = null, watchFolder: Boolean = false): Album {
        val album = Album(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Albüm" },
            folderPath = folderPath,
            watchFolder = watchFolder
        )
        persist(_albums.value + album)
        return album
    }

    fun update(album: Album) {
        persist(listOfAlbumsReplacing(album))
    }

    fun delete(id: String) {
        persist(_albums.value.filterNot { it.id == id })
    }

    @Synchronized
    fun addImages(albumId: String, uris: List<String>) {
        val album = getById(albumId) ?: return
        val existing = album.images.map { it.uri }.toHashSet()
        val added = ArrayList<WallpaperImage>()
        for (uri in uris) {
            if (existing.add(uri)) {
                added.add(WallpaperImage(uri = uri))
            }
        }
        if (added.isEmpty()) return
        // Tarama sırasında FileObserver yenileme fırtınası olmasın
        persist(listOfAlbumsReplacing(album.copy(images = album.images + added)), refreshWatch = false)
        runCatching { FolderWatchManager.refresh(appContext) }
    }

    private fun listOfAlbumsReplacing(album: Album): List<Album> =
        _albums.value.map { if (it.id == album.id) album else it }

    fun removeImage(albumId: String, uri: String) {
        val album = getById(albumId) ?: return
        update(album.copy(images = album.images.filterNot { it.uri == uri }))
    }

    fun updateCrop(albumId: String, uri: String, left: Float, top: Float, right: Float, bottom: Float) {
        val album = getById(albumId) ?: return
        update(
            album.copy(
                images = album.images.map {
                    if (it.uri == uri) {
                        it.copy(
                            cropLeft = left,
                            cropTop = top,
                            cropRight = right,
                            cropBottom = bottom
                        )
                    } else it
                }
            )
        )
    }

    fun setWatchFolder(albumId: String, watch: Boolean, folderPath: String? = null) {
        val album = getById(albumId) ?: return
        update(
            album.copy(
                watchFolder = watch,
                folderPath = folderPath ?: album.folderPath
            )
        )
    }

    fun replaceAll(list: List<Album>) {
        persist(list)
    }

    fun toJson(): String = gson.toJson(_albums.value)

    private fun loadAlbums(): List<Album> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Album>>() {}.type
            gson.fromJson<List<Album>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Album>, refreshWatch: Boolean = true) {
        // Büyük albümlerde apply() kaybı riskine karşı commit
        prefs.edit().putString(KEY, gson.toJson(list)).commit()
        _albums.value = list
        if (refreshWatch) {
            runCatching { FolderWatchManager.refresh(appContext) }
        }
    }

    companion object {
        private const val PREFS = "album_prefs"
        private const val KEY = "albums"

        @Volatile
        private var instance: AlbumRepository? = null

        fun getInstance(context: Context): AlbumRepository {
            return instance ?: synchronized(this) {
                instance ?: AlbumRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
