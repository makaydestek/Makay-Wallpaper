package com.makay.wallpaper.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.util.LanguageManager

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
                onClick = { WallpaperController.setAlbum(context, album.id) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(LanguageManager.t("Aktif albüm yap", "Set as active", context))
            }

            TvFocusRow(
                onClick = {
                    repo.setWatchFolder(album.id, !album.watchFolder, album.folderPath)
                },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    LanguageManager.t("Klasörü izle", "Watch folder", context),
                    modifier = Modifier.weight(1f)
                )
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
