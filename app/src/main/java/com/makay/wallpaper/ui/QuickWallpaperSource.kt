package com.makay.wallpaper.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.util.FolderImageScanner
import com.makay.wallpaper.util.ImageImportHelper
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.LiveWallpaperHelper
import com.makay.wallpaper.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wallpaper Changer Pro tarzı: tek dokunuşla görsel/klasör seç → albüm oluştur → aktif et → uygula.
 */
@Composable
fun QuickWallpaperSourceButtons(
    modifier: Modifier = Modifier,
    onNeedPermission: () -> Unit,
    /** true = ana ekran vurgulu CTA; false = albüm ekranı yardımcı butonlar */
    prominent: Boolean = true,
    onCompleted: ((albumId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val repo = remember { AlbumRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var busyFound by remember { mutableIntStateOf(0) }
    var busyTotal by remember { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    /** null = yeni albüm oluştur; dolu = mevcut albüme ekle */
    var targetAlbumId by remember { mutableStateOf<String?>(null) }
    var createNew by remember { mutableStateOf(true) }

    fun toast(tr: String, en: String) {
        Toast.makeText(context, LanguageManager.t(tr, en, context), Toast.LENGTH_SHORT).show()
    }

    fun stampName(): String {
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return LanguageManager.t("Albüm ${fmt.format(Date())}", "Album ${fmt.format(Date())}", context)
    }

    fun ensureAlbumId(preferredName: String): String {
        val existing = targetAlbumId
        if (!createNew && existing != null && repo.getById(existing) != null) return existing
        return repo.create(preferredName).id
    }

    fun promptLiveWallpaperIfNeeded() {
        // Her değişimde sistem seçicisini AÇMA — uygulama/ekran sürekli öne geliyordu.
        val gesturesOn = WallpaperSettingsRepository.getInstance(context).gestures.value.enabled
        if (!gesturesOn) return
        if (LiveWallpaperHelper.isOurLiveWallpaperActive(context)) return
        val prefs = context.getSharedPreferences("makay_wp_ui", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("live_wp_hint_shown", false)) return
        prefs.edit().putBoolean("live_wp_hint_shown", true).apply()
        toast(
            "Çift dokunuş için Ayarlar → Canlı duvar kağıdını ayarla",
            "For double-tap: Settings → Set live wallpaper"
        )
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            targetAlbumId = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busyMessage = LanguageManager.t("Görseller hazırlanıyor…", "Preparing images…", context)
            val local = withContext(Dispatchers.IO) {
                ImageImportHelper.importUris(context, uris)
            }
            if (local.isEmpty()) {
                busyMessage = null
                toast("Görsel alınamadı", "Could not import images")
                targetAlbumId = null
                return@launch
            }
            val albumId = ensureAlbumId(
                LanguageManager.t("Seçilen görseller", "Selected images", context)
            )
            repo.addImages(albumId, local)
            val ok = WallpaperController.setAlbum(context, albumId)
            busyMessage = null
            targetAlbumId = null
            if (ok) {
                toast(
                    "${local.size} görsel uygulandı",
                    "${local.size} images applied"
                )
                promptLiveWallpaperIfNeeded()
            } else {
                toast("Albüm hazır; duvar kağıdı uygulanamadı", "Album ready; wallpaper failed")
            }
            onCompleted?.invoke(albumId)
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            targetAlbumId = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busyFound = 0
            busyTotal = 0
            busyMessage = LanguageManager.t(
                "Klasör taranıyor…",
                "Scanning folder…",
                context
            )
            try {
                val folderName = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }
                        ?: stampName()
                }
                val scanned = withContext(Dispatchers.IO) {
                    FolderImageScanner.scan(context, uri.toString()) { found, total ->
                        mainHandler.post {
                            busyFound = found
                            busyTotal = total
                            busyMessage = LanguageManager.t(
                                "Klasör taranıyor… $found/$total",
                                "Scanning folder… $found/$total",
                                context
                            )
                        }
                    }
                }
                if (scanned.isEmpty()) {
                    toast("Klasörde görsel yok", "No images in folder")
                    targetAlbumId = null
                    return@launch
                }
                val albumId = ensureAlbumId(folderName)
                repo.setWatchFolder(albumId, true, uri.toString())
                repo.addImages(albumId, scanned)
                val ok = WallpaperController.setAlbum(context, albumId)
                targetAlbumId = null
                if (ok) {
                    toast(
                        "${scanned.size} görsel uygulandı ($folderName)",
                        "${scanned.size} images applied ($folderName)"
                    )
                    promptLiveWallpaperIfNeeded()
                } else {
                    toast("Albüm hazır; duvar kağıdı uygulanamadı", "Album ready; wallpaper failed")
                }
                onCompleted?.invoke(albumId)
            } finally {
                busyMessage = null
                busyFound = 0
                busyTotal = 0
            }
        }
    }

    fun startImages(albumId: String? = null) {
        if (!PermissionHelper.hasMediaPermission(context)) {
            onNeedPermission()
            return
        }
        createNew = albumId == null
        targetAlbumId = albumId
        pickImages.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun startFolder(albumId: String? = null) {
        createNew = albumId == null
        targetAlbumId = albumId
        pickFolder.launch(null)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (prominent) {
            Text(
                LanguageManager.t("Hızlı başlat", "Quick start", context),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                LanguageManager.t(
                    "Görsel veya klasör seçin — albüm oluşur ve hemen uygulanır.",
                    "Pick images or a folder — album is created and applied instantly.",
                    context
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvActionButton(
                onClick = { startImages(null) },
                modifier = Modifier.weight(1f).height(if (prominent) 56.dp else 48.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    LanguageManager.t("Görsel seç", "Pick images", context),
                    fontWeight = FontWeight.SemiBold
                )
            }
            TvActionButton(
                onClick = { startFolder(null) },
                modifier = Modifier.weight(1f).height(if (prominent) 56.dp else 48.dp),
                idle = MaterialTheme.colorScheme.secondary
            ) {
                Text(
                    LanguageManager.t("Klasör seç", "Pick folder", context),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Albüm kartlarından çağrı için state holder — dışarıya gerek yok;
    // AlbumsScreen kendi launcher'larını kullanmaya devam edebilir.
    // Bu composable yalnızca yeni albüm hızlı akışı içindir.

    if (busyMessage != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        busyMessage.orEmpty(),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (busyTotal > 0) {
                        Text(
                            "$busyFound / $busyTotal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = {
                                (busyFound.toFloat() / busyTotal.toFloat().coerceAtLeast(1f))
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mevcut albüme görsel/klasör ekleyip hemen aktif eder.
 * AlbumsScreen kart butonları için.
 */
@Composable
fun rememberAlbumPickLaunchers(
    onNeedPermission: () -> Unit,
    onBusy: (String?) -> Unit
): Pair<(String) -> Unit, (String) -> Unit> {
    val context = LocalContext.current
    val repo = remember { AlbumRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var pendingId by remember { mutableStateOf<String?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val id = pendingId
        pendingId = null
        if (id == null || uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            onBusy(LanguageManager.t("Görseller kaydediliyor…", "Saving images…", context))
            val local = withContext(Dispatchers.IO) {
                ImageImportHelper.importUris(context, uris)
            }
            onBusy(null)
            if (local.isEmpty()) {
                Toast.makeText(
                    context,
                    LanguageManager.t("Görsel alınamadı", "Could not import", context),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            repo.addImages(id, local)
            WallpaperController.setAlbum(context, id)
            Toast.makeText(
                context,
                LanguageManager.t(
                    "${local.size} görsel uygulandı",
                    "${local.size} images applied",
                    context
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val id = pendingId
        pendingId = null
        if (id == null || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mainHandler = Handler(Looper.getMainLooper())
            onBusy(LanguageManager.t("Klasör taranıyor…", "Scanning folder…", context))
            try {
                val scanned = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    FolderImageScanner.scan(context, uri.toString()) { found, total ->
                        mainHandler.post {
                            onBusy(
                                LanguageManager.t(
                                    "Klasör taranıyor… $found/$total",
                                    "Scanning folder… $found/$total",
                                    context
                                )
                            )
                        }
                    }
                }
                repo.setWatchFolder(id, true, uri.toString())
                if (scanned.isNotEmpty()) {
                    repo.addImages(id, scanned)
                    WallpaperController.setAlbum(context, id)
                    Toast.makeText(
                        context,
                        LanguageManager.t(
                            "${scanned.size} görsel uygulandı",
                            "${scanned.size} images applied",
                            context
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        LanguageManager.t("Klasörde görsel yok", "No images in folder", context),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                onBusy(null)
            }
        }
    }

    val launchImages: (String) -> Unit = { albumId ->
        if (!PermissionHelper.hasMediaPermission(context)) {
            onNeedPermission()
        } else {
            pendingId = albumId
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
    val launchFolder: (String) -> Unit = { albumId ->
        pendingId = albumId
        pickFolder.launch(null)
    }
    return launchImages to launchFolder
}
