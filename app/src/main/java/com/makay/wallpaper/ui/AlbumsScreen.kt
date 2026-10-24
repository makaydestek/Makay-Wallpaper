package com.makay.wallpaper.ui

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.util.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onNavigateBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onNeedPermission: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AlbumRepository.getInstance(context) }
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val albums by repo.albums.collectAsState()
    val activeId by settings.activeAlbumId.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    /** Oluşturulan albüme hemen kaynak seçtir */
    var pickSourceForId by remember { mutableStateOf<String?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }

    val (launchImages, launchFolder) = rememberAlbumPickLaunchers(
        onNeedPermission = onNeedPermission,
        onBusy = { busyMessage = it }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("Albümler", "Albums", context),
                        fontWeight = FontWeight.Bold
                    )
                },
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
            QuickWallpaperSourceButtons(
                onNeedPermission = onNeedPermission,
                prominent = true,
                onCompleted = { /* ana akış zaten uyguladı */ }
            )

            Text(
                LanguageManager.t("Kayıtlı albümler", "Saved albums", context),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            TvActionButton(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                idle = MaterialTheme.colorScheme.tertiary
            ) {
                Text(LanguageManager.t("Boş albüm oluştur", "Create empty album", context))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    val isActive = album.id == activeId
                    Card(
                        onClick = { onOpenAlbum(album.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(album.name, fontWeight = FontWeight.SemiBold)
                                if (isActive) {
                                    Text(
                                        LanguageManager.t("Aktif", "Active", context),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(onClick = { launchImages(album.id) }) {
                                    Text(LanguageManager.t("Görsel", "Images", context))
                                }
                                TextButton(onClick = { launchFolder(album.id) }) {
                                    Text(LanguageManager.t("Klasör", "Folder", context))
                                }
                                TextButton(
                                    onClick = {
                                        if (album.images.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                LanguageManager.t(
                                                    "Önce görsel ekleyin",
                                                    "Add images first",
                                                    context
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (WallpaperController.setAlbum(context, album.id)) {
                                            Toast.makeText(
                                                context,
                                                LanguageManager.t(
                                                    "Uygulandı",
                                                    "Applied",
                                                    context
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ) {
                                    Text(
                                        LanguageManager.t("Kullan", "Use", context),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(busyMessage.orEmpty())
                }
            }
        }
    }

    pickSourceForId?.let { albumId ->
        AlertDialog(
            onDismissRequest = { pickSourceForId = null },
            title = {
                Text(LanguageManager.t("Kaynak seçin", "Choose source", context))
            },
            text = {
                Text(
                    LanguageManager.t(
                        "Albüm oluşturuldu. Görsel veya klasör ekleyin — hemen uygulanır.",
                        "Album created. Add images or a folder — applied instantly.",
                        context
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pickSourceForId = null
                    launchImages(albumId)
                }) {
                    Text(LanguageManager.t("Görsel seç", "Pick images", context))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pickSourceForId = null
                        launchFolder(albumId)
                    }) {
                        Text(LanguageManager.t("Klasör seç", "Pick folder", context))
                    }
                    TextButton(onClick = {
                        pickSourceForId = null
                        onOpenAlbum(albumId)
                    }) {
                        Text(LanguageManager.t("Sonra", "Later", context))
                    }
                }
            }
        )
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
                    val album = repo.create(
                        newName.ifBlank {
                            LanguageManager.t("Albüm", "Album", context)
                        }
                    )
                    newName = ""
                    showCreate = false
                    pickSourceForId = album.id
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
