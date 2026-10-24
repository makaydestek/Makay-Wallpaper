package com.makay.wallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.wallpaper.core.WallpaperController
import com.makay.wallpaper.data.AlbumRepository
import com.makay.wallpaper.data.WallpaperSettingsRepository
import com.makay.wallpaper.domain.model.IntervalUnit
import com.makay.wallpaper.util.LanguageManager
import com.makay.wallpaper.util.PermissionHelper
import com.makay.wallpaper.util.rememberMediaPermissionState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateAlbums: () -> Unit,
    onNavigateTimer: () -> Unit,
    onNavigateSchedules: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigatePermissions: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { WallpaperSettingsRepository.getInstance(context) }
    val albums = remember { AlbumRepository.getInstance(context) }
    val timer by settings.timer.collectAsState()
    val activeAlbumId by settings.activeAlbumId.collectAsState()
    val activeIndex by settings.activeIndex.collectAsState()
    val lockAlbumId by settings.lockAlbumId.collectAsState()
    val lastChangeAt by settings.lastChangeAt.collectAsState()
    val albumList by albums.albums.collectAsState()
    val hasPermission = rememberMediaPermissionState()
    val landscapePaused = WallpaperController.isLandscapePaused(context)

    val current = remember(activeAlbumId, activeIndex, albumList) {
        WallpaperController.currentImage(context)
    }
    val album = albumList.find { it.id == activeAlbumId }
    val lockAlbum = albumList.find { it.id == lockAlbumId }
    var etaMs by remember { mutableLongStateOf(WallpaperController.nextChangeEtaMillis(context) ?: -1L) }
    LaunchedEffect(timer, lastChangeAt) {
        while (true) {
            etaMs = WallpaperController.nextChangeEtaMillis(context) ?: -1L
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        LanguageManager.t("Makay Wallpaper", "Makay Wallpaper", context),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasPermission) {
                TvActionButton(
                    onClick = onNavigatePermissions,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    idle = MaterialTheme.colorScheme.error
                ) {
                    Text(LanguageManager.t("Medya izni gerekli", "Media permission required", context))
                }
            }

            if (landscapePaused) {
                Text(
                    LanguageManager.t(
                        "Yatay mod: otomatik değişim duraklatıldı",
                        "Landscape: auto-change paused",
                        context
                    ),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        album?.name ?: LanguageManager.t("Albüm seçilmedi", "No album selected", context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (current != null && album != null) {
                        Text(
                            LanguageManager.t(
                                "Görsel ${activeIndex + 1} / ${album.images.size}",
                                "Image ${activeIndex + 1} / ${album.images.size}",
                                context
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AsyncImage(
                            model = current.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            LanguageManager.t(
                                "Önizleme yok — albüm ekleyip aktifleştirin",
                                "No preview — add and activate an album",
                                context
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Durum paneli
                    Text(
                        LanguageManager.t("Durum", "Status", context),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildStatusLines(
                            context = context,
                            timerEnabled = timer.enabled,
                            intervalValue = timer.intervalValue,
                            unit = timer.unit,
                            etaMs = etaMs,
                            lastChangeAt = lastChangeAt,
                            lockAlbumName = lockAlbum?.name
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TvActionButton(
                            onClick = { WallpaperController.prev(context) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            idle = MaterialTheme.colorScheme.secondary
                        ) { Text(LanguageManager.t("Önceki", "Prev", context)) }
                        TvActionButton(
                            onClick = { WallpaperController.next(context) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            idle = MaterialTheme.colorScheme.primary
                        ) { Text(LanguageManager.t("Sonraki", "Next", context)) }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TvActionButton(
                            onClick = { WallpaperController.prevAlbum(context) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            idle = MaterialTheme.colorScheme.secondaryContainer,
                            idleContent = MaterialTheme.colorScheme.onSecondaryContainer
                        ) { Text(LanguageManager.t("Önceki albüm", "Prev album", context)) }
                        TvActionButton(
                            onClick = { WallpaperController.nextAlbum(context) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            idle = MaterialTheme.colorScheme.secondaryContainer,
                            idleContent = MaterialTheme.colorScheme.onSecondaryContainer
                        ) { Text(LanguageManager.t("Sonraki albüm", "Next album", context)) }
                    }
                }
            }

            NavButton(LanguageManager.t("Albümler", "Albums", context), onNavigateAlbums)
            NavButton(LanguageManager.t("Zamanlayıcı", "Timer", context), onNavigateTimer)
            NavButton(LanguageManager.t("Programlar", "Schedules", context), onNavigateSchedules)
            NavButton(LanguageManager.t("Ayarlar", "Settings", context), onNavigateSettings)

            if (!PermissionHelper.hasMediaPermission(context)) {
                // CTA above
            }
        }
    }
}

private fun buildStatusLines(
    context: android.content.Context,
    timerEnabled: Boolean,
    intervalValue: Int,
    unit: IntervalUnit,
    etaMs: Long,
    lastChangeAt: Long,
    lockAlbumName: String?
): String {
    val lines = mutableListOf<String>()
    if (timerEnabled) {
        val u = when (unit) {
            IntervalUnit.SECONDS -> LanguageManager.t("sn", "sec", context)
            IntervalUnit.MINUTES -> LanguageManager.t("dk", "min", context)
            IntervalUnit.HOURS -> LanguageManager.t("sa", "hr", context)
            IntervalUnit.DAYS -> LanguageManager.t("gün", "day", context)
        }
        lines += LanguageManager.t(
            "Zamanlayıcı: her $intervalValue $u",
            "Timer: every $intervalValue $u",
            context
        )
        if (etaMs >= 0) {
            val sec = (etaMs / 1000).toInt()
            val m = sec / 60
            val s = sec % 60
            lines += LanguageManager.t(
                "Sonraki değişim: %02d:%02d".format(m, s),
                "Next change: %02d:%02d".format(m, s),
                context
            )
        }
    } else {
        lines += LanguageManager.t("Zamanlayıcı kapalı", "Timer off", context)
    }
    if (lastChangeAt > 0) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lines += LanguageManager.t(
            "Son değişim: ${fmt.format(Date(lastChangeAt))}",
            "Last change: ${fmt.format(Date(lastChangeAt))}",
            context
        )
    }
    if (lockAlbumName != null) {
        lines += LanguageManager.t(
            "Kilit albümü: $lockAlbumName",
            "Lock album: $lockAlbumName",
            context
        )
    }
    return lines.joinToString("\n")
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    TvActionButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        idle = MaterialTheme.colorScheme.primaryContainer,
        idleContent = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    }
}
