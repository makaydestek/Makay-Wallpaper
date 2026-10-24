package com.makay.wallpaper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.util.LanguageManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeLockAlbumSection() {
    val context = LocalContext.current
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val albumsRepo = remember { AlbumRepository.getInstance(context) }
    val albumList by albumsRepo.albums.collectAsState()
    val activeId by settings.activeAlbumId.collectAsState()
    val lockAlbumId by settings.lockAlbumId.collectAsState()
    val activeName = albumList.find { it.id == activeId }?.name
    val lockName = lockAlbumId?.let { id -> albumList.find { it.id == id }?.name }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            LanguageManager.t("Ana ve kilit ekranı", "Home and lock screen", context),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    LanguageManager.t(
                        "Ana ekran: zamanlayıcı ve çift dokunuş bu albümü kullanır.\n" +
                            "Kilit ekranı: «Ana ile aynı» = kilide de aynı fotoğraflar. " +
                            "Başka albüm seçerseniz kilit bağımsız kalır.",
                        "Home: timer and double-tap use this album.\n" +
                            "Lock: Same as home = lock uses the same photos. " +
                            "Pick another album for an independent lock.",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text(
                    LanguageManager.t(
                        "Ana ekran albümü" + (activeName?.let { " → $it" } ?: ""),
                        "Home album" + (activeName?.let { " → $it" } ?: ""),
                        context
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                if (albumList.isEmpty()) {
                    Text(
                        LanguageManager.t(
                            "Önce ana ekrandan klasör veya görsel seçin.",
                            "Pick a folder or images from Home first.",
                            context
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        albumList.forEach { a ->
                            FilterChip(
                                selected = activeId == a.id,
                                onClick = {
                                    WallpaperController.setAlbum(context, a.id)
                                    Toast.makeText(
                                        context,
                                        LanguageManager.t(
                                            "Ana ekran: ${a.name}",
                                            "Home: ${a.name}",
                                            context
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                label = { Text("${a.name} (${a.images.size})") }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    LanguageManager.t(
                        "Kilit ekranı" + if (lockAlbumId == null) {
                            " → ana ile aynı"
                        } else {
                            lockName?.let { " → $it" }.orEmpty()
                        },
                        "Lock screen" + if (lockAlbumId == null) {
                            " → same as home"
                        } else {
                            lockName?.let { " → $it" }.orEmpty()
                        },
                        context
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = lockAlbumId == null,
                        onClick = {
                            WallpaperController.setLockAlbum(context, null)
                            Toast.makeText(
                                context,
                                LanguageManager.t(
                                    "Kilit = ana ekran albümü",
                                    "Lock = home album",
                                    context
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        label = {
                            Text(LanguageManager.t("Ana ile aynı", "Same as home", context))
                        }
                    )
                    albumList.forEach { a ->
                        FilterChip(
                            selected = lockAlbumId == a.id,
                            onClick = {
                                WallpaperController.setLockAlbum(context, a.id)
                                Toast.makeText(
                                    context,
                                    LanguageManager.t(
                                        "Kilit ayrı: ${a.name}",
                                        "Lock separate: ${a.name}",
                                        context
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = { Text("${a.name} (${a.images.size})") }
                        )
                    }
                }

                TvActionButton(
                    onClick = {
                        val ok = WallpaperController.applyCurrent(context)
                        Toast.makeText(
                            context,
                            if (ok) {
                                LanguageManager.t("Uygulandı", "Applied", context)
                            } else {
                                LanguageManager.t("Uygulanamadı", "Failed", context)
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    idle = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        LanguageManager.t("Şimdi uygula", "Apply now", context),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
