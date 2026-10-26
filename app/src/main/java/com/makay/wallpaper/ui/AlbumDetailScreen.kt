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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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

            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(album.images, key = { it.uri }) { image ->
                    Card(
                        onClick = { onOpenCrop(album.id, image.uri) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        AsyncImage(
                            model = image.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
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
