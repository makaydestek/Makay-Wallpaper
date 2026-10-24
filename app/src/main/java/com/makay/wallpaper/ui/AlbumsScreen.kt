package com.makay.wallpaper.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.util.FolderImageScanner
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onNavigateBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onNeedPermission: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AlbumRepository.getInstance(context) }
    val albums by repo.albums.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingAlbumId by remember { mutableStateOf<String?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val id = pendingAlbumId ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) {
            repo.addImages(id, uris.map { it.toString() })
        }
        pendingAlbumId = null
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val id = pendingAlbumId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val scanned = FolderImageScanner.scan(context, uri.toString())
            repo.setWatchFolder(id, true, uri.toString())
            if (scanned.isNotEmpty()) repo.addImages(id, scanned)
        }
        pendingAlbumId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(LanguageManager.t("Albümler", "Albums", context), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TvIconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            TvActionButton(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(LanguageManager.t("Yeni albüm", "New album", context))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    Card(
                        onClick = { onOpenAlbum(album.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(album.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                LanguageManager.t(
                                    "${album.images.size} görsel",
                                    "${album.images.size} images",
                                    context
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = {
                                    if (!PermissionHelper.hasMediaPermission(context)) {
                                        onNeedPermission()
                                        return@TextButton
                                    }
                                    pendingAlbumId = album.id
                                    pickImages.launch("image/*")
                                }) {
                                    Text(LanguageManager.t("Görsel ekle", "Add images", context))
                                }
                                TextButton(onClick = {
                                    pendingAlbumId = album.id
                                    pickFolder.launch(null)
                                }) {
                                    Text(LanguageManager.t("Klasör", "Folder", context))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(LanguageManager.t("Yeni albüm", "New album", context)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(LanguageManager.t("Ad", "Name", context)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val album = repo.create(newName.ifBlank {
                        LanguageManager.t("Albüm", "Album", context)
                    })
                    newName = ""
                    showCreate = false
                    onOpenAlbum(album.id)
                }) { Text(LanguageManager.t("Oluştur", "Create", context)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text(LanguageManager.t("İptal", "Cancel", context))
                }
            }
        )
    }
}
