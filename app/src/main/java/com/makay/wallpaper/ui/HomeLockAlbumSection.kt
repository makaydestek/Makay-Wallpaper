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

/**
 * Ana ekran ve kilit ekranı için ayrı albüm seçimi (zorunlu net ayrım).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeLockAlbumSection() {
    val context = LocalContext.current
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val albumsRepo = remember { AlbumRepository.getInstance(context) }
    val albumList by albumsRepo.albums.collectAsState()
    val activeId by settings.activeAlbumId.collectAsState()
    val lockAlbumId by settings.lockAlbumId.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            LanguageManager.t("Ana / kilit albümü", "Home / lock albums", context),
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
                        "Ana ve kilit ekranı için ayrı albüm seçebilirsiniz. " +
                            "Kilit için «Aynı» dışında bir albüm seçerseniz iki ekran bağımsız kalır.",
                        "Pick separate albums for home and lock. " +
                            "Choosing a lock album other than Same keeps the screens independent.",
                        context
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    LanguageManager.t("Ana ekran albümü", "Home screen album", context),
                    fontWeight = FontWeight.SemiBold
                )
                if (albumList.isEmpty()) {
                    Text(
                        LanguageManager.t(
                            "Önce ana ekrandan görsel veya klasör seçin.",
                            "Pick images or a folder from Home first.",
                            context
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
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
                                    if (WallpaperController.setAlbum(context, a.id)) {
                                        Toast.makeText(
                                            context,
                                            LanguageManager.t(
                                                "Ana ekran: ${a.name}",
                                                "Home: ${a.name}",
                                                context
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                label = {
                                    Text(
                                        a.name + if (a.images.isEmpty()) " (0)" else " (${a.images.size})"
                                    )
                                }
                            )
                        }
                    }
                }

                Text(
                    LanguageManager.t("Kilit ekranı albümü", "Lock screen album", context),
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
                                    "Kilit = ana ile aynı",
                                    "Lock = same as home",
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
                                        "Kilit: ${a.name}",
                                        "Lock: ${a.name}",
                                        context
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            label = {
                                Text(
                                    a.name + if (a.images.isEmpty()) " (0)" else " (${a.images.size})"
                                )
                            }
                        )
                    }
                }

                TvActionButton(
                    onClick = {
                        val ok = WallpaperController.applyCurrent(context)
                        Toast.makeText(
                            context,
                            if (ok) {
                                LanguageManager.t(
                                    "Ana ve kilit uygulandı",
                                    "Home and lock applied",
                                    context
                                )
                            } else {
                                LanguageManager.t(
                                    "Uygulanamadı — albüm boş olabilir",
                                    "Apply failed — album may be empty",
                                    context
                                )
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
