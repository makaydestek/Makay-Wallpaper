package com.makay.wallpaper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.core.WallpaperScheduler
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
                        scope.launch {
                            val before = album.images.size
                            val added = withContext(Dispatchers.IO) {
                                // Klasör izlemeyi aç
                                repo.setWatchFolder(album.id, true, album.folderPath)
                                val uris = FolderImageScanner.scan(context, album.folderPath!!)
                                val existing = repo.getById(album.id)?.images?.map { it.uri }?.toSet()
                                    .orEmpty()
                                val neu = uris.filter { it !in existing }
                                if (neu.isNotEmpty()) repo.addImages(album.id, neu)
                                neu.size
                            }
                            scanning = false
                            WallpaperScheduler.scanFoldersNow(context)
                            val total = repo.getById(album.id)?.images?.size ?: before
                            Toast.makeText(
                                context,
                                if (added > 0) {
                                    LanguageManager.t(
                                        "$added yeni görsel eklendi (toplam $total)",
                                        "$added new images added ($total total)",
                                        context
                                    )
                                } else {
                                    LanguageManager.t(
                                        "Yeni görsel yok (toplam $total)",
                                        "No new images ($total total)",
                                        context
                                    )
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 8.dp),
                    idle = MaterialTheme.colorScheme.secondary
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
                Text(
                    LanguageManager.t(
                        "Yeni eklenen fotoğraflar tarama ile listeye eklenir (otomatik ~15 dk).",
                        "New photos are added on scan (auto about every 15 min).",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
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
                            "Açıkken arka planda yeni dosyalar izlenir",
                            "When on, new files are watched in background",
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
}
