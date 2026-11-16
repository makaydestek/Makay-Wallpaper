package com.makay.wallpaper.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.util.FolderImageScanner
import com.makay.wallpaper.util.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onNavigateBack: () -> Unit,
    onOpenCrop: (albumId: String, uri: String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AlbumRepository.getInstance(context) }
    val albums by repo.albums.collectAsState()
    val album = albums.find { it.id == albumId }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var scanFound by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var imageQuery by remember { mutableStateOf("") }
    var imageFilter by remember { mutableIntStateOf(0) }
    var imageSort by remember { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        album?.name ?: LanguageManager.t("Albüm", "Album", context),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TvIconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TvIconButton(onClick = {
                        renameValue = album?.name.orEmpty()
                        showRename = true
                    }) {
                        Text(LanguageManager.t("Ad", "Rename", context))
                    }
                    TvIconButton(onClick = {
                        repo.delete(albumId)
                        onNavigateBack()
                    }) {
                        Text(LanguageManager.t("Sil", "Del", context))
                    }
                }
            )
        }
    ) { padding ->
        if (album == null) {
            Text(
                LanguageManager.t("Albüm bulunamadı", "Album not found", context),
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            TvActionButton(
                onClick = {
                    if (album.images.isEmpty()) return@TvActionButton
                    WallpaperController.setAlbum(context, album.id)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    LanguageManager.t(
                        "Şimdi duvar kağıdı yap",
                        "Set as wallpaper now",
                        context
                    ),
                    fontWeight = FontWeight.Bold
                )
            }

            if (!album.folderPath.isNullOrBlank()) {
                TvActionButton(
                    onClick = {
                        if (scanning) return@TvActionButton
                        scanning = true
                        scanFound = 0
                        scanTotal = 0
                        scope.launch {
                            var sync = AlbumRepository.FolderSyncResult.EMPTY
                            try {
                                sync = withContext(Dispatchers.IO) {
                                    repo.setWatchFolder(album.id, true, album.folderPath)
                                    val uris = FolderImageScanner.scan(
                                        context,
                                        album.folderPath!!
                                    ) { found, total ->
                                        mainHandler.post {
                                            scanFound = found
                                            scanTotal = total
                                        }
                                    }
                                    repo.syncFolderImages(album.id, uris)
                                }
                            } finally {
                                scanning = false
                            }
                            val msg = when {
                                sync.added > 0 && sync.removed > 0 ->
                                    LanguageManager.t(
                                        "${sync.added} eklendi, ${sync.removed} silindi — albüm ${sync.total}",
                                        "${sync.added} added, ${sync.removed} removed — album ${sync.total}",
                                        context
                                    )
                                sync.added > 0 ->
                                    LanguageManager.t(
                                        "${sync.added} yeni görsel eklendi (tarama ${sync.scanned}, albüm ${sync.total})",
                                        "${sync.added} new images (scanned ${sync.scanned}, album ${sync.total})",
                                        context
                                    )
                                sync.removed > 0 ->
                                    LanguageManager.t(
                                        "${sync.removed} eksik görsel temizlendi — albüm ${sync.total}",
                                        "${sync.removed} missing images cleaned — album ${sync.total}",
                                        context
                                    )
                                else ->
                                    LanguageManager.t(
                                        "Güncel — tarama ${sync.scanned}, albümde ${sync.total}",
                                        "Up to date — scanned ${sync.scanned}, album has ${sync.total}",
                                        context
                                    )
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 8.dp),
                    idle = MaterialTheme.colorScheme.secondary,
                    enabled = !scanning
                ) {
                    Text(
                        if (scanning) {
                            LanguageManager.t("Taranıyor…", "Scanning…", context)
                        } else {
                            LanguageManager.t("Klasörü tara", "Scan folder", context)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (scanning) {
                    val total = scanTotal.coerceAtLeast(scanFound).coerceAtLeast(1)
                    val fraction = (scanFound.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                LanguageManager.t(
                                    "Klasör taranıyor",
                                    "Scanning folder",
                                    context
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "$scanFound / $scanTotal",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Text(
                            LanguageManager.t(
                                "Büyük klasörlerde birkaç dakika sürebilir; lütfen bekleyin.",
                                "Large folders may take a few minutes; please wait.",
                                context
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        LanguageManager.t(
                            "Albümde ${album.images.size} görsel. «Klasörü tara» eklenen/silinen dosyaları " +
                                "senkronize eder (otomatik ~15 dk).",
                            "${album.images.size} images in album. Scan folder syncs added/removed files " +
                                "(auto ~15 min).",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
            }

            TvFocusRow(
                onClick = {
                    repo.setWatchFolder(album.id, !album.watchFolder, album.folderPath)
                },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(LanguageManager.t("Klasörü izle", "Watch folder", context))
                    Text(
                        LanguageManager.t(
                            "Açıkken arka planda eklenen/silinen dosyalar senkronize edilir",
                            "When on, added/removed files sync in background",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = album.watchFolder,
                    onCheckedChange = { repo.setWatchFolder(album.id, it, album.folderPath) },
                    modifier = Modifier.tvSkipChildFocus()
                )
            }

            OutlinedTextField(
                value = imageQuery,
                onValueChange = { imageQuery = it },
                singleLine = true,
                label = { Text(LanguageManager.t("Görsellerde ara", "Search images", context)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = imageFilter == 0,
                    onClick = { imageFilter = 0 },
                    label = { Text(LanguageManager.t("Tümü", "All", context)) }
                )
                FilterChip(
                    selected = imageFilter == 1,
                    onClick = { imageFilter = 1 },
                    label = { Text(LanguageManager.t("Favoriler", "Favorites", context)) }
                )
                FilterChip(
                    selected = imageSort == 0,
                    onClick = { imageSort = 0 },
                    label = { Text(LanguageManager.t("Albüm sırası", "Album order", context)) }
                )
                FilterChip(
                    selected = imageSort == 1,
                    onClick = { imageSort = 1 },
                    label = { Text(LanguageManager.t("En az gösterilen", "Least shown", context)) }
                )
            }
            val visibleImages = remember(album.images, imageQuery, imageFilter, imageSort) {
                album.images
                    .filter { imageFilter == 0 || it.favorite }
                    .filter { item ->
                        imageQuery.isBlank() || item.uri.contains(imageQuery, ignoreCase = true) ||
                            imageDisplayName(context, item.uri).contains(imageQuery, ignoreCase = true)
                    }
                    .let { list ->
                        if (imageSort == 1) list.sortedWith(
                            compareBy<com.makay.wallpaper.domain.model.WallpaperImage> { it.shownCount }
                                .thenBy { it.lastShownAt }
                        ) else list
                    }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val removed = repo.removeDuplicateImages(context, album.id)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                LanguageManager.t("$removed yinelenen görsel kaldırıldı", "$removed duplicate images removed", context),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) { Text(LanguageManager.t("Yinelenenleri bul", "Find duplicates", context)) }
                Text(
                    LanguageManager.t("${visibleImages.size} görsel", "${visibleImages.size} images", context),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleImages, key = { it.uri }) { image ->
                    Card(
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable { onOpenCrop(album.id, image.uri) },
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                LanguageManager.t("Gösterim: ${image.shownCount}", "Shown: ${image.shownCount}", context),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { repo.toggleFavorite(album.id, image.uri) }, modifier = Modifier.weight(1f)) {
                                    Text(if (image.favorite) "★" else "☆")
                                }
                                IconButton(onClick = {
                                    repo.rotateImage(album.id, image.uri)
                                    if (WallpaperController.currentImage(context)?.uri == image.uri) {
                                        WallpaperController.applyCurrent(context)
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("↻") }
                            }
                            val originalIndex = album.images.indexOfFirst { it.uri == image.uri }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = originalIndex > 0 && imageSort == 0,
                                    onClick = {
                                        val reordered = album.images.toMutableList()
                                        java.util.Collections.swap(reordered, originalIndex, originalIndex - 1)
                                        repo.reorderImages(album.id, reordered.map { it.uri })
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("↑") }
                                IconButton(
                                    enabled = originalIndex in 0 until album.images.lastIndex && imageSort == 0,
                                    onClick = {
                                        val reordered = album.images.toMutableList()
                                        java.util.Collections.swap(reordered, originalIndex, originalIndex + 1)
                                        repo.reorderImages(album.id, reordered.map { it.uri })
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("↓") }
                            }
                            TextButton(
                                onClick = { repo.removeImage(album.id, image.uri) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(LanguageManager.t("Albümden kaldır", "Remove", context)) }
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = {
                Text(LanguageManager.t("Albümü yeniden adlandır", "Rename album", context))
            },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text(LanguageManager.t("Ad", "Name", context)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.rename(albumId, renameValue)
                    showRename = false
                }) {
                    Text(LanguageManager.t("Kaydet", "Save", context))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(LanguageManager.t("İptal", "Cancel", context))
                }
            }
        )
    }
}

private fun imageDisplayName(context: android.content.Context, imageUri: String): String {
    val uri = android.net.Uri.parse(imageUri)
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
    }.getOrDefault(uri.lastPathSegment.orEmpty())
}
