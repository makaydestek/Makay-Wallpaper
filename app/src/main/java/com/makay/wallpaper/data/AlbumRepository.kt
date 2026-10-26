package com.makay.wallpaper.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makay.wallpaper.domain.model.Album
import com.makay.wallpaper.domain.model.WallpaperImage
import com.makay.wallpaper.util.FolderWatchManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
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

    fun rename(id: String, name: String) {
        val album = getById(id) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == album.name) return
        update(album.copy(name = trimmed))
    }

    fun delete(id: String) {
        persist(_albums.value.filterNot { it.id == id })
    }

    /**
     * Yerel (file://) görselleri silinmişse albümden çıkarır.
     * content:// URI'ler korunur (izin / MediaStore hâlâ geçerli olabilir).
     * Klasör albümleri için asıl senkron [syncFolderImages] kullanın.
     */
    @Synchronized
    fun pruneMissingLocalFiles(albumId: String) {
        val album = getById(albumId) ?: return
        val kept = album.images.filter { image ->
            val uri = Uri.parse(image.uri)
            when {
                uri.scheme.equals("file", ignoreCase = true) -> {
                    val path = uri.path
                    !path.isNullOrBlank() && File(path).isFile
                }
                else -> true
            }
        }
        if (kept.size == album.images.size) return
        persist(listOfAlbumsReplacing(album.copy(images = kept)), refreshWatch = false)
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

    /**
     * Klasör taraması sonucunu albümle senkronize eder: yeni URI'ler eklenir,
     * klasörden silinmiş (özellikle SAF content://) kayıtlar çıkarılır.
     * Elle eklenen (klasör dışı) görseller korunur.
     * Boş tarama (hata / gerçekten boş) albümü silmez.
     */
    @Synchronized
    fun syncFolderImages(albumId: String, scannedUris: List<String>): FolderSyncResult {
        val album = getById(albumId) ?: return FolderSyncResult.EMPTY
        if (scannedUris.isEmpty()) {
            return FolderSyncResult(
                scanned = 0,
                added = 0,
                removed = 0,
                total = album.images.size
            )
        }
        val folderPath = album.folderPath
        val byUri = album.images.associateBy { it.uri }
        val scannedOrdered = LinkedHashSet(scannedUris)
        val folderImages = scannedOrdered.map { uri ->
            byUri[uri] ?: WallpaperImage(uri = uri)
        }
        // Klasör dışı elle eklenenler korunur; path yoksa tarama albümün tamamını temsil eder
        val manualKept = if (folderPath.isNullOrBlank()) {
            emptyList()
        } else {
            album.images.filter { img ->
                img.uri !in scannedOrdered && !isUriUnderFolder(img.uri, folderPath)
            }
        }
        val newImages = folderImages + manualKept
        val added = scannedOrdered.count { it !in byUri }
        val removed = album.images.count { img ->
            img.uri !in scannedOrdered &&
                (folderPath.isNullOrBlank() || isUriUnderFolder(img.uri, folderPath))
        }
        if (added == 0 && removed == 0 && newImages.size == album.images.size) {
            return FolderSyncResult(
                scanned = scannedOrdered.size,
                added = 0,
                removed = 0,
                total = album.images.size
            )
        }
        persist(
            listOfAlbumsReplacing(album.copy(images = newImages)),
            refreshWatch = false
        )
        runCatching { FolderWatchManager.refresh(appContext) }
        return FolderSyncResult(
            scanned = scannedOrdered.size,
            added = added,
            removed = removed,
            total = newImages.size
        )
    }

    /** URI bu albümün izlenen klasör ağacına ait mi? */
    private fun isUriUnderFolder(imageUri: String, folderPath: String): Boolean {
        return when {
            folderPath.startsWith("content:", ignoreCase = true) -> {
                val treeUri = Uri.parse(folderPath)
                val imgUri = Uri.parse(imageUri)
                if (!imgUri.scheme.equals("content", ignoreCase = true)) return false
                val treeId = runCatching {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }.getOrNull() ?: return false
                val imgTreeId = runCatching {
                    DocumentsContract.getTreeDocumentId(imgUri)
                }.getOrNull()
                if (imgTreeId != null) return imgTreeId == treeId
                // Yedek: document URI yolunda tree id geçiyor mu
                val encoded = Uri.encode(treeId)
                val s = imageUri
                s.contains("/tree/$treeId/") || s.contains("/tree/$encoded/")
            }
            folderPath.startsWith("file:", ignoreCase = true) -> {
                val root = Uri.parse(folderPath).path ?: return false
                pathUnderDir(Uri.parse(imageUri).path, root)
            }
            else -> pathUnderDir(Uri.parse(imageUri).path, folderPath)
        }
    }

    private fun pathUnderDir(filePath: String?, dirPath: String): Boolean {
        if (filePath.isNullOrBlank()) return false
        val file = runCatching { File(filePath).canonicalFile }.getOrNull() ?: return false
        val dir = runCatching { File(dirPath).canonicalFile }.getOrNull() ?: return false
        val prefix = dir.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        return file.path == dir.path || file.path.startsWith(prefix)
    }

    data class FolderSyncResult(
        val scanned: Int,
        val added: Int,
        val removed: Int,
        val total: Int
    ) {
        companion object {
            val EMPTY = FolderSyncResult(0, 0, 0, 0)
        }
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
